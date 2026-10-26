package com.sasayaki.service.voice

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.data.repository.ProfileRepository
import com.sasayaki.domain.model.AppContext
import com.sasayaki.service.HapticFeedback
import com.sasayaki.service.bubble.BubbleActions
import com.sasayaki.service.bubble.BubbleAppearance
import com.sasayaki.service.bubble.OverlayLifecycleOwner
import com.sasayaki.service.dictation.RecordingProcessor
import com.sasayaki.service.fitToCursor
import com.sasayaki.service.isBankingPackage
import com.sasayaki.service.isSensitiveInputType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * VoiceInputService is a voice-only input method. Its auxiliary voice subtype is what keyboards
 * look for when their mic key hands off to voice input. It stays open after typing, so more can be
 * dictated, until the keyboard key hands back.
 */
@AndroidEntryPoint
class VoiceInputService : InputMethodService() {
    @Inject lateinit var recordingProcessor: RecordingProcessor

    @Inject lateinit var preferencesDataStore: PreferencesDataStore

    @Inject lateinit var profileRepository: ProfileRepository

    private val scope = MainScope()
    private val lifecycleOwner = OverlayLifecycleOwner()
    private lateinit var session: VoiceInputSession
    private var hapticFeedback: HapticFeedback? = null

    // canSwitchBack turns false when no previous keyboard exists, for example after Sasayaki
    // was picked as the keyboard; recording then waits for a tap instead of starting on focus.
    private var canSwitchBack = true

    private val preferences by lazy {
        preferencesDataStore.preferences.stateIn(scope, SharingStarted.Eagerly, UserPreferences())
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.start()
        hapticFeedback = HapticFeedback(this)
        session = VoiceInputSession(
            scope = scope,
            processor = recordingProcessor,
            preferences = preferencesDataStore.preferences,
            activeProfile = { packageName ->
                profileRepository.switchToRememberedProfile(packageName)
                profileRepository.getActiveProfile()
            },
            pickProfile = { packageName, profileId ->
                profileRepository.activate(profileId)
                profileRepository.rememberProfileForApp(packageName, profileId)
                profileRepository.getActiveProfile()
            },
            waveformBars = KEYBOARD_WAVEFORM_BARS,
            cacheDir = cacheDir,
            onRecordingChanged = ::buzz,
            onText = ::insert
        )
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let(lifecycleOwner::attachTo)
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val state by session.state.collectAsStateWithLifecycle()
                val levels = session.levels.collectAsStateWithLifecycle()
                val prefs by preferences.collectAsStateWithLifecycle()
                val profiles by profileRepository.profiles.collectAsStateWithLifecycle(emptyList())
                VoiceInputPanel(
                    state = state,
                    levels = { levels.value },
                    profiles = profiles,
                    actions = BubbleActions(
                        onStart = session::retry,
                        onStop = session::stop,
                        onCancel = ::cancelAndSwitchBack,
                        onTogglePause = session::togglePause,
                        onCancelProcessing = ::cancelAndSwitchBack,
                        onRetry = session::retry,
                        onDismissError = ::cancelAndSwitchBack
                    ),
                    appearance = BubbleAppearance(prefs.wallpaperColors, prefs.bubbleTranslucent, prefs.bubbleMonochrome),
                    onSelectProfile = session::selectProfile,
                    onSwitchKeyboard = ::cancelAndSwitchBack
                )
            }
        }
    }

    // The mic key expects dictation to start at once, so the panel opens recording.
    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        if (restarting) return
        val target = target(editorInfo)
        if (canSwitchBack) session.start(target) else session.prepare(target)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        session.cancel()
        super.onFinishInputView(finishingInput)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        session.cancel()
        scope.cancel()
        lifecycleOwner.destroy()
        super.onDestroy()
    }

    private fun target(editorInfo: EditorInfo): VoiceInputTarget {
        val packageName = editorInfo.packageName
        val label = packageName?.let { name ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(name, 0)).toString() }.getOrNull()
        }
        return VoiceInputTarget(
            appContext = AppContext(label = label, packageName = packageName).takeIf { it.hasData },
            sensitive = isSensitiveInputType(editorInfo.inputType) || isBankingPackage(packageName),
            micAllowed = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            incognito = editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        )
    }

    private fun insert(text: String): Boolean {
        val connection = currentInputConnection ?: return false
        val before = connection.getTextBeforeCursor(CURSOR_CONTEXT_CHARS, 0)?.toString()
        if (!connection.commitText(fitToCursor(before, text), 1)) return false
        // The panel stays open so another dictation can follow, unless the setting hands back to the
        // keyboard; the buzz says this one landed.
        scope.launch {
            val prefs = preferencesDataStore.preferences.first()
            if (prefs.vibrateOnRecord) hapticFeedback?.complete()
            if (prefs.returnToKeyboardAfterTyping) switchBack()
        }
        return true
    }

    private fun cancelAndSwitchBack() {
        session.cancel()
        switchBack()
    }

    private fun switchBack() {
        canSwitchBack = switchToPreviousInputMethod()
        if (!canSwitchBack) requestHideSelf(0)
    }

    private fun buzz(recording: Boolean) {
        scope.launch {
            if (!preferencesDataStore.preferences.first().vibrateOnRecord) return@launch
            if (recording) hapticFeedback?.recordStart() else hapticFeedback?.recordStop()
        }
    }

    private companion object {
        const val CURSOR_CONTEXT_CHARS = 16
    }
}
