package com.sasayaki.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sasayaki.data.repository.ConnectionResult
import com.sasayaki.domain.provider.ModelInfo
import com.sasayaki.domain.provider.ModelKind
import com.sasayaki.ui.common.SetupItem
import com.sasayaki.ui.onboarding.DictationMethod
import com.sasayaki.ui.onboarding.OnboardingActions
import com.sasayaki.ui.onboarding.OnboardingScreen
import com.sasayaki.ui.onboarding.OnboardingSetup
import com.sasayaki.ui.onboarding.OnboardingStep
import com.sasayaki.ui.settings.ProviderEditorActions
import com.sasayaki.ui.settings.ProviderEditorUiState
import com.sasayaki.ui.settings.ProviderForm
import com.sasayaki.ui.theme.SasayakiIcons
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class OnboardingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val models = listOf(ModelInfo("whisper-1", ModelKind.SPEECH), ModelInfo("gpt-4o-mini", ModelKind.CHAT))
    private val actions = ProviderEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {})
    private val permissions = listOf(
        SetupItem("microphone", "Microphone", "Records your voice while you dictate.", SasayakiIcons.Mic, true, true, "Allow") {
        },
        SetupItem(
            "overlay",
            "Display over other apps",
            "Lets the bubble float above your keyboard.",
            SasayakiIcons.Layers,
            false,
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
            "Shows that the bubble is on, with a button to turn it off.",
            SasayakiIcons.Notifications,
            false,
            false,
            "Allow"
        ) {
        }
    )

    @Composable
    private fun onboarding(
        step: OnboardingStep,
        form: ProviderForm = ProviderForm("", "", "", null, null),
        state: ProviderEditorUiState = ProviderEditorUiState(loaded = true),
        method: DictationMethod = DictationMethod.BUBBLE,
        items: List<SetupItem> = permissions
    ) = OnboardingScreen(
        step = step,
        providerForm = form,
        providerState = state,
        providerActions = actions,
        setup = OnboardingSetup(method, items, restrictedSettingsLikely = false, bubbleRunning = false),
        actions = OnboardingActions(onStep = {}, onMethod = {}, onSaveProvider = {}, onFinish = {})
    )

    @Test
    fun welcome() = compose.screenshot("onboarding_welcome_light") { onboarding(OnboardingStep.WELCOME) }

    @Test
    fun welcomeDark() = compose.screenshot("onboarding_welcome_dark", dark = true) { onboarding(OnboardingStep.WELCOME) }

    @Test
    fun provider() = compose.screenshot("onboarding_provider_light") {
        onboarding(
            OnboardingStep.PROVIDER,
            ProviderForm("OpenAI", "https://api.openai.com/v1", "sk-secret", "whisper-1", null),
            ProviderEditorUiState(loaded = true, result = ConnectionResult.Connected(models), models = models)
        )
    }

    @Test
    fun permissions() = compose.screenshot("onboarding_permissions_dark", dark = true) { onboarding(OnboardingStep.PERMISSIONS) }

    @Test
    fun method() = compose.screenshot("onboarding_method_light") { onboarding(OnboardingStep.METHOD) }

    @Test
    fun keyboardSetup() = compose.screenshot("onboarding_keyboard_light") {
        onboarding(
            OnboardingStep.PERMISSIONS,
            method = DictationMethod.KEYBOARD,
            items = listOf(
                permissions.first(),
                SetupItem(
                    "voice_input",
                    "Sasayaki voice input",
                    "Lets your keyboard's mic key open Sasayaki.",
                    SasayakiIcons.Keyboard,
                    false,
                    true,
                    "Turn on"
                ) {
                }
            )
        )
    }

    @Test
    fun tryIt() = compose.screenshot("onboarding_try_light") { onboarding(OnboardingStep.TRY) }
}
