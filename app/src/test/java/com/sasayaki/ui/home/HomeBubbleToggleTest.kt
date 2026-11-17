package com.sasayaki.ui.home

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class HomeBubbleToggleTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping the status card text turns the bubble on`() {
        var starts = 0
        compose.setContent {
            HomeScreen(
                state = HomeUiState(loaded = true, serviceRunning = false, speechProviderConfigured = true),
                setupSteps = emptyList(),
                voiceInputEnabled = false,
                restrictedSettingsLikely = false,
                snackbarHostState = SnackbarHostState(),
                onStartService = { starts++ },
                onStopService = {},
                onSelectProfile = {},
                onOpenProfiles = {},
                onOpenHistory = {},
                onOpenDictation = {},
                onCopy = {}
            )
        }

        compose.onNodeWithText("The bubble is off", useUnmergedTree = false).assertIsOff().performClick()

        compose.runOnIdle { assertEquals(1, starts) }
    }
}
