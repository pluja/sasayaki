package com.sasayaki.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.ui.common.SasayakiScaffold
import com.sasayaki.ui.common.SasayakiTopBar
import com.sasayaki.ui.common.SectionCard
import com.sasayaki.ui.common.StatusPill

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val asrTestState by viewModel.asrTestState.collectAsStateWithLifecycle()
    val llmTestState by viewModel.llmTestState.collectAsStateWithLifecycle()
    val asrSaved by viewModel.asrSaved.collectAsStateWithLifecycle()
    val llmSaved by viewModel.llmSaved.collectAsStateWithLifecycle()

    var asrUrl by rememberSaveable(preferences.asrBaseUrl) { mutableStateOf(preferences.asrBaseUrl) }
    var asrKey by rememberSaveable(preferences.asrApiKey) { mutableStateOf(preferences.asrApiKey) }
    var asrModel by rememberSaveable(preferences.asrModel) { mutableStateOf(preferences.asrModel) }

    var llmUrl by rememberSaveable(preferences.llmBaseUrl) { mutableStateOf(preferences.llmBaseUrl) }
    var llmKey by rememberSaveable(preferences.llmApiKey) { mutableStateOf(preferences.llmApiKey) }
    var llmModel by rememberSaveable(preferences.llmModel) { mutableStateOf(preferences.llmModel) }
    var llmEnabled by rememberSaveable(preferences.llmEnabled) { mutableStateOf(preferences.llmEnabled) }

    var silenceThreshold by rememberSaveable(preferences.silenceThresholdMs) {
        mutableStateOf(preferences.silenceThresholdMs.toFloat())
    }

    val asrUrlValid by remember { derivedStateOf { isSecureUrl(asrUrl) } }
    val llmUrlValid by remember { derivedStateOf { isSecureUrl(llmUrl) } }
    val canSaveAsr by remember {
        derivedStateOf {
            val dirty = asrUrl != preferences.asrBaseUrl ||
                asrKey != preferences.asrApiKey ||
                asrModel != preferences.asrModel
            dirty && asrUrlValid && asrModel.isNotBlank()
        }
    }
    val canTestAsr by remember {
        derivedStateOf { asrUrl.isNotBlank() && asrKey.isNotBlank() && asrModel.isNotBlank() && asrUrlValid }
    }
    val canSaveLlm by remember {
        derivedStateOf {
            val dirty = llmUrl != preferences.llmBaseUrl ||
                llmKey != preferences.llmApiKey ||
                llmModel != preferences.llmModel ||
                llmEnabled != preferences.llmEnabled
            dirty && (!llmEnabled || (llmUrlValid && llmModel.isNotBlank()))
        }
    }
    val canTestLlm by remember {
        derivedStateOf { llmEnabled && llmUrl.isNotBlank() && llmKey.isNotBlank() && llmModel.isNotBlank() && llmUrlValid }
    }

    SasayakiScaffold(
        topBar = {
            SasayakiTopBar(
                title = "Settings",
                subtitle = "Tune transcription quality, post-processing, and dictation behavior.",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = settingsContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "asr") { AsrSettingsSection(
                url = asrUrl,
                apiKey = asrKey,
                model = asrModel,
                saved = asrSaved,
                state = asrTestState,
                urlValid = asrUrlValid,
                canSave = canSaveAsr,
                canTest = canTestAsr,
                onUrlChange = { asrUrl = it },
                onApiKeyChange = { asrKey = it },
                onModelChange = { asrModel = it },
                onSave = { viewModel.saveAsrConfig(asrUrl, asrKey, asrModel) },
                onTest = { viewModel.testAsrConnection(asrUrl, asrKey, asrModel) }
            ) }

            item(key = "llm") { LlmSettingsSection(
                url = llmUrl,
                apiKey = llmKey,
                model = llmModel,
                enabled = llmEnabled,
                saved = llmSaved,
                state = llmTestState,
                urlValid = llmUrlValid,
                canSave = canSaveLlm,
                canTest = canTestLlm,
                onEnabledChange = { llmEnabled = it },
                onUrlChange = { llmUrl = it },
                onApiKeyChange = { llmKey = it },
                onModelChange = { llmModel = it },
                onSave = { viewModel.saveLlmConfig(llmUrl, llmKey, llmModel, llmEnabled) },
                onTest = { viewModel.testLlmConnection(llmUrl, llmKey, llmModel) }
            ) }

            item(key = "lang") { LanguageSettingsSection(
                languages = preferences.preferredLanguages,
                onAddLanguage = { viewModel.addPreferredLanguage(it) },
                onRemoveLanguage = { viewModel.removePreferredLanguage(it) }
            ) }

            item(key = "general") { GeneralSettingsSection(
                preferences = preferences,
                silenceThreshold = silenceThreshold,
                onAutoClipboardChange = {
                    viewModel.saveGeneralSettings(
                        autoClipboard = it,
                        vibrateOnRecord = preferences.vibrateOnRecord,
                        silenceThresholdMs = silenceThreshold.toLong(),
                        historyEnabled = preferences.historyEnabled
                    )
                },
                onVibrateOnRecordChange = {
                    viewModel.saveGeneralSettings(
                        autoClipboard = preferences.autoClipboard,
                        vibrateOnRecord = it,
                        silenceThresholdMs = silenceThreshold.toLong(),
                        historyEnabled = preferences.historyEnabled
                    )
                },
                onSilenceThresholdChange = { silenceThreshold = it },
                onSilenceThresholdSave = {
                    viewModel.saveGeneralSettings(
                        autoClipboard = preferences.autoClipboard,
                        vibrateOnRecord = preferences.vibrateOnRecord,
                        silenceThresholdMs = silenceThreshold.toLong(),
                        historyEnabled = preferences.historyEnabled
                    )
                },
                onHistoryEnabledChange = {
                    viewModel.saveGeneralSettings(
                        autoClipboard = preferences.autoClipboard,
                        vibrateOnRecord = preferences.vibrateOnRecord,
                        silenceThresholdMs = silenceThreshold.toLong(),
                        historyEnabled = it
                    )
                }
            ) }
        }
    }
}

@Composable
private fun AsrSettingsSection(
    url: String,
    apiKey: String,
    model: String,
    saved: Boolean,
    state: TestState,
    urlValid: Boolean,
    canSave: Boolean,
    canTest: Boolean,
    onUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    SectionCard(
        title = "Speech recognition",
        subtitle = "Point Sasayaki at the transcription endpoint that should receive your audio."
    ) {
        StatusPill(
            label = "ASR",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )

        SettingTextField(
            value = url,
            onValueChange = onUrlChange,
            label = "Base URL",
            placeholder = "https://api.openai.com/",
            supportingText = if (urlValid) null else "Use an HTTPS endpoint."
        )

        SettingTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = "API key",
            isSecret = true
        )

        SettingTextField(
            value = model,
            onValueChange = onModelChange,
            label = "Model",
            supportingText = "Example: whisper-1"
        )

        SaveAndTestRow(
            saved = saved,
            state = state,
            canSave = canSave,
            canTest = canTest,
            onSave = onSave,
            onTest = onTest
        )

        TestResultBanner(state = state)
    }
}

@Composable
private fun LlmSettingsSection(
    url: String,
    apiKey: String,
    model: String,
    enabled: Boolean,
    saved: Boolean,
    state: TestState,
    urlValid: Boolean,
    canSave: Boolean,
    canTest: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    SectionCard(
        title = "Post-processing",
        subtitle = "Optionally polish transcripts with an LLM before they are inserted."
    ) {
        SettingSwitchRow(
            title = "Enable LLM cleanup",
            description = "Use a second model to tidy punctuation, spelling, and casing before insertion.",
            checked = enabled,
            onCheckedChange = onEnabledChange
        )

        SettingTextField(
            value = url,
            onValueChange = onUrlChange,
            label = "Base URL",
            supportingText = when {
                !enabled -> "Disabled until LLM cleanup is enabled."
                urlValid -> null
                else -> "Use an HTTPS endpoint."
            },
            enabled = enabled
        )

        SettingTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = "API key",
            enabled = enabled,
            isSecret = true
        )

        SettingTextField(
            value = model,
            onValueChange = onModelChange,
            label = "Model",
            supportingText = if (enabled) "Example: gpt-4o-mini" else null,
            enabled = enabled
        )

        SaveAndTestRow(
            saved = saved,
            state = state,
            canSave = canSave,
            canTest = canTest,
            onSave = onSave,
            onTest = onTest
        )

        TestResultBanner(state = state)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSettingsSection(
    languages: List<String>,
    onAddLanguage: (String) -> Unit,
    onRemoveLanguage: (String) -> Unit
) {
    var languageInput by rememberSaveable { mutableStateOf("") }

    SectionCard(
        title = "Languages",
        subtitle = "Set preferred languages for transcription and text cleanup. With one language, it is sent to the ASR for better accuracy. With multiple, the ASR auto-detects per dictation."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = languageInput,
                onValueChange = { languageInput = it.lowercase().filter { char -> char.isLetter() }.take(3) },
                modifier = Modifier.weight(1f),
                label = { Text("Language code") },
                placeholder = { Text("en, es, ja...") },
                singleLine = true,
                supportingText = { Text("ISO 639-1 codes") }
            )
            Button(
                onClick = {
                    val code = languageInput.trim()
                    if (code.isNotBlank() && code !in languages) {
                        onAddLanguage(code)
                        languageInput = ""
                    }
                },
                enabled = languageInput.trim().length in 2..3
            ) {
                Text("Add")
            }
        }

        if (languages.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                languages.forEach { code ->
                    InputChip(
                        selected = false,
                        onClick = { onRemoveLanguage(code) },
                        label = { Text(code) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove $code",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsSection(
    preferences: UserPreferences,
    silenceThreshold: Float,
    onAutoClipboardChange: (Boolean) -> Unit,
    onVibrateOnRecordChange: (Boolean) -> Unit,
    onSilenceThresholdChange: (Float) -> Unit,
    onSilenceThresholdSave: () -> Unit,
    onHistoryEnabledChange: (Boolean) -> Unit
) {
    SectionCard(
        title = "Behavior",
        subtitle = "Decide how Sasayaki behaves when insertion fails, when recording starts, and how much history stays on the device."
    ) {
        SettingSwitchRow(
            title = "Clipboard fallback",
            description = "When direct insertion is unavailable, copy the dictated text so you can paste it manually.",
            checked = preferences.autoClipboard,
            onCheckedChange = onAutoClipboardChange
        )

        SettingSwitchRow(
            title = "Haptic feedback",
            description = "Give a short vibration when recording starts and stops.",
            checked = preferences.vibrateOnRecord,
            onCheckedChange = onVibrateOnRecordChange
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Silence detection",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Stop recording after ${"%.1f".format(silenceThreshold / 1000f)} seconds of silence.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(label = "${"%.1f".format(silenceThreshold / 1000f)}s")
            }

            Slider(
                value = silenceThreshold,
                onValueChange = onSilenceThresholdChange,
                onValueChangeFinished = onSilenceThresholdSave,
                valueRange = 500f..5000f,
                steps = 8
            )
        }

        SettingSwitchRow(
            title = "Save dictation history",
            description = "Keep recent dictations on the device so you can review and copy them later.",
            checked = preferences.historyEnabled,
            onCheckedChange = onHistoryEnabledChange
        )
    }
}
