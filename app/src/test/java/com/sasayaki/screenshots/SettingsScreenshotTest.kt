package com.sasayaki.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sasayaki.data.preferences.BubbleSize
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.data.repository.ConnectionResult
import com.sasayaki.domain.model.TextReplacementRule
import com.sasayaki.domain.provider.ModelInfo
import com.sasayaki.domain.provider.ModelKind
import com.sasayaki.domain.provider.Provider
import com.sasayaki.ui.common.SetupItem
import com.sasayaki.ui.components.SettingsPage
import com.sasayaki.ui.components.SetupGroup
import com.sasayaki.ui.settings.AppearanceActions
import com.sasayaki.ui.settings.AppearanceSettingsScreen
import com.sasayaki.ui.settings.AppsSettingsScreen
import com.sasayaki.ui.settings.ProviderEditorActions
import com.sasayaki.ui.settings.ProviderEditorScreen
import com.sasayaki.ui.settings.ProviderEditorUiState
import com.sasayaki.ui.settings.ProviderForm
import com.sasayaki.ui.settings.ProvidersScreen
import com.sasayaki.ui.settings.ReplacementRulesScreen
import com.sasayaki.ui.settings.SettingsDestinations
import com.sasayaki.ui.settings.SettingsScreen
import com.sasayaki.ui.settings.SettingsUiState
import com.sasayaki.ui.settings.VocabularyScreen
import com.sasayaki.ui.theme.SasayakiIcons
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val providers = listOf(
        Provider(1, "Home server", "https://asr.home.lan", speechModel = "deepdml/faster-whisper-large-v3-turbo-ct2"),
        Provider(2, "llama-swap", "https://llm.home.lan", cleanupModel = "gpt-oss-120b"),
        Provider(3, "OpenAI", "https://api.openai.com", apiKey = "sk", speechModel = "whisper-1", cleanupModel = "gpt-4o-mini")
    )

    private val noDestinations = SettingsDestinations({}, {}, {}, {}, {}, {}, {}, {}, {})

    private val editorActions = ProviderEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun overview() = compose.screenshot("settings_overview_light") {
        SettingsScreen(
            state = SettingsUiState(loaded = true, preferences = UserPreferences(), providers = providers, ruleCount = 3),
            missingPermissions = 1,
            voiceInputEnabled = false,
            onOpenKeyboardSettings = {},
            destinations = noDestinations,
            onOpenSource = {}
        )
    }

    @Test
    fun overviewDark() = compose.screenshot("settings_overview_dark", dark = true) {
        SettingsScreen(
            state = SettingsUiState(loaded = true, preferences = UserPreferences(), providers = emptyList()),
            missingPermissions = 0,
            voiceInputEnabled = true,
            onOpenKeyboardSettings = {},
            destinations = noDestinations,
            onOpenSource = {}
        )
    }

    @Test
    fun apps() = compose.screenshot("settings_apps_light") {
        AppsSettingsScreen(
            loaded = true,
            prefs = UserPreferences(appProfiles = mapOf("com.slack" to 1L, "com.google.android.gm" to 2L)),
            onRememberProfilesChange = {},
            onForgetApps = {},
            onSkipAiChatsChange = {},
            onBack = {}
        )
    }

    @Test
    fun appearance() = compose.screenshot("settings_appearance_dark", dark = true) {
        AppearanceSettingsScreen(
            loaded = true,
            prefs = UserPreferences(bubbleTranslucent = true, bubbleSize = BubbleSize.TINY, controlsSize = BubbleSize.LARGE),
            bubbleRunning = true,
            actions = AppearanceActions({}, {}, {}, {}, {}, {}, {}, {}),
            onBack = {}
        )
    }

    @Test
    fun appearanceBubbleOff() = compose.screenshot("settings_appearance_off_light") {
        AppearanceSettingsScreen(
            loaded = true,
            prefs = UserPreferences(),
            bubbleRunning = false,
            actions = AppearanceActions({}, {}, {}, {}, {}, {}, {}, {}),
            onBack = {}
        )
    }

    @Test
    fun vocabulary() = compose.screenshot("settings_vocabulary_light") {
        VocabularyScreen(
            terms = listOf("Grafana", "Kubernetes", "Mika Häkkinen", "Sasayaki", "Tailscale", "vLLM", "WisprFlow"),
            onAdd = {},
            onRemove = {},
            onBack = {}
        )
    }

    @Test
    fun providers() = compose.screenshot("settings_providers_light") {
        ProvidersScreen(providers = providers, onOpenProvider = {}, onBack = {})
    }

    @Test
    fun providersEmpty() = compose.screenshot("settings_providers_empty_dark", dark = true) {
        ProvidersScreen(providers = emptyList(), onOpenProvider = {}, onBack = {})
    }

    @Test
    fun newProvider() = compose.screenshot("settings_provider_new_light") {
        ProviderEditorScreen(
            isNew = true,
            form = ProviderForm(name = "", url = "", apiKey = "", speechModel = null, cleanupModel = null),
            state = ProviderEditorUiState(loaded = true),
            actions = editorActions
        )
    }

    @Test
    fun connectedProvider() = compose.screenshot("settings_provider_connected_dark", dark = true) {
        val models = listOf(
            ModelInfo("deepdml/faster-whisper-large-v3-turbo-ct2", ModelKind.SPEECH),
            ModelInfo("Systran/faster-whisper-small", ModelKind.SPEECH),
            ModelInfo("gpt-oss-120b", ModelKind.CHAT),
            ModelInfo("kokoro", ModelKind.OTHER)
        )
        ProviderEditorScreen(
            isNew = false,
            form = ProviderForm("Home server", "https://asr.home.lan", "secret", "deepdml/faster-whisper-large-v3-turbo-ct2", null),
            state = ProviderEditorUiState(loaded = true, result = ConnectionResult.Connected(models), models = models),
            actions = editorActions
        )
    }

    @Test
    fun failedProvider() = compose.screenshot("settings_provider_failed_light") {
        ProviderEditorScreen(
            isNew = false,
            form = ProviderForm("OpenAI", "https://api.openai.com", "wrong", "whisper-1", "gpt-4o-mini"),
            state = ProviderEditorUiState(loaded = true, result = ConnectionResult.Failed("The server rejected the API key.")),
            actions = editorActions
        )
    }

    @Test
    fun permissionsRestricted() = compose.screenshot("settings_permissions_restricted_light") {
        SettingsPage(title = "Permissions", onBack = {}) {
            item {
                SetupGroup(
                    items = listOf(
                        SetupItem(
                            "microphone",
                            "Microphone",
                            "Records your voice while the bubble is listening.",
                            SasayakiIcons.Mic,
                            true,
                            true,
                            "Allow"
                        ) {
                        },
                        SetupItem(
                            "overlay",
                            "Display over other apps",
                            "Lets the bubble float above your keyboard.",
                            SasayakiIcons.Layers,
                            true,
                            true,
                            "Allow"
                        ) {
                        },
                        SetupItem(
                            "accessibility",
                            "Text insertion",
                            "Types the result into the field you are using.",
                            SasayakiIcons.Keyboard,
                            false,
                            true,
                            "Allow"
                        ) {
                        },
                        SetupItem(
                            "notifications",
                            "Notifications",
                            "Keeps the bubble running reliably.",
                            SasayakiIcons.Notifications,
                            false,
                            false,
                            "Allow"
                        ) {
                        }
                    ),
                    restrictedSettingsLikely = true
                )
            }
        }
    }

    @Test
    fun rules() = compose.screenshot("settings_rules_dark", dark = true) {
        ReplacementRulesScreen(
            rules = listOf(
                TextReplacementRule(1, "Product name", "sasa yaki", "Sasayaki"),
                TextReplacementRule(2, "", """(\d+) percent""", "$1%", isRegex = true)
            ),
            onSave = {},
            onDelete = {},
            onBack = {}
        )
    }
}
