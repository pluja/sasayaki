package com.sasayaki.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.repository.ProfileRepository
import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.provider.describeConnectionError
import com.sasayaki.domain.transcription.DictationFailure
import com.sasayaki.service.bubble.BubbleActions
import com.sasayaki.service.bubble.BubbleAppearance
import com.sasayaki.service.bubble.BubbleOverlay
import com.sasayaki.service.bubble.BubbleScales
import com.sasayaki.service.bubble.BubbleUiState
import com.sasayaki.service.bubble.WAVEFORM_BARS
import com.sasayaki.service.bubble.toPhase
import com.sasayaki.service.dictation.ProcessedRecording
import com.sasayaki.service.dictation.RecordingProcessor
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class BubbleService : Service() {
    companion object {
        private const val TAG = "BubbleService"
        private const val INSERTED_FLASH_MS = 700L
        private const val NO_SPEECH_NOTICE_MS = 2500L
        const val ACTION_START = "com.sasayaki.ACTION_START_BUBBLE"
        private const val ACTION_CARRY = "com.sasayaki.ACTION_CARRY_DICTATION"

        @Volatile
        var isRunning: Boolean = false
            private set(value) {
                field = value
                _runningState.value = value
            }

        private val _runningState = MutableStateFlow(false)
        val runningState: StateFlow<Boolean> = _runningState.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, BubbleService::class.java).apply {
                    action = ACTION_START
                }
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }

        /**
         * carry starts the bubble for a dictation the keyboard panel is about to hand over with
         * DictationHandover.offer. The panel calls it while still on screen, because Android only
         * lets a visible app start a microphone service. It reports whether Android allowed the start.
         */
        fun carry(context: Context): Boolean = try {
            context.startForegroundService(
                Intent(context, BubbleService::class.java).apply {
                    action = ACTION_CARRY
                }
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not start the bubble to carry a dictation", e)
            false
        }
    }

    @Inject lateinit var recordingProcessor: RecordingProcessor

    @Inject lateinit var textInjectionBridge: TextInjectionBridge

    @Inject lateinit var preferencesDataStore: PreferencesDataStore

    @Inject lateinit var profileRepository: ProfileRepository

    // scope reports a failure that escapes a dictation instead of crashing the app.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Unhandled bubble failure", error)
            mainHandler.post { showError(describe(error), null) }
        }
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlay: BubbleOverlay? = null
    private val uiState = MutableStateFlow(BubbleUiState())
    private val levels = MutableStateFlow<List<Float>>(emptyList())
    private var notificationHelper: NotificationHelper? = null
    private var hapticFeedback: HapticFeedback? = null
    private var audioRecorder: AudioRecorder? = null
    private var silenceDetector: SilenceDetector? = null

    @Volatile private var recordingJob: Job? = null
    private var transcriptionJob: Job? = null
    private var silenceCheckJob: Job? = null
    private var levelJob: Job? = null
    private var timerJob: Job? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var profileJob: Job? = null
    private var startJob: Job? = null

    private var state: ServiceState = ServiceState.Idle
    private var recordingStartTime: Long = 0
    private var pausedStartedAt: Long = 0
    private var totalPausedMs: Long = 0
    private var recordingAppContext: Deferred<AppContext?>? = null
    private var recordingProfile: Deferred<Profile>? = null
    private var recordingIncognito = false
    private var carriedAppContext: AppContext? = null

    // carryOnly is set when the bubble was off and started only to carry a keyboard dictation, so
    // it stops again once that dictation ends.
    private var carryOnly = false
    private var profileSwitch: Job? = null

    private val dictationInFlight: Boolean
        get() = state is ServiceState.Recording || state is ServiceState.Transcribing ||
            state is ServiceState.PostProcessing || state is ServiceState.Injecting

    @Volatile private var pcmFile: File? = null

    private val keyboardListener = object : TextInjectorService.KeyboardListener {
        override fun onKeyboardVisibilityChanged(visible: Boolean) {
            scope.launch(Dispatchers.Main) {
                // An active dictation keeps the bubble up even if the keyboard closes.
                if (visible) {
                    overlay?.show()
                    // Switching mid-dictation would process it with another app's profile.
                    if (!dictationInFlight) {
                        profileSwitch = scope.launch {
                            profileRepository.switchToRememberedProfile(textInjectionBridge.focusedEditorPackage())
                        }
                    }
                } else if (state is ServiceState.Idle) {
                    overlay?.hide()
                }
            }
        }
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            turnOff()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        BubbleTileService.requestRefresh(this)
        val helper = NotificationHelper(this)
        notificationHelper = helper
        hapticFeedback = HapticFeedback(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        helper.createNotificationChannel()
        profileJob = scope.launch {
            profileRepository.activeProfile.collect { profile ->
                uiState.update { it.copy(profileName = profile?.name.orEmpty()) }
            }
        }

        ContextCompat.registerReceiver(
            this,
            stopReceiver,
            IntentFilter(NotificationHelper.ACTION_STOP),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper?.buildForegroundNotification() ?: run {
            Log.e(TAG, "NotificationHelper not initialized")
            stopSelf()
            return START_NOT_STICKY
        }
        // Idle as specialUse so the service can also be started from the boot receiver;
        // promoteToMicrophoneService() upgrades the type when recording actually starts.
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission required.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        val carrying = intent?.action == ACTION_CARRY
        carryOnly = if (carrying) carryOnly || overlay == null else false
        prepareOverlay()
        TextInjectorService.keyboardListener = keyboardListener
        if (TextInjectorService.isKeyboardVisible) overlay?.show()
        if (carrying) carryHandedDictation()

        // A bubble started only to carry one dictation must not come back on its own.
        return if (carryOnly) START_NOT_STICKY else START_STICKY
    }

    /**
     * carryHandedDictation picks up a dictation the keyboard panel handed over while it was still
     * recording. The recording never stopped, so the bubble takes over its timer, its silence check
     * and its delivery.
     */
    private fun carryHandedDictation() {
        val dictation = DictationHandover.take() ?: return stopIfOnlyCarrying()
        if (dictationInFlight || !promoteToMicrophoneService()) {
            Log.w(TAG, "Bubble could not carry the keyboard dictation, state=$state")
            dictation.recorder.stop()
            dictation.pcm.delete()
            return stopIfOnlyCarrying()
        }
        DictationHandover.carrying = true
        DictationHandover.release = ::releaseToPanel
        carriedAppContext = dictation.appContext
        audioRecorder = dictation.recorder
        pcmFile = dictation.pcm
        recordingJob = dictation.job
        val now = System.currentTimeMillis()
        recordingStartTime = now - dictation.recordedMs
        totalPausedMs = 0
        pausedStartedAt = if (dictation.paused) now else 0
        recordingAppContext = CompletableDeferred(dictation.appContext)
        recordingProfile = dictation.profile
        recordingIncognito = dictation.incognito
        uiState.update { it.copy(elapsedSeconds = dictation.recordedMs / 1000) }
        updateState(ServiceState.Recording(paused = dictation.paused))
        overlay?.show()
        watchRecording(dictation.recorder)
    }

    // releaseToPanel gives a carried dictation back to the keyboard panel when it opens again. The
    // recording keeps going, and the bubble goes.
    private fun releaseToPanel(): CarriedDictation? {
        val current = state as? ServiceState.Recording ?: return null
        val handed = CarriedDictation(
            recorder = audioRecorder ?: return null,
            pcm = pcmFile ?: return null,
            job = recordingJob,
            recordedMs = recordedDurationMs(),
            paused = current.paused,
            appContext = carriedAppContext,
            profile = recordingProfile,
            incognito = recordingIncognito
        )
        silenceCheckJob?.cancel()
        levelJob?.cancel()
        timerJob?.cancel()
        overlay?.dismissProfileMenu()
        audioRecorder = null
        pcmFile = null
        recordingJob = null
        silenceDetector = null
        demoteToIdleService()
        updateState(ServiceState.Idle)
        return handed
    }

    private fun stopIfOnlyCarrying() {
        if (carryOnly) stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        BubbleTileService.requestRefresh(this)
        discardRecordingOnDestroy()
        overlay?.destroy()
        overlay = null
        if (TextInjectorService.keyboardListener === keyboardListener) TextInjectorService.keyboardListener = null
        try {
            unregisterReceiver(stopReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Stop receiver was not registered", e)
        }
        scope.cancel()
        abandonRecordingAudioFocus()
        super.onDestroy()
    }

    private fun prepareOverlay() {
        if (overlay != null) return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = BubbleOverlay(
            context = this,
            windowManager = windowManager,
            scope = scope,
            uiState = uiState,
            levels = levels,
            profiles = profileRepository.profiles.stateIn(scope, SharingStarted.Eagerly, emptyList()),
            appearance = preferencesDataStore.preferences
                .map { BubbleAppearance(it.wallpaperColors, it.bubbleTranslucent, it.bubbleMonochrome) }
                .stateIn(scope, SharingStarted.Eagerly, BubbleAppearance()),
            scales = preferencesDataStore.preferences
                .map { BubbleScales(bubble = it.bubbleSize.scale, controls = it.controlsSize.scale) }
                .stateIn(scope, SharingStarted.Eagerly, BubbleScales()),
            actions = BubbleActions(
                onStart = ::onBubbleTap,
                onStop = ::stopRecordingAndTranscribe,
                onCancel = ::cancelRecording,
                onTogglePause = ::toggleRecordingPause,
                onCancelProcessing = ::cancelProcessing,
                onRetry = ::retryBubbleError,
                onDismissError = ::cancelBubbleError
            ),
            onSelectProfile = { id ->
                hapticFeedback?.tick()
                val selection = scope.async {
                    val packageName = textInjectionBridge.focusedEditorPackage()
                    profileRepository.activate(id)
                    profileRepository.rememberProfileForApp(packageName, id)
                    profileRepository.getActiveProfile()
                }
                profileSwitch = selection
                // The pill shows the pick at once, so it applies to the dictation in progress.
                if (state is ServiceState.Recording) recordingProfile = selection
            },
            onRemove = ::turnOff
        ).also { it.create() }
    }

    // turnOff stops the bubble at the person's request and remembers it, so a boot or an update
    // leaves it off. A bubble that was only carrying a dictation was never turned on.
    private fun turnOff() {
        if (!carryOnly) {
            CoroutineScope(Dispatchers.IO).launch { preferencesDataStore.setShowBubble(false) }
        }
        stopSelf()
    }

    private fun onBubbleTap() {
        when (val current = state) {
            is ServiceState.Idle, ServiceState.NoSpeech -> requestRecording()

            is ServiceState.Recording -> stopRecordingAndTranscribe()

            is ServiceState.Transcribing, is ServiceState.PostProcessing -> cancelProcessing()

            // Injection is already underway and cannot be meaningfully undone.
            is ServiceState.Injecting -> {}

            is ServiceState.Error -> current.retryEntryId?.let(::retrySavedFailure) ?: updateState(ServiceState.Idle)
        }
    }

    /**
     * Adds the microphone type to the running foreground service for the duration of a
     * recording. The service idles as specialUse so it can be started at boot, but the
     * platform requires the microphone type to be active while the mic is in use.
     *
     * @return false if the platform refused the promotion, in which case recording must
     *   not start — the user is told rather than left with a bubble that does nothing.
     */
    private fun promoteToMicrophoneService(): Boolean {
        val notification = notificationHelper?.buildForegroundNotification() ?: return false
        return try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not switch to microphone foreground service", e)
            showError("Microphone unavailable. Reopen Sasayaki and try again.", null)
            false
        }
    }

    /**
     * Drops back to specialUse once the microphone is released, so the system's
     * microphone-in-use indicator does not stay lit while the bubble sits idle.
     */
    private fun demoteToIdleService() {
        val notification = notificationHelper?.buildForegroundNotification() ?: return
        try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not drop microphone foreground service type", e)
        }
    }

    /**
     * requestRecording checks the focused field first: the lookup crosses into the focused
     * app, so it runs off the main thread, and taps that land meanwhile are ignored.
     */
    private fun requestRecording() {
        if (startJob?.isActive == true) return
        startJob = scope.launch {
            val sensitive = textInjectionBridge.isFocusedFieldSensitive()
            if (state !is ServiceState.Idle && state !is ServiceState.NoSpeech) return@launch
            if (sensitive) {
                showError("Dictation is off in password and banking fields.", null)
            } else {
                startRecording()
            }
        }
    }

    private fun startRecording() {
        if (!promoteToMicrophoneService()) return

        scope.launch {
            val prefs = preferencesDataStore.preferences.first()
            if (prefs.pauseOtherAudio) requestRecordingAudioFocus()
            if (prefs.vibrateOnRecord) hapticFeedback?.recordStart()
        }

        uiState.update { it.copy(elapsedSeconds = 0) }
        updateState(ServiceState.Recording())
        recordingStartTime = System.currentTimeMillis()
        pausedStartedAt = 0
        totalPausedMs = 0
        // The lookup crosses into the focused app, so it stays off the main thread.
        recordingAppContext = scope.async(Dispatchers.Default) { textInjectionBridge.currentAppContext }
        val pendingSwitch = profileSwitch
        recordingProfile = scope.async {
            pendingSwitch?.join()
            profileRepository.getActiveProfile()
        }
        recordingIncognito = false

        audioRecorder = AudioRecorder()
        pcmFile = File(cacheDir, "recording_${System.currentTimeMillis()}.pcm")

        val recorder = audioRecorder ?: return
        val outputFile = pcmFile ?: return

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                recorder.record(outputFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recording error", e)
            }
        }

        watchRecording(recorder)
    }

    // watchRecording draws the waveform and the timer, and stops on silence, for a recording the
    // bubble started or carried.
    private fun watchRecording(recorder: AudioRecorder) {
        levels.value = emptyList()
        levelJob = scope.launch {
            recorder.audioLevel.collect { level ->
                levels.update { (it + level).takeLast(WAVEFORM_BARS) }
            }
        }

        timerJob = scope.launch {
            while (state is ServiceState.Recording) {
                uiState.update { it.copy(elapsedSeconds = recordedDurationMs() / 1000) }
                delay(250)
            }
        }

        silenceCheckJob = scope.launch {
            val prefs = preferencesDataStore.preferences.first()
            silenceDetector = SilenceDetector(
                audioLevel = recorder.audioLevel,
                silenceThresholdMs = prefs.silenceThresholdMs
            )
            delay(1000) // grace period
            while (state is ServiceState.Recording) {
                if ((state as? ServiceState.Recording)?.paused == true) {
                    delay(100)
                    continue
                }
                if (silenceDetector?.checkSilence() == true) {
                    stopRecordingAndTranscribe()
                    break
                }
                delay(100)
            }
        }
    }

    private fun toggleRecordingPause() {
        val current = state as? ServiceState.Recording ?: return
        if (current.paused) {
            totalPausedMs += System.currentTimeMillis() - pausedStartedAt
            pausedStartedAt = 0
            silenceDetector?.reset()
            audioRecorder?.resume()
            updateState(ServiceState.Recording(paused = false))
        } else {
            pausedStartedAt = System.currentTimeMillis()
            silenceDetector?.reset()
            audioRecorder?.pause()
            updateState(ServiceState.Recording(paused = true))
        }
    }

    // discardRecordingOnDestroy drops a recording in progress; the service's scope is about
    // to be cancelled, so nothing could transcribe it.
    private fun discardRecordingOnDestroy() {
        if (state !is ServiceState.Recording) return
        audioRecorder?.stop()
        recordingJob?.cancel()
        pcmFile?.delete()
    }

    private fun stopRecordingAndTranscribe() {
        if (state !is ServiceState.Recording) return

        // Signal the recorder to stop — do NOT cancel the job
        audioRecorder?.stop()
        demoteToIdleService()
        silenceCheckJob?.cancel()
        levelJob?.cancel()
        timerJob?.cancel()
        overlay?.dismissProfileMenu()

        scope.launch {
            val prefs = preferencesDataStore.preferences.first()
            if (prefs.vibrateOnRecord) hapticFeedback?.recordStop()
        }

        val durationMs = recordedDurationMs()
        updateState(ServiceState.Transcribing)

        // A new recording may start before this job ends, so it holds on to its own
        // recording and only clears the shared fields that still point at it.
        val recording = Recording(recordingJob, pcmFile, audioRecorder, silenceDetector)
        transcriptionJob = scope.launch {
            try {
                recording.job?.join()
                val pcm = recording.pcm ?: return@launch updateState(ServiceState.Idle)
                val outcome = recordingProcessor.process(
                    pcm = pcm,
                    durationMs = durationMs,
                    appContext = recordingAppContext?.await(),
                    profile = recordingProfile?.await(),
                    onPostProcessing = ::showPostProcessing,
                    incognito = recordingIncognito
                )
                when (outcome) {
                    ProcessedRecording.Empty -> updateState(ServiceState.Idle)
                    ProcessedRecording.NoSpeech -> showNoSpeech()
                    is ProcessedRecording.Transcribed -> deliver(outcome.result)
                }
            } catch (e: CancellationException) {
                // Cancelled by the user; cancelProcessing() owns the state transition.
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                showError(describe(e), null)
            } finally {
                recording.job?.cancel()
                abandonRecordingAudioFocus()
                recording.pcm?.delete()
                release(recording)
            }
        }
    }

    /**
     * Aborts an in-flight transcription or post-processing request. The ASR and LLM calls
     * are suspending Retrofit calls, so cancelling the job cancels the HTTP request; the
     * coroutine's finally block still runs to release audio focus and temp files.
     */
    private fun cancelProcessing() {
        if (state !is ServiceState.Transcribing && state !is ServiceState.PostProcessing) return
        Log.d(TAG, "Cancelling processing at state=$state")
        transcriptionJob?.cancel()
        transcriptionJob = null
        scope.launch {
            val prefs = preferencesDataStore.preferences.first()
            if (prefs.vibrateOnRecord) hapticFeedback?.recordStop()
        }
        updateState(ServiceState.Idle)
    }

    private fun cancelRecording() {
        if (state !is ServiceState.Recording) return

        audioRecorder?.stop()
        demoteToIdleService()
        recordingJob?.cancel()
        silenceCheckJob?.cancel()
        levelJob?.cancel()
        timerJob?.cancel()
        overlay?.dismissProfileMenu()

        scope.launch {
            val prefs = preferencesDataStore.preferences.first()
            if (prefs.vibrateOnRecord) hapticFeedback?.recordStop()
            abandonRecordingAudioFocus()
        }

        updateState(ServiceState.Idle)

        val recording = Recording(recordingJob, pcmFile, audioRecorder, silenceDetector)
        scope.launch {
            try {
                recording.job?.join()
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { recording.pcm?.delete() }
                release(recording)
            }
        }
    }

    private class Recording(val job: Job?, val pcm: File?, val recorder: AudioRecorder?, val detector: SilenceDetector?)

    private fun release(recording: Recording) {
        if (recordingJob === recording.job) recordingJob = null
        if (pcmFile === recording.pcm) pcmFile = null
        if (audioRecorder === recording.recorder) audioRecorder = null
        if (silenceDetector === recording.detector) silenceDetector = null
    }

    private fun recordedDurationMs(): Long {
        val now = System.currentTimeMillis()
        val currentPauseMs = if ((state as? ServiceState.Recording)?.paused == true && pausedStartedAt > 0) {
            now - pausedStartedAt
        } else {
            0L
        }
        return (now - recordingStartTime - totalPausedMs - currentPauseMs).coerceAtLeast(0L)
    }

    private fun retrySavedFailure(entryId: Long) {
        updateState(ServiceState.Transcribing)
        transcriptionJob = scope.launch {
            try {
                deliver(recordingProcessor.retry(entryId, ::showPostProcessing))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Retry failed", e)
                showError(describe(e), entryId)
            }
        }
    }

    private suspend fun deliver(result: Result<String>) {
        result.onSuccess { text ->
            if (text.isNotBlank()) {
                updateState(ServiceState.Injecting)
                if (textInjectionBridge.inject(text)) flashInserted()
                hapticFeedback?.complete()
            }
            updateState(ServiceState.Idle)
        }.onFailure { error ->
            showError(describe(error), (error as? DictationFailure)?.retryEntryId)
        }
    }

    // showPostProcessing is called from the processing thread, and only moves a dictation
    // that is still transcribing; a cancel may already have returned the bubble to idle.
    private fun showPostProcessing() {
        scope.launch {
            if (state == ServiceState.Transcribing) updateState(ServiceState.PostProcessing)
        }
    }

    private fun describe(error: Throwable): String = describeConnectionError((error as? DictationFailure)?.cause ?: error)

    private fun retryBubbleError() {
        val retryEntryId = (state as? ServiceState.Error)?.retryEntryId
        if (retryEntryId != null) {
            retrySavedFailure(retryEntryId)
        } else {
            updateState(ServiceState.Idle)
        }
    }

    private fun cancelBubbleError() {
        updateState(ServiceState.Idle)
    }

    private fun showNoSpeech() {
        updateState(ServiceState.NoSpeech)
        scope.launch {
            delay(NO_SPEECH_NOTICE_MS)
            if (state is ServiceState.NoSpeech) updateState(ServiceState.Idle)
        }
    }

    private fun showError(message: String, retryEntryId: Long? = null) {
        updateState(ServiceState.Error(message, retryEntryId))
        hapticFeedback?.error()
        scope.launch {
            delay(if (retryEntryId == null) 4000 else 8000)
            if (state is ServiceState.Error) {
                updateState(ServiceState.Idle)
            }
        }
    }

    private fun flashInserted() {
        uiState.update { it.copy(justInserted = true) }
        scope.launch {
            delay(INSERTED_FLASH_MS)
            uiState.update { it.copy(justInserted = false) }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onDisplayChanged()
    }

    private fun updateState(newState: ServiceState) {
        state = newState
        uiState.update { it.copy(phase = newState.toPhase()) }
        if (newState !is ServiceState.Idle) return
        if (DictationHandover.carrying) DictationHandover.carryEnded()
        DictationHandover.carrying = false
        DictationHandover.release = null
        if (carryOnly) return stopSelf()
        if (!TextInjectorService.isKeyboardVisible) overlay?.hide()
    }

    private fun requestRecordingAudioFocus() {
        val manager = audioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        audioFocusRequest = request
        manager.requestAudioFocus(request)
    }

    private fun abandonRecordingAudioFocus() {
        val manager = audioManager ?: return
        audioFocusRequest?.let(manager::abandonAudioFocusRequest)
        audioFocusRequest = null
    }
}
