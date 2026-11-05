package com.sasayaki.service

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.ui.settings.RecordingSettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class RecordingSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the screen timeout setting reflects its value and reports changes`() {
        val prefs = mutableStateOf(UserPreferences())
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            RecordingSettingsScreen(
                loaded = true,
                prefs = prefs.value,
                onSilenceThreshold = {},
                onPauseOtherAudio = {},
                onKeepScreenOnWhileRecording = { enabled ->
                    changes += enabled
                    prefs.value = prefs.value.copy(keepScreenOnWhileRecording = enabled)
                },
                onVibrateOnRecord = {},
                onAutoClipboard = {},
                onStartOnBoot = {},
                onBack = {}
            )
        }

        val setting = compose.onNodeWithText("Keep screen on while recording")
        setting.assertIsOn().performClick().assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(listOf(false, true), changes) }
    }
}
