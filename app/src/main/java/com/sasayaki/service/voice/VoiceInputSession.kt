package com.sasayaki.service.voice

import android.util.Log
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.provider.describeConnectionError
import com.sasayaki.domain.transcription.DictationFailure
import com.sasayaki.service.AudioRecorder
import com.sasayaki.service.CarriedDictation
import com.sasayaki.service.SilenceDetector
import com.sasayaki.service.bubble.BubblePhase
import com.sasayaki.service.bubble.BubbleUiState
import com.sasayaki.service.bubble.WAVEFORM_BARS
import com.sasayaki.service.dictation.ProcessedRecording
import com.sasayaki.service.dictation.ProcessesRecordings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceInputTarget(val appContext: AppContext?, val sensitive: Boolean, val micAllowed: Boolean, val incognito: Boolean)

/**
 * VoiceInputSession records one dictation for the voice-input keyboard, using the same
 * processing as the bubble. It reports progress in the bubble's own states, so the keyboard
 * can draw the bubble's controls.
 */
class VoiceInputSession(
    private val scope: CoroutineScope,
    private val processor: ProcessesRecordings,
    private val preferences: Flow<UserPreferences>,
    private val activeProfile: suspend (packageName: String?) -> Profile,
    private val pickProfile: suspend (packageName: String?, profileId: Long) -> Profile,
    private val cacheDir: File,
    private val onRecordingChanged: (recording: Boolean) -> Unit,
    // onText reports whether the text reached the field.
    private val onText: (String) -> Boolean,
    private val waveformBars: Int = WAVEFORM_BARS,
    // recordingScope runs the recording itself, so it can outlive the panel and pass to the bubble.
    private val recordingScope: CoroutineScope = scope,
    private val newRecorder: () -> AudioRecorder = ::AudioRecorder,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val _state = MutableStateFlow(BubbleUiState())
    val state: StateFlow<BubbleUiState> = _state.asStateFlow()

    private val _levels = MutableStateFlow<List<Float>>(emptyList())
    val levels: StateFlow<List<Float>> = _levels.asStateFlow()

    private var target: VoiceInputTarget? = null

    // recordingTarget is the field a dictation started in. Its app shapes the cleanup even after
    // focus moves to another app, as the bubble's does.
    private var recordingTarget: VoiceInputTarget? = null
    private var recorder: AudioRecorder? = null
    private var pcm: File? = null
    private var recordingJob: Job? = null
    private var watchJob: Job? = null
    private var processingJob: Job? = null
    private var profile: Deferred<Profile>? = null
    private var startedAt = 0L
    private var pausedAt = 0L
    private var pausedMs = 0L
    private var retryEntryId: Long? = null

    private val phase: BubblePhase get() = _state.value.phase

    /** busy is true while a dictation is recording or being turned into text. */
    val busy: Boolean get() = phase is BubblePhase.Recording || phase is BubblePhase.Processing

    /** recording is true while a dictation is recording, paused or not. */
    val recording: Boolean get() = phase is BubblePhase.Recording

    fun start(target: VoiceInputTarget) {
        if (prepare(target)) record(target)
    }

    /** prepare waits for a tap instead of recording, and reports whether recording may start. */
    fun prepare(target: VoiceInputTarget): Boolean {
        this.target = target
        when {
            target.sensitive -> fail("Dictation is off in password and banking fields.")

            !target.micAllowed -> fail("Allow microphone access in Sasayaki first.")

            else -> {
                setPhase(BubblePhase.Idle)
                return true
            }
        }
        return false
    }

    /** retarget points the next dictation at a new field, and leaves the one in progress alone. */
    fun retarget(target: VoiceInputTarget) {
        this.target = target
    }

    fun stop() {
        if (phase !is BubblePhase.Recording) return
        val durationMs = recordedMs()
        val recordedPcm = pcm ?: return
        val job = recordingJob
        stopRecorder()
        setPhase(BubblePhase.Processing(cleaningUp = false))
        processingJob = scope.launch {
            try {
                job?.join()
                val outcome = processor.process(
                    pcm = recordedPcm,
                    durationMs = durationMs,
                    appContext = recordingTarget?.appContext,
                    profile = profile?.await(),
                    onPostProcessing = ::showCleaningUp,
                    incognito = recordingTarget?.incognito == true
                )
                when (outcome) {
                    ProcessedRecording.Empty -> setPhase(BubblePhase.Idle)
                    ProcessedRecording.NoSpeech -> setPhase(BubblePhase.NoSpeech)
                    is ProcessedRecording.Transcribed -> deliver(outcome.result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice input failed", e)
                fail(describeConnectionError(e))
            } finally {
                recordedPcm.delete()
            }
        }
    }

    /**
     * handOver lets go of a recording in progress without stopping it, so the bubble can carry it.
     * It returns null when nothing is recording.
     */
    fun handOver(): CarriedDictation? {
        val current = phase as? BubblePhase.Recording ?: return null
        val handed = CarriedDictation(
            recorder = recorder ?: return null,
            pcm = pcm ?: return null,
            job = recordingJob,
            recordedMs = recordedMs(),
            paused = current.paused,
            appContext = recordingTarget?.appContext,
            profile = profile,
            incognito = recordingTarget?.incognito == true
        )
        watchJob?.cancel()
        recorder = null
        pcm = null
        recordingJob = null
        setPhase(BubblePhase.Idle)
        return handed
    }

    /** takeOver picks up a dictation the bubble carried, still recording, so the panel finishes it. */
    fun takeOver(dictation: CarriedDictation, target: VoiceInputTarget) {
        this.target = target
        recordingTarget = VoiceInputTarget(
            appContext = dictation.appContext,
            sensitive = false,
            micAllowed = true,
            incognito = dictation.incognito
        )
        recorder = dictation.recorder
        pcm = dictation.pcm
        recordingJob = dictation.job
        retryEntryId = null
        val takenAt = now()
        startedAt = takenAt - dictation.recordedMs
        pausedMs = 0
        pausedAt = if (dictation.paused) takenAt else 0
        _levels.value = emptyList()
        profile = dictation.profile
        dictation.profile?.let(::showProfileName)
        _state.update { it.copy(elapsedSeconds = dictation.recordedMs / 1000) }
        setPhase(BubblePhase.Recording(paused = dictation.paused))
        watchJob = scope.launch { watch(dictation.recorder) }
    }

    /** cancel drops whatever is in progress and leaves the field untouched. */
    fun cancel() {
        processingJob?.cancel()
        processingJob = null
        if (phase is BubblePhase.Recording) {
            stopRecorder()
            recordingJob?.cancel()
            pcm?.delete()
        }
        setPhase(BubblePhase.Idle)
    }

    fun togglePause() {
        val recording = phase as? BubblePhase.Recording ?: return
        if (recording.paused) {
            pausedMs += now() - pausedAt
            recorder?.resume()
        } else {
            pausedAt = now()
            recorder?.pause()
        }
        setPhase(BubblePhase.Recording(paused = !recording.paused))
    }

    /** retry sends a saved failure again, or records anew when nothing was heard. */
    fun retry() {
        val entryId = retryEntryId
        when {
            phase is BubblePhase.Failed && entryId != null -> {
                setPhase(BubblePhase.Processing(cleaningUp = false))
                processingJob = scope.launch {
                    try {
                        deliver(processor.retry(entryId, ::showCleaningUp))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Voice input retry failed", e)
                        fail(describeConnectionError(e), entryId)
                    }
                }
            }

            phase is BubblePhase.Failed || phase is BubblePhase.NoSpeech || phase is BubblePhase.Idle -> target?.let(::start)
        }
    }

    /** selectProfile switches the active profile, remembers it for the app, and applies it to a recording in progress. */
    fun selectProfile(profileId: Long) {
        val selection = scope.async { pickProfile(target?.appContext?.packageName, profileId) }
        if (phase is BubblePhase.Recording) profile = selection
        showProfileName(selection)
    }

    private fun record(target: VoiceInputTarget) {
        val newRecorder = newRecorder()
        val file = File(cacheDir, "voice_input_${now()}.pcm")
        recorder = newRecorder
        recordingTarget = target
        pcm = file
        retryEntryId = null
        startedAt = now()
        pausedMs = 0
        _levels.value = emptyList()
        val recordingProfile = recordingScope.async { activeProfile(target.appContext?.packageName) }
        profile = recordingProfile
        showProfileName(recordingProfile)
        _state.update { it.copy(elapsedSeconds = 0) }
        setPhase(BubblePhase.Recording(paused = false))
        onRecordingChanged(true)
        recordingJob = recordingScope.launch(Dispatchers.IO) {
            try {
                newRecorder.record(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice input recording failed", e)
            }
        }
        watchJob = scope.launch { watch(newRecorder) }
    }

    private suspend fun watch(recorder: AudioRecorder) {
        val detector = SilenceDetector(recorder.audioLevel, preferences.first().silenceThresholdMs)
        val levelsJob = scope.launch {
            recorder.audioLevel.collect { level -> _levels.update { (it + level).takeLast(waveformBars) } }
        }
        try {
            delay(SILENCE_GRACE_MS)
            while (true) {
                val recording = phase as? BubblePhase.Recording ?: break
                _state.update { it.copy(elapsedSeconds = recordedMs() / 1000) }
                if (recording.paused) {
                    detector.reset()
                } else if (detector.checkSilence()) {
                    stop()
                    break
                }
                delay(TICK_MS)
            }
        } finally {
            levelsJob.cancel()
        }
    }

    private fun showProfileName(selection: Deferred<Profile>) {
        scope.launch {
            val name = try {
                selection.await().name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the profile", e)
                return@launch
            }
            _state.update { it.copy(profileName = name) }
        }
    }

    private fun stopRecorder() {
        recorder?.stop()
        watchJob?.cancel()
        onRecordingChanged(false)
    }

    private fun deliver(result: Result<String>) {
        result.onSuccess { text ->
            when {
                text.isBlank() -> setPhase(BubblePhase.Idle)
                onText(text) -> _state.update { it.copy(phase = BubblePhase.Idle, justInserted = true) }
                else -> fail("The text field closed before the text arrived.")
            }
        }.onFailure { error ->
            val failure = error as? DictationFailure
            fail(describeConnectionError(failure?.cause ?: error), failure?.retryEntryId)
        }
    }

    private fun fail(message: String, entryId: Long? = null) {
        retryEntryId = entryId
        setPhase(BubblePhase.Failed(message, canRetry = entryId != null))
    }

    // showCleaningUp is called from the processing thread, after a cancel may already have
    // returned the session to idle.
    private fun showCleaningUp() {
        _state.update { current ->
            if (current.phase is BubblePhase.Processing) current.copy(phase = BubblePhase.Processing(cleaningUp = true)) else current
        }
    }

    private fun setPhase(phase: BubblePhase) {
        _state.update { it.copy(phase = phase, justInserted = false) }
    }

    private fun recordedMs(): Long {
        val currentPause = if ((phase as? BubblePhase.Recording)?.paused == true) now() - pausedAt else 0L
        return (now() - startedAt - pausedMs - currentPause).coerceAtLeast(0L)
    }

    private companion object {
        const val TAG = "VoiceInputSession"
        const val SILENCE_GRACE_MS = 1000L
        const val TICK_MS = 100L
    }
}
