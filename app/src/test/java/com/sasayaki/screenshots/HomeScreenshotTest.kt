@file:Suppress("ktlint:standard:max-line-length") // Prose and fixture rows read better unwrapped.

package com.sasayaki.screenshots

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sasayaki.data.db.entity.DictationStats
import com.sasayaki.data.db.entity.DictationSummary
import com.sasayaki.domain.model.Profile
import com.sasayaki.ui.common.SetupItem
import com.sasayaki.ui.home.HomeScreen
import com.sasayaki.ui.home.HomeUiState
import com.sasayaki.ui.theme.SasayakiIcons
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class HomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()

    private val profiles = listOf(
        Profile(id = 1, name = "Chat", isActive = true, language = "en", llmEnabled = true),
        Profile(id = 2, name = "Email", llmEnabled = true)
    )

    private val recent = listOf(
        DictationSummary(1, "Running ten minutes late, save me a seat and order the usual please.", 13, now - 120_000, "Signal", null, 5_000, "SUCCESS", null, 1, null),
        DictationSummary(2, "Thanks for the notes. I'll review the draft tonight and send comments tomorrow morning.", 15, now - 3_600_000, "Gmail", null, 6_000, "SUCCESS", null, 2, null),
        DictationSummary(3, "", 0, now - 7_200_000, "Slack", null, 3_000, "FAILURE", "Server unreachable", 1, "/audio.wav")
    )

    private fun step(key: String, title: String, done: Boolean, required: Boolean = true) = SetupItem(
        key = key,
        title = title,
        description = "Explains why this permission matters.",
        icon = SasayakiIcons.Mic,
        done = done,
        required = required,
        actionLabel = "Allow",
        onAction = {}
    )

    @Composable
    private fun home(
        state: HomeUiState,
        steps: List<SetupItem> = emptyList(),
        restricted: Boolean = false,
        voiceInputEnabled: Boolean = false
    ) = HomeScreen(
        state = state,
        setupSteps = steps,
        voiceInputEnabled = voiceInputEnabled,
        restrictedSettingsLikely = restricted,
        snackbarHostState = SnackbarHostState(),
        onStartService = {},
        onStopService = {},
        onSelectProfile = {},
        onOpenProfiles = {},
        onOpenHistory = {},
        onOpenDictation = {},
        onCopy = {}
    )

    @Test
    fun running() = compose.screenshot("home_running_light") {
        home(
            HomeUiState(
                loaded = true,
                serviceRunning = true,
                speechProviderConfigured = true,
                profiles = profiles,
                today = DictationStats(14, 812, 300_000),
                lifetime = DictationStats(420, 21_400, 9_100_000),
                recent = recent
            )
        )
    }

    @Test
    fun runningDark() = compose.screenshot("home_running_dark", dark = true) {
        home(
            HomeUiState(
                loaded = true,
                serviceRunning = true,
                speechProviderConfigured = true,
                profiles = profiles,
                today = DictationStats(14, 812, 300_000),
                lifetime = DictationStats(420, 21_400, 9_100_000),
                recent = recent
            )
        )
    }

    @Test
    fun setup() = compose.screenshot("home_setup_light") {
        home(
            HomeUiState(loaded = true, profiles = profiles),
            listOf(
                step("provider", "Speech to text", done = false),
                step("microphone", "Microphone", done = true),
                step("overlay", "Display over other apps", done = false),
                step("accessibility", "Text insertion", done = false),
                step("notifications", "Notifications", done = false, required = false)
            ),
            restricted = true
        )
    }

    @Test
    fun keyboardOnly() = compose.screenshot("home_keyboard_light") {
        home(
            HomeUiState(loaded = true, speechProviderConfigured = true, profiles = profiles, recent = recent),
            listOf(
                step("microphone", "Microphone", done = true),
                step("overlay", "Display over other apps", done = false, required = false),
                step("accessibility", "Text insertion", done = false, required = false)
            ),
            voiceInputEnabled = true
        )
    }

    @Test
    fun off() = compose.screenshot("home_off_dark", dark = true) {
        home(HomeUiState(loaded = true, speechProviderConfigured = true, profiles = profiles))
    }
}
