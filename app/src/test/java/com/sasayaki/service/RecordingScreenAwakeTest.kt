package com.sasayaki.service

import android.app.Application
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sasayaki.service.bubble.BubbleActions
import com.sasayaki.service.bubble.BubbleAppearance
import com.sasayaki.service.bubble.BubbleContent
import com.sasayaki.service.bubble.BubblePhase
import com.sasayaki.service.bubble.BubbleTheme
import com.sasayaki.service.bubble.BubbleUiState
import com.sasayaki.service.voice.VoiceInputPanel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class RecordingScreenAwakeTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = BubbleActions({}, {}, {}, {}, {}, {}, {})

    @Test
    fun `the keyboard keeps the screen on only for enabled active recording`() {
        assertRecordingWakeBehavior { phase, enabled ->
            VoiceInputPanel(
                state = BubbleUiState(phase),
                levels = { emptyList() },
                profiles = emptyList(),
                actions = actions,
                appearance = BubbleAppearance(),
                onSelectProfile = {},
                onSwitchKeyboard = {},
                keepScreenOnWhileRecording = enabled
            )
        }
    }

    @Test
    fun `the bubble keeps the screen on only for enabled active recording`() {
        assertRecordingWakeBehavior { phase, enabled ->
            BubbleTheme(BubbleAppearance()) {
                BubbleContent(
                    state = BubbleUiState(phase),
                    levels = { emptyList() },
                    actions = actions,
                    onShowProfiles = {},
                    keepScreenOnWhileRecording = enabled
                )
            }
        }
    }

    private fun assertRecordingWakeBehavior(content: @Composable (BubblePhase, Boolean) -> Unit) {
        val phase = mutableStateOf<BubblePhase>(BubblePhase.Idle)
        val enabled = mutableStateOf(true)
        val shown = mutableStateOf(true)
        lateinit var view: View
        compose.setContent {
            if (shown.value) {
                view = LocalView.current
                content(phase.value, enabled.value)
            }
        }

        fun assertAwake(expected: Boolean) = compose.runOnIdle { assertEquals(expected, view.keepScreenOn) }

        assertAwake(false)
        compose.runOnIdle { phase.value = BubblePhase.Recording(paused = false) }
        assertAwake(true)
        compose.runOnIdle { phase.value = BubblePhase.Recording(paused = true) }
        assertAwake(false)
        compose.runOnIdle { phase.value = BubblePhase.Recording(paused = false) }
        assertAwake(true)
        compose.runOnIdle { enabled.value = false }
        assertAwake(false)
        compose.runOnIdle { enabled.value = true }
        assertAwake(true)

        listOf(
            BubblePhase.Processing(cleaningUp = false),
            BubblePhase.Processing(cleaningUp = true),
            BubblePhase.Idle,
            BubblePhase.NoSpeech,
            BubblePhase.Failed("Recording failed", canRetry = false)
        ).forEach { terminal ->
            compose.runOnIdle { phase.value = terminal }
            assertAwake(false)
            compose.runOnIdle { phase.value = BubblePhase.Recording(paused = false) }
            assertAwake(true)
        }

        compose.runOnIdle { shown.value = false }
        assertAwake(false)
    }
}
