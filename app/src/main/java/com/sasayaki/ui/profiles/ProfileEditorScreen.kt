package com.sasayaki.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.domain.model.OutputStyle
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.SummarizeMode
import com.sasayaki.domain.model.languageName
import com.sasayaki.domain.provider.Provider
import com.sasayaki.domain.provider.Purpose
import com.sasayaki.domain.provider.suitableFor
import com.sasayaki.ui.components.ConnectedChoice
import com.sasayaki.ui.components.DestructiveDialog
import com.sasayaki.ui.components.ItemIcon
import com.sasayaki.ui.components.ModelPickerSheet
import com.sasayaki.ui.components.NavigationItem
import com.sasayaki.ui.components.Pill
import com.sasayaki.ui.components.SegmentedGroup
import com.sasayaki.ui.components.SegmentedItem
import com.sasayaki.ui.components.SettingsPage
import com.sasayaki.ui.components.SwitchItem
import com.sasayaki.ui.components.segmentedItemColors
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.Spacing

private enum class ProfileSheet { LANGUAGE, SPEECH_PROVIDER, SPEECH_MODEL, CLEANUP_PROVIDER, CLEANUP_MODEL, PROMPTS, RULES }

class ProfileEditorNavigation(
    val onBack: () -> Unit,
    val onOpenProfile: (Long) -> Unit,
    val onManageProviders: () -> Unit,
    val onManagePrompts: () -> Unit,
    val onManageRules: () -> Unit
)

@Composable
fun ProfileEditorRoute(navigation: ProfileEditorNavigation, viewModel: ProfileEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) {
        if (finished) navigation.onBack()
    }
    val profile = viewModel.profile ?: return
    ProfileEditorScreen(
        profile = profile,
        state = state,
        navigation = navigation,
        onUpdate = viewModel::update,
        onActivate = viewModel::activate,
        onDuplicate = { viewModel.duplicate(navigation.onOpenProfile) },
        onDelete = viewModel::delete,
        onLoadModels = viewModel::loadModels
    )
}

@Composable
fun ProfileEditorScreen(
    profile: Profile,
    state: ProfileEditorUiState,
    navigation: ProfileEditorNavigation,
    onUpdate: ((Profile) -> Profile) -> Unit,
    onActivate: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onLoadModels: (Long) -> Unit
) {
    var sheet by rememberSaveable { mutableStateOf<ProfileSheet?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val speech = resolvedChoice(state.providers, Purpose.SPEECH, profile.speechProviderId, profile.asrModel)
    val cleanup = resolvedChoice(state.providers, Purpose.CLEANUP, profile.cleanupProviderId, profile.llmModel)

    SettingsPage(
        title = profile.name.ifBlank { "Profile" },
        onBack = navigation.onBack,
        actions = {
            ProfileMenu(canDelete = state.profileCount > 1, onDuplicate = onDuplicate, onDelete = { confirmDelete = true })
        }
    ) {
        item(key = "status") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (state.active) {
                    Pill(
                        text = "Active profile",
                        icon = SasayakiIcons.Check,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    FilledTonalButton(onClick = onActivate) { Text("Use this profile") }
                }
            }
        }
        item(key = "name") {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
                OutlinedTextField(
                    value = profile.name,
                    onValueChange = { value -> onUpdate { it.copy(name = value) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.l),
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
        }
        item(key = "speech") {
            SegmentedGroup(
                title = "Speech to text",
                items = listOf(
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Language",
                            supporting = profile.language?.let { languageName(it) } ?: "Detect automatically",
                            icon = SasayakiIcons.Translate,
                            onClick = { sheet = ProfileSheet.LANGUAGE }
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Provider",
                            supporting = speech.providerLabel,
                            needsAttention = speech.provider == null,
                            icon = SasayakiIcons.Dns,
                            onClick = { sheet = ProfileSheet.SPEECH_PROVIDER }
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Model",
                            supporting = speech.modelLabel,
                            needsAttention = speech.provider != null && speech.model == null,
                            icon = SasayakiIcons.Mic,
                            enabled = speech.provider != null,
                            onClick = {
                                speech.provider?.let { onLoadModels(it.id) }
                                sheet = ProfileSheet.SPEECH_MODEL
                            }
                        )
                    }
                )
            )
        }
        item(key = "cleanup") {
            val items = buildList<SegmentedItem> {
                add { shapes ->
                    SwitchItem(
                        shapes = shapes,
                        headline = "Clean up text",
                        supporting = "Removes filler words, fixes punctuation and applies your corrections",
                        icon = SasayakiIcons.AutoAwesome,
                        checked = profile.llmEnabled,
                        onCheckedChange = { enabled -> onUpdate { it.copy(llmEnabled = enabled) } }
                    )
                }
                if (profile.llmEnabled) {
                    add { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Provider",
                            supporting = cleanup.providerLabel,
                            needsAttention = cleanup.provider == null,
                            icon = SasayakiIcons.Dns,
                            onClick = { sheet = ProfileSheet.CLEANUP_PROVIDER }
                        )
                    }
                    add { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Model",
                            supporting = cleanup.modelLabel,
                            needsAttention = cleanup.provider != null && cleanup.model == null,
                            icon = SasayakiIcons.AutoAwesome,
                            enabled = cleanup.provider != null,
                            onClick = {
                                cleanup.provider?.let { onLoadModels(it.id) }
                                sheet = ProfileSheet.CLEANUP_MODEL
                            }
                        )
                    }
                }
            }
            SegmentedGroup(title = "Cleanup", items = items)
        }
        if (profile.llmEnabled) {
            item(key = "style") {
                SegmentedGroup(
                    title = "Writing style",
                    items = listOf(
                        { shapes ->
                            StylePreview(shapes, profile.outputStyle, profile.rewriteMode, profile.summarizeMode, profile.emojiAllowed)
                        },
                        { shapes ->
                            StyleItem(
                                shapes,
                                SasayakiIcons.FormatQuote,
                                "Punctuation",
                                OutputStyle.entries,
                                profile.outputStyle,
                                ::styleLabel,
                                ::styleDescription
                            ) { value ->
                                onUpdate { it.copy(outputStyle = value) }
                            }
                        },
                        { shapes ->
                            StyleItem(
                                shapes,
                                SasayakiIcons.AutoFixHigh,
                                "Rewriting",
                                listOf(RewriteMode.NONE, RewriteMode.FIX, RewriteMode.POLISH),
                                profile.rewriteMode,
                                ::rewriteLabel,
                                ::rewriteDescription
                            ) { value -> onUpdate { it.copy(rewriteMode = value) } }
                        },
                        { shapes ->
                            StyleItem(
                                shapes,
                                SasayakiIcons.Compress,
                                "Shorten",
                                SummarizeMode.entries,
                                profile.summarizeMode,
                                ::summarizeLabel,
                                ::summarizeDescription
                            ) { value ->
                                onUpdate { it.copy(summarizeMode = value) }
                            }
                        },
                        { shapes ->
                            SwitchItem(
                                shapes = shapes,
                                headline = "Emoji",
                                supporting = "Allow emoji where they fit the message",
                                icon = SasayakiIcons.Mood,
                                checked = profile.emojiAllowed,
                                onCheckedChange = { allowed -> onUpdate { it.copy(emojiAllowed = allowed) } }
                            )
                        }
                    )
                )
            }
            item(key = "instructions") {
                Column {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
                        OutlinedTextField(
                            value = profile.profilePrompt,
                            onValueChange = { value -> onUpdate { it.copy(profilePrompt = value) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.l),
                            label = { Text("Extra instructions") },
                            placeholder = { Text("I'm a nurse; keep medical terms exactly as said.") },
                            minLines = 3,
                            maxLines = 8,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                        )
                    }
                }
            }
        }
        item(key = "text") {
            val activePrompts = state.customPrompts.count { it.id in profile.selectedPromptIds }
            val activeRules = state.rules.count { it.id in profile.selectedRuleIds }
            val items = buildList<SegmentedItem> {
                add { shapes ->
                    NavigationItem(
                        shapes = shapes,
                        headline = "Replacement rules",
                        supporting = if (state.rules.isEmpty()) "None yet" else "$activeRules of ${state.rules.size} on",
                        icon = SasayakiIcons.FindReplace,
                        onClick = { sheet = ProfileSheet.RULES }
                    )
                }
                if (profile.llmEnabled) {
                    add { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Your prompts",
                            supporting = if (state.customPrompts.isEmpty()) {
                                "None yet"
                            } else {
                                "$activePrompts of ${state.customPrompts.size} on"
                            },
                            icon = SasayakiIcons.Notes,
                            onClick = { sheet = ProfileSheet.PROMPTS }
                        )
                    }
                }
            }
            SegmentedGroup(title = "Text", items = items)
        }
    }

    ProfileSheets(
        sheet = sheet,
        profile = profile,
        state = state,
        speech = speech,
        cleanup = cleanup,
        navigation = navigation,
        onUpdate = onUpdate,
        onDismiss = { sheet = null }
    )

    if (confirmDelete) {
        DestructiveDialog(
            icon = SasayakiIcons.Delete,
            title = "Delete ${profile.name.ifBlank { "this profile" }}?",
            text = "Its settings are removed. Your dictation history stays.",
            confirmLabel = "Delete",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun ProfileSheets(
    sheet: ProfileSheet?,
    profile: Profile,
    state: ProfileEditorUiState,
    speech: ResolvedChoice,
    cleanup: ResolvedChoice,
    navigation: ProfileEditorNavigation,
    onUpdate: ((Profile) -> Profile) -> Unit,
    onDismiss: () -> Unit
) {
    when (sheet) {
        null -> Unit

        ProfileSheet.LANGUAGE -> LanguageSheet(
            selected = profile.language,
            onSelect = { code ->
                onUpdate { it.copy(language = code) }
                onDismiss()
            },
            onDismiss = onDismiss
        )

        ProfileSheet.SPEECH_PROVIDER, ProfileSheet.CLEANUP_PROVIDER -> {
            val purpose = if (sheet == ProfileSheet.SPEECH_PROVIDER) Purpose.SPEECH else Purpose.CLEANUP
            ProviderSheet(
                purpose = purpose,
                providers = state.providers,
                selectedId = if (purpose == Purpose.SPEECH) profile.speechProviderId else profile.cleanupProviderId,
                automaticLabel = automaticProvider(state.providers, purpose)?.let { "Uses ${it.name}" } ?: "No provider offers this yet",
                onSelect = { id ->
                    onUpdate {
                        // A model override belongs to its provider, so switching resets it.
                        if (purpose == Purpose.SPEECH) {
                            it.copy(speechProviderId = id, asrModel = "")
                        } else {
                            it.copy(cleanupProviderId = id, llmModel = "")
                        }
                    }
                    onDismiss()
                },
                onManage = {
                    onDismiss()
                    navigation.onManageProviders()
                },
                onDismiss = onDismiss
            )
        }

        ProfileSheet.SPEECH_MODEL, ProfileSheet.CLEANUP_MODEL -> {
            val purpose = if (sheet == ProfileSheet.SPEECH_MODEL) Purpose.SPEECH else Purpose.CLEANUP
            val choice = if (purpose == Purpose.SPEECH) speech else cleanup
            val provider = choice.provider ?: return
            val models = state.models[provider.id].orEmpty()
            val default = provider.defaultModel(purpose)
            ModelPickerSheet(
                title = if (purpose == Purpose.SPEECH) "Speech to text model" else "Cleanup model",
                models = models.suitableFor(purpose),
                selected = choice.override,
                noneLabel = default?.let { "Provider default · $it" },
                loading = provider.id in state.loadingModels && models.isEmpty(),
                note = if (models.isEmpty() && provider.id !in state.loadingModels) {
                    "${provider.name} didn't list its models. Type one below."
                } else {
                    null
                },
                onSelect = { model ->
                    val value = model.takeUnless { it == default }.orEmpty()
                    onUpdate { if (purpose == Purpose.SPEECH) it.copy(asrModel = value) else it.copy(llmModel = value) }
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }

        ProfileSheet.RULES -> ChecklistSheet(
            title = "Replacement rules",
            description = "Rules run on this profile's text before cleanup.",
            entries = state.rules.map {
                ChecklistEntry(it.id, "${it.pattern} → ${it.replacement.ifEmpty { "∅" }}", it.name.ifBlank { null })
            },
            selected = profile.selectedRuleIds,
            emptyText = "You haven't written any rules yet.",
            manageLabel = "Manage rules",
            onToggle = { id -> onUpdate { it.copy(selectedRuleIds = it.selectedRuleIds.toggle(id)) } },
            onManage = {
                onDismiss()
                navigation.onManageRules()
            },
            onDismiss = onDismiss
        )

        ProfileSheet.PROMPTS -> ChecklistSheet(
            title = "Your prompts",
            description = "Built-in prompts always apply. Pick which of yours this profile adds.",
            entries = state.customPrompts.map { ChecklistEntry(it.id, it.title.ifBlank { "Untitled" }, it.prompt) },
            selected = profile.selectedPromptIds,
            emptyText = "You haven't written any prompts yet.",
            manageLabel = "Manage prompts",
            onToggle = { id -> onUpdate { it.copy(selectedPromptIds = it.selectedPromptIds.toggle(id)) } },
            onManage = {
                onDismiss()
                navigation.onManagePrompts()
            },
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun ProfileMenu(canDelete: Boolean, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(SasayakiIcons.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Duplicate") },
                leadingIcon = { Icon(SasayakiIcons.ContentCopy, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDuplicate()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                enabled = canDelete,
                leadingIcon = { Icon(SasayakiIcons.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun <T> StyleItem(
    shapes: ListItemShapes,
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: (T) -> String,
    onSelect: (T) -> Unit
) {
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedItemColors(),
        leadingContent = { ItemIcon(icon) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = Spacing.s)) {
                ConnectedChoice(options = options, selected = selected, onSelect = onSelect, label = label)
                Text(description(selected))
            }
        }
    ) {
        Text(title)
    }
}

data class ResolvedChoice(
    val provider: Provider?,
    val override: String?,
    val model: String?,
    val providerLabel: String,
    val modelLabel: String
)

private fun resolvedChoice(providers: List<Provider>, purpose: Purpose, chosenId: Long?, override: String): ResolvedChoice {
    val chosen = chosenId?.let { id -> providers.firstOrNull { it.id == id } }
    val provider = chosen ?: automaticProvider(providers, purpose)
    val default = provider?.defaultModel(purpose)
    val overrideModel = override.trim().ifEmpty { null }
    return ResolvedChoice(
        provider = provider,
        override = overrideModel,
        model = overrideModel ?: default,
        providerLabel = when {
            provider == null -> "Add a provider first"
            chosen == null -> "Automatic · ${provider.name}"
            else -> provider.name
        },
        modelLabel = when {
            overrideModel != null -> overrideModel
            default != null -> "Default · $default"
            provider == null -> "Not available"
            else -> "Choose a model"
        }
    )
}

private fun automaticProvider(providers: List<Provider>, purpose: Purpose): Provider? =
    providers.firstOrNull { it.defaultModel(purpose) != null }

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

private fun styleLabel(style: OutputStyle) = when (style) {
    OutputStyle.STANDARD -> "Full"
    OutputStyle.RELAXED -> "Light"
    OutputStyle.MINIMAL -> "Minimal"
}

private fun styleDescription(style: OutputStyle) = when (style) {
    OutputStyle.STANDARD -> "Capital letters and complete punctuation."
    OutputStyle.RELAXED -> "Capital letters, lighter punctuation, like a chat."
    OutputStyle.MINIMAL -> "Lowercase with almost no punctuation."
}

private fun rewriteLabel(mode: RewriteMode) = when (mode) {
    RewriteMode.NONE -> "Keep"
    RewriteMode.FIX -> "Fix"
    RewriteMode.POLISH -> "Polish"
}

private fun rewriteDescription(mode: RewriteMode) = when (mode) {
    RewriteMode.NONE -> "Your exact words; only rules and prompts apply."
    RewriteMode.FIX -> "Keeps your wording and repairs the rough edges."
    RewriteMode.POLISH -> "Rewrites into clear, more formal prose."
}

private fun summarizeLabel(mode: SummarizeMode) = when (mode) {
    SummarizeMode.NONE -> "Off"
    SummarizeMode.LIGHT -> "Light"
    SummarizeMode.HARD -> "Strong"
}

private fun summarizeDescription(mode: SummarizeMode) = when (mode) {
    SummarizeMode.NONE -> "Everything you said is kept."
    SummarizeMode.LIGHT -> "Trims rambling and repetition."
    SummarizeMode.HARD -> "Boils it down to a short summary."
}
