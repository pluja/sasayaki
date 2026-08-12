package com.sasayaki.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.domain.model.PostProcessingPrompt
import com.sasayaki.domain.model.TextReplacementRule
import com.sasayaki.ui.common.SasayakiScaffold
import com.sasayaki.ui.common.SasayakiTopBar
import com.sasayaki.ui.common.SectionCard
import com.sasayaki.ui.common.StatusPill

private sealed interface SettingsMode {
    data object Main : SettingsMode
    data object Rules : SettingsMode
    data object Prompts : SettingsMode
    data object BuiltIns : SettingsMode
}

@Composable
fun SettingsScreen(
    outerPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val prompts by viewModel.prompts.collectAsStateWithLifecycle()
    val asrTestState by viewModel.asrTestState.collectAsStateWithLifecycle()
    val llmTestState by viewModel.llmTestState.collectAsStateWithLifecycle()
    val asrSaved by viewModel.asrSaved.collectAsStateWithLifecycle()
    val llmSaved by viewModel.llmSaved.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf<SettingsMode>(SettingsMode.Main) }
    var editingRule by remember { mutableStateOf<TextReplacementRule?>(null) }
    var editingPrompt by remember { mutableStateOf<PostProcessingPrompt?>(null) }
    var viewingPrompt by remember { mutableStateOf<PostProcessingPrompt?>(null) }

    BackHandler(enabled = mode != SettingsMode.Main) {
        mode = when (mode) {
            SettingsMode.BuiltIns -> SettingsMode.Prompts
            else -> SettingsMode.Main
        }
    }

    when (mode) {
        SettingsMode.Main -> SettingsMainScreen(
            preferences = preferences,
            asrSaved = asrSaved,
            llmSaved = llmSaved,
            asrTestState = asrTestState,
            llmTestState = llmTestState,
            onSaveAsr = viewModel::saveAsrConfig,
            onSaveLlm = viewModel::saveLlmConfig,
            onTestAsr = viewModel::testAsrConnection,
            onTestLlm = viewModel::testLlmConnection,
            onSaveGeneral = viewModel::saveGeneralSettings,
            onRules = { mode = SettingsMode.Rules },
            onPrompts = { mode = SettingsMode.Prompts },
            outerPadding = outerPadding
        )
        SettingsMode.Rules -> TextReplacementRulesScreen(
            rules = rules,
            onBack = { mode = SettingsMode.Main },
            onAdd = { editingRule = TextReplacementRule(name = "", pattern = "", replacement = "") },
            onEdit = { editingRule = it },
            onDelete = { viewModel.deleteRule(it.id) },
            outerPadding = outerPadding
        )
        SettingsMode.Prompts -> PostProcessingPromptsScreen(
            prompts = prompts,
            onBack = { mode = SettingsMode.Main },
            onBuiltIns = { mode = SettingsMode.BuiltIns },
            onAdd = { editingPrompt = PostProcessingPrompt(title = "", prompt = "") },
            onEdit = { editingPrompt = it },
            onDelete = { viewModel.deletePrompt(it.id) },
            outerPadding = outerPadding
        )
        SettingsMode.BuiltIns -> BuiltInPromptsScreen(
            prompts = prompts.filter(PostProcessingPrompt::builtIn),
            onBack = { mode = SettingsMode.Prompts },
            onOpen = { viewingPrompt = it },
            outerPadding = outerPadding
        )
    }

    editingRule?.let { rule ->
        RuleDialog(
            initialRule = rule,
            onDismiss = { editingRule = null },
            onSave = {
                viewModel.saveRule(it)
                editingRule = null
            }
        )
    }

    editingPrompt?.let { prompt ->
        PromptDialog(
            initialPrompt = prompt,
            onDismiss = { editingPrompt = null },
            onSave = {
                viewModel.savePrompt(it)
                editingPrompt = null
            }
        )
    }

    viewingPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewingPrompt = null },
            title = { Text(prompt.title.ifBlank { "Built-in prompt" }) },
            text = {
                LazyColumn {
                    item {
                        Text(prompt.prompt, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingPrompt = null }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun SettingsMainScreen(
    preferences: UserPreferences,
    asrSaved: Boolean,
    llmSaved: Boolean,
    asrTestState: TestState,
    llmTestState: TestState,
    onSaveAsr: (String, String, String) -> Unit,
    onSaveLlm: (String, String, String, Boolean) -> Unit,
    onTestAsr: (String, String, String) -> Unit,
    onTestLlm: (String, String, String) -> Unit,
    onSaveGeneral: (Boolean, Boolean, Boolean, Long, Boolean, Boolean, Int, Boolean) -> Unit,
    onRules: () -> Unit,
    onPrompts: () -> Unit,
    outerPadding: PaddingValues
) {
    SasayakiScaffold(
        topBar = {
            SasayakiTopBar(
                title = "Settings",
                subtitle = "Global providers, reusable processing assets, recording, and history."
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = settingsContentPadding(padding, outerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("providers") {
                ProviderSettingsSection(
                    preferences = preferences,
                    asrSaved = asrSaved,
                    llmSaved = llmSaved,
                    asrTestState = asrTestState,
                    llmTestState = llmTestState,
                    onSaveAsr = onSaveAsr,
                    onSaveLlm = onSaveLlm,
                    onTestAsr = onTestAsr,
                    onTestLlm = onTestLlm
                )
            }
            item("processing") {
                SectionCard(title = "Processing", subtitle = "Reusable assets selected inside profiles.") {
                    NavigationRow("Text replacement rules", "Add exact or regex replacements.", onRules)
                    NavigationRow("Post-processing prompts", "Manage custom prompts and inspect built-ins.", onPrompts)
                }
            }
            item("recording") {
                RecordingHistorySection(preferences = preferences, onSave = onSaveGeneral)
            }
        }
    }
}

@Composable
private fun TextReplacementRulesScreen(
    rules: List<TextReplacementRule>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (TextReplacementRule) -> Unit,
    onDelete: (TextReplacementRule) -> Unit,
    outerPadding: PaddingValues
) {
    DrillInScaffold(
        title = "Text replacement rules",
        onBack = onBack,
        onAdd = onAdd,
        outerPadding = outerPadding
    ) { padding ->
        if (rules.isEmpty()) {
            EmptyProcessingState("No rules yet", "Create replacements here and enable them in profiles.")
        } else {
            LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rules, key = { it.id }) { rule ->
                    RuleManagementRow(rule = rule, onEdit = { onEdit(rule) }, onDelete = { onDelete(rule) })
                }
            }
        }
    }
}

@Composable
private fun PostProcessingPromptsScreen(
    prompts: List<PostProcessingPrompt>,
    onBack: () -> Unit,
    onBuiltIns: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PostProcessingPrompt) -> Unit,
    onDelete: (PostProcessingPrompt) -> Unit,
    outerPadding: PaddingValues
) {
    val customPrompts = prompts.filterNot(PostProcessingPrompt::builtIn)
    DrillInScaffold(
        title = "Post-processing prompts",
        onBack = onBack,
        onAdd = onAdd,
        outerPadding = outerPadding
    ) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(
                    onClick = onBuiltIns,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Built-in prompts", style = MaterialTheme.typography.titleMedium)
                            Text("App maintained prompts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (customPrompts.isEmpty()) {
                item { EmptyProcessingState("No custom prompts yet", "Create prompts here and use them in profiles.") }
            } else {
                items(customPrompts, key = { it.id }) { prompt ->
                    PromptManagementRow(prompt = prompt, onEdit = { onEdit(prompt) }, onDelete = { onDelete(prompt) })
                }
            }
        }
    }
}

@Composable
private fun BuiltInPromptsScreen(
    prompts: List<PostProcessingPrompt>,
    onBack: () -> Unit,
    onOpen: (PostProcessingPrompt) -> Unit,
    outerPadding: PaddingValues
) {
    DrillInScaffold(title = "Built-in prompts", onBack = onBack, onAdd = null, outerPadding = outerPadding) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(prompts, key = { it.id }) { prompt ->
                Surface(
                    onClick = { onOpen(prompt) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            prompt.prompt,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrillInScaffold(
    title: String,
    onBack: () -> Unit,
    onAdd: (() -> Unit)?,
    outerPadding: PaddingValues,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            }
        },
        floatingActionButton = {
            if (onAdd != null) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add") }
                )
            }
        }
    ) { scaffoldPadding ->
        content(
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = scaffoldPadding.calculateTopPadding() + 16.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + outerPadding.calculateBottomPadding() + 96.dp
            )
        )
    }
}

@Composable
private fun RuleManagementRow(rule: TextReplacementRule, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(if (rule.isRegex) ".*" else "Aa")
            Text(rule.pattern, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rule.replacement.ifBlank { "(Empty)" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit ${rule.name}") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete ${rule.name}") }
        }
    }
}

@Composable
private fun PromptManagementRow(prompt: PostProcessingPrompt, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(prompt.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(prompt.prompt, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit ${prompt.title}") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete ${prompt.title}") }
        }
    }
}

@Composable
private fun EmptyProcessingState(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 220.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun NavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
}

@Composable
private fun ProviderSettingsSection(
    preferences: UserPreferences,
    asrSaved: Boolean,
    llmSaved: Boolean,
    asrTestState: TestState,
    llmTestState: TestState,
    onSaveAsr: (String, String, String) -> Unit,
    onSaveLlm: (String, String, String, Boolean) -> Unit,
    onTestAsr: (String, String, String) -> Unit,
    onTestLlm: (String, String, String) -> Unit
) {
    var asrUrl by rememberSaveable(preferences.asrBaseUrl) { mutableStateOf(preferences.asrBaseUrl) }
    var asrKey by rememberSaveable(preferences.asrApiKey) { mutableStateOf(preferences.asrApiKey) }
    var asrModel by rememberSaveable(preferences.asrModel) { mutableStateOf(preferences.asrModel) }
    var llmUrl by rememberSaveable(preferences.llmBaseUrl) { mutableStateOf(preferences.llmBaseUrl) }
    var llmKey by rememberSaveable(preferences.llmApiKey) { mutableStateOf(preferences.llmApiKey) }
    var llmModel by rememberSaveable(preferences.llmModel) { mutableStateOf(preferences.llmModel) }
    var llmEnabled by rememberSaveable(preferences.llmEnabled) { mutableStateOf(preferences.llmEnabled) }

    val asrUrlValid by remember { derivedStateOf { isSecureUrl(asrUrl) } }
    val llmUrlValid by remember { derivedStateOf { isSecureUrl(llmUrl) } }

    SectionCard(title = "API providers", subtitle = "Endpoints and API keys remain global. Profiles choose models and language.") {
        StatusPill("Speech provider")
        SettingTextField(asrUrl, { asrUrl = it }, "ASR base URL", "https://api.openai.com/", if (asrUrlValid) null else "Use an HTTPS endpoint.")
        SettingTextField(asrKey, { asrKey = it }, "ASR API key", isSecret = true)
        SettingTextField(asrModel, { asrModel = it }, "Fallback ASR model")
        SaveAndTestRow(
            saved = asrSaved,
            state = asrTestState,
            canSave = asrUrlValid && asrModel.isNotBlank(),
            canTest = asrUrl.isNotBlank() && asrKey.isNotBlank() && asrModel.isNotBlank() && asrUrlValid,
            onSave = { onSaveAsr(asrUrl, asrKey, asrModel) },
            onTest = { onTestAsr(asrUrl, asrKey, asrModel) }
        )
        TestResultBanner(asrTestState)

        SettingSwitchRow("Enable LLM fallback", "New default profiles use this as their initial post-processing state.", llmEnabled, { llmEnabled = it })
        SettingTextField(llmUrl, { llmUrl = it }, "LLM base URL", "https://api.openai.com/", if (llmUrlValid) null else "Use an HTTPS endpoint.", enabled = llmEnabled)
        SettingTextField(llmKey, { llmKey = it }, "LLM API key", enabled = llmEnabled, isSecret = true)
        SettingTextField(llmModel, { llmModel = it }, "Fallback LLM model", enabled = llmEnabled)
        SaveAndTestRow(
            saved = llmSaved,
            state = llmTestState,
            canSave = !llmEnabled || (llmUrlValid && llmModel.isNotBlank()),
            canTest = llmEnabled && llmUrl.isNotBlank() && llmKey.isNotBlank() && llmModel.isNotBlank() && llmUrlValid,
            onSave = { onSaveLlm(llmUrl, llmKey, llmModel, llmEnabled) },
            onTest = { onTestLlm(llmUrl, llmKey, llmModel) }
        )
        TestResultBanner(llmTestState)
    }
}

@Composable
private fun RecordingHistorySection(
    preferences: UserPreferences,
    onSave: (Boolean, Boolean, Boolean, Long, Boolean, Boolean, Int, Boolean) -> Unit
) {
    var silenceThreshold by rememberSaveable(preferences.silenceThresholdMs) {
        mutableStateOf(preferences.silenceThresholdMs.toFloat())
    }
    var retentionText by rememberSaveable(preferences.historyRetentionLimit) {
        mutableStateOf(preferences.historyRetentionLimit.toString())
    }

    fun save(
        autoClipboard: Boolean = preferences.autoClipboard,
        vibrateOnRecord: Boolean = preferences.vibrateOnRecord,
        pauseOtherAudio: Boolean = preferences.pauseOtherAudio,
        silenceThresholdMs: Long = silenceThreshold.toLong(),
        historyEnabled: Boolean = preferences.historyEnabled,
        keepStatsWithoutHistory: Boolean = preferences.keepStatsWithoutHistory,
        historyRetentionLimit: Int = retentionText.toIntOrNull() ?: preferences.historyRetentionLimit,
        startOnBoot: Boolean = preferences.startOnBoot
    ) {
        onSave(autoClipboard, vibrateOnRecord, pauseOtherAudio, silenceThresholdMs, historyEnabled, keepStatsWithoutHistory, historyRetentionLimit, startOnBoot)
    }

    SectionCard(title = "Recording and history", subtitle = "Control recording feedback, audio focus, and saved transcript retention.") {
        SettingSwitchRow("Start on boot", "Bring the dictation service back after the device restarts.", preferences.startOnBoot, { save(startOnBoot = it) })
        SettingSwitchRow("Clipboard fallback", "Copy text when direct insertion is unavailable.", preferences.autoClipboard, { save(autoClipboard = it) })
        SettingSwitchRow("Haptic feedback", "Vibrate when recording starts, stops, or fails.", preferences.vibrateOnRecord, { save(vibrateOnRecord = it) })
        SettingSwitchRow("Pause other audio", "Request audio focus while recording so other apps pause.", preferences.pauseOtherAudio, { save(pauseOtherAudio = it) })
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Silence detection", style = MaterialTheme.typography.titleMedium)
                StatusPill("${"%.1f".format(silenceThreshold / 1000f)}s")
            }
            Slider(
                value = silenceThreshold,
                onValueChange = { silenceThreshold = it },
                onValueChangeFinished = { save(silenceThresholdMs = silenceThreshold.toLong()) },
                valueRange = 500f..5000f,
                steps = 8
            )
        }
        SettingSwitchRow("Save dictation history", "Keep transcripts and retryable audio on this device.", preferences.historyEnabled, { save(historyEnabled = it) })
        SettingSwitchRow("Keep stats without history", "When history is off, store counts and durations without dictated content.", preferences.keepStatsWithoutHistory, { save(keepStatsWithoutHistory = it) }, enabled = !preferences.historyEnabled)
        OutlinedTextField(
            value = retentionText,
            onValueChange = { value -> retentionText = value.filter(Char::isDigit).take(4) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Transcriptions to keep") },
            singleLine = true
        )
        Button(onClick = { save(historyRetentionLimit = retentionText.toIntOrNull() ?: preferences.historyRetentionLimit) }) {
            Text("Save retention")
        }
    }
}

@Composable
private fun RuleDialog(initialRule: TextReplacementRule, onDismiss: () -> Unit, onSave: (TextReplacementRule) -> Unit) {
    var name by rememberSaveable(initialRule.id) { mutableStateOf(initialRule.name) }
    var pattern by rememberSaveable(initialRule.id) { mutableStateOf(initialRule.pattern) }
    var replacement by rememberSaveable(initialRule.id) { mutableStateOf(initialRule.replacement) }
    var isRegex by rememberSaveable(initialRule.id) { mutableStateOf(initialRule.isRegex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule.id == 0L) "Add rule" else "Edit rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(pattern, { pattern = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Find") }, singleLine = true)
                OutlinedTextField(replacement, { replacement = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Replace with") }, singleLine = true)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Regex")
                    Switch(checked = isRegex, onCheckedChange = { isRegex = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = pattern.isNotBlank(), onClick = { onSave(initialRule.copy(name = name, pattern = pattern, replacement = replacement, isRegex = isRegex)) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PromptDialog(initialPrompt: PostProcessingPrompt, onDismiss: () -> Unit, onSave: (PostProcessingPrompt) -> Unit) {
    var title by rememberSaveable(initialPrompt.id) { mutableStateOf(initialPrompt.title) }
    var prompt by rememberSaveable(initialPrompt.id) { mutableStateOf(initialPrompt.prompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialPrompt.id == 0L) "Add prompt" else "Edit prompt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
                OutlinedTextField(prompt, { prompt = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Prompt") }, minLines = 5)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && prompt.isNotBlank(), onClick = { onSave(initialPrompt.copy(title = title, prompt = prompt)) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

internal fun settingsContentPadding(padding: PaddingValues, outerPadding: PaddingValues): PaddingValues {
    return PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = padding.calculateTopPadding() + 16.dp,
        bottom = padding.calculateBottomPadding() + outerPadding.calculateBottomPadding() + 96.dp
    )
}
