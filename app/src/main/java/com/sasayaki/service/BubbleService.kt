package com.sasayaki.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.domain.transcription.AudioConverter
import com.sasayaki.domain.transcription.TranscriptionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class BubbleService : Service() {
    companion object {
        private const val TAG = "BubbleService"
        const val ACTION_START = "com.sasayaki.ACTION_START_BUBBLE"

        @Volatile
        var isRunning: Boolean = false
            private set(value) {
                field = value
                _runningState.value = value
            }

        private val _runningState = MutableStateFlow(false)
        val runningState: StateFlow<Boolean> = _runningState.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BubbleService::class.java).apply {
                action = ACTION_START
            })
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }
    }

    @Inject lateinit var transcriptionManager: TranscriptionManager
    @Inject lateinit var textInjectionBridge: TextInjectionBridge
    @Inject lateinit var preferencesDataStore: PreferencesDataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var bubbleView: BubbleView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var notificationHelper: NotificationHelper? = null
    private var hapticFeedback: HapticFeedback? = null
    private var audioRecorder: AudioRecorder? = null
    private var silenceDetector: SilenceDetector? = null
    private var recordingJob: Job? = null
    private var silenceCheckJob: Job? = null
    private var levelJob: Job? = null
    private var longPressJob: Job? = null
    private var fanMenuController: FanMenuController? = null

    private var state: ServiceState = ServiceState.Idle
    private var recordingStartTime: Long = 0
    private var recordingSourceApp: String? = null
    private var pcmFile: File? = null
    private var bubbleAdded = false

    // Keyboard-aware visibility
    private val keyboardListener = object : TextInjectorService.KeyboardListener {
        override fun onKeyboardVisibilityChanged(visible: Boolean) {
            scope.launch(Dispatchers.Main) {
                if (visible) {
                    showBubble()
                } else if (state is ServiceState.Idle) {
                    hideBubble()
                }
                // Don't hide while recording/transcribing
            }
        }
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val helper = NotificationHelper(this)
        notificationHelper = helper
        hapticFeedback = HapticFeedback(this)
        helper.createNotificationChannel()

        registerReceiver(stopReceiver, IntentFilter(NotificationHelper.ACTION_STOP), RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper?.buildForegroundNotification() ?: run {
            Log.e(TAG, "NotificationHelper not initialized")
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission required.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prepareBubble()

        // Register for keyboard events
        TextInjectorService.keyboardListener = keyboardListener

        // If keyboard is already visible, show immediately
        if (TextInjectorService.isKeyboardVisible) {
            showBubble()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        fanMenuController?.dismiss()
        stopRecordingAndWait()
        removeBubble()
        TextInjectorService.keyboardListener = null
        try { unregisterReceiver(stopReceiver) } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

    private fun prepareBubble() {
        if (bubbleView != null) return

        bubbleView = BubbleView(this)
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        val wm = windowManager ?: return
        fanMenuController = FanMenuController(
            context = this,
            windowManager = wm,
            preferencesDataStore = preferencesDataStore,
            scope = scope,
            hapticFeedback = hapticFeedback
        )

        setupTouchListener()
    }

    private fun showBubble() {
        if (bubbleAdded || bubbleView == null) return
        try {
            windowManager?.addView(bubbleView, layoutParams)
            bubbleAdded = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show bubble", e)
        }
    }

    private fun hideBubble() {
        if (!bubbleAdded || bubbleView == null) return
        try {
            windowManager?.removeView(bubbleView)
            bubbleAdded = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide bubble", e)
        }
    }

    private fun removeBubble() {
        bubbleView?.cleanup()
        if (bubbleAdded) {
            try { windowManager?.removeView(bubbleView) } catch (_: Exception) {}
            bubbleAdded = false
        }
        bubbleView = null
    }

    private fun setupTouchListener() {
        val params = layoutParams ?: return
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var longPressTriggered = false
        val tapThreshold = 10 * resources.displayMetrics.density
        val longPressDelayMs = 400L

        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    longPressTriggered = false
                    longPressJob?.cancel()
                    if (state is ServiceState.Idle) {
                        longPressJob = scope.launch {
                            delay(longPressDelayMs)
                            if (!isDragging) {
                                longPressTriggered = true
                                val bubbleSizePx = bubbleView?.measuredWidth ?: 0
                                fanMenuController?.show(params.x, params.y, bubbleSizePx)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > tapThreshold * tapThreshold) {
                        isDragging = true
                        longPressJob?.cancel()
                    }
                    if (isDragging && bubbleAdded) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        try { windowManager?.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressJob?.cancel()
                    if (longPressTriggered) {
                        // Long press already handled, do nothing
                    } else if (!isDragging) {
                        if (fanMenuController?.isShowing == true) {
                            fanMenuController?.dismiss()
                        } else {
                            onBubbleTap()
                        }
                    } else {
                        val displayHeight = resources.displayMetrics.heightPixels
                        if (params.y > displayHeight - 200) {
                            stopSelf()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onBubbleTap() {
        when (state) {
            is ServiceState.Idle -> startRecording()
            is ServiceState.Recording -> stopRecordingAndTranscribe()
            is ServiceState.Transcribing -> {}
            is ServiceState.Injecting -> {}
            is ServiceState.Error -> updateState(ServiceState.Idle)
        }
    }

    private fun startRecording() {
        scope.launch {
            val prefs = preferencesDataStore.preferences.first()
            if (prefs.vibrateOnRecord) hapticFeedback?.recordStart()
        }

        updateState(ServiceState.Recording)
        recordingStartTime = System.currentTimeMillis()
        recordingSourceApp = textInjectionBridge.focusedAppName

        audioRecorder = AudioRecorder()
        pcmFile = File(cacheDir, "recording_${System.currentTimeMillis()}.pcm")

        val recorder = audioRecorder ?: return
        val outputFile = pcmFile ?: return

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                recorder.record(outputFile)
            } catch (e: Exception) {
                Log.e(TAG, "Recording error", e)
            }
        }

        levelJob = scope.launch {
            recorder.audioLevel.collect { level ->
                bubbleView?.updateAudioLevel(level)
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
                if (silenceDetector?.checkSilence() == true) {
                    stopRecordingAndTranscribe()
                    break
                }
                delay(100)
            }
        }
    }

    /** Stop recording without transcribing (for cleanup on destroy) */
    private fun stopRecordingAndWait() {
        if (state !is ServiceState.Recording) return
        audioRecorder?.stop()
        levelJob?.cancel()
        silenceCheckJob?.cancel()
        // Don't cancel recordingJob — let it finish flushing the file
    }

    /** Stop recording and start transcription */
    private fun stopRecordingAndTranscribe() {
        if (state !is ServiceState.Recording) return

        // Signal the recorder to stop — do NOT cancel the job
        audioRecorder?.stop()
        silenceCheckJob?.cancel()
        levelJob?.cancel()

        scope.launch {
            val prefs = preferencesDataStore.preferences.first()
            if (prefs.vibrateOnRecord) hapticFeedback?.recordStop()
        }

        val durationMs = System.currentTimeMillis() - recordingStartTime
        updateState(ServiceState.Transcribing)

        scope.launch {
            var currentPcmFile: File? = null
            var wavFile: File? = null
            try {
                recordingJob?.join()

                currentPcmFile = pcmFile
                if (currentPcmFile == null || !currentPcmFile.exists() || currentPcmFile.length() == 0L) {
                    Log.w(TAG, "PCM file empty or missing")
                    updateState(ServiceState.Idle)
                    return@launch
                }

                wavFile = File(cacheDir, "recording_${System.currentTimeMillis()}.wav")
                withContext(Dispatchers.IO) {
                    AudioConverter.pcmToWav(currentPcmFile, wavFile)
                }

                val sourceApp = recordingSourceApp

                val result = withContext(Dispatchers.IO) {
                    transcriptionManager.transcribe(wavFile, durationMs, sourceApp)
                }

                result.onSuccess { text ->
                    if (text.isNotBlank()) {
                        updateState(ServiceState.Injecting)
                        textInjectionBridge.inject(text)
                        hapticFeedback?.complete()
                    }
                    updateState(ServiceState.Idle)
                }.onFailure { error ->
                    Log.e(TAG, "Transcription failed", error)
                    showError("Transcription failed: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                showError(e.message ?: "Unknown error")
            } finally {
                currentPcmFile?.delete()
                wavFile?.delete()
            }
        }
    }

    private fun showError(message: String) {
        updateState(ServiceState.Error(message))
        hapticFeedback?.error()
        Toast.makeText(this@BubbleService, message, Toast.LENGTH_SHORT).show()
        scope.launch {
            delay(2000)
            updateState(ServiceState.Idle)
        }
    }

    private fun updateState(newState: ServiceState) {
        state = newState
        scope.launch(Dispatchers.Main) {
            bubbleView?.updateState(newState)
            // When done transcribing, hide bubble if keyboard is gone
            if (newState is ServiceState.Idle && !TextInjectorService.isKeyboardVisible) {
                hideBubble()
            }
        }
    }
}
