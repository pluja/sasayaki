package com.sasayaki.ui.profiles

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.R
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
        title = profile.name.ifBlank { stringResource(R.string.profile_title_unnamed) },
        onBack = navigation.onBack,
        actions = {
            ProfileMenu(canDelete = state.profileCount > 1, onDuplicate = onDuplicate, onDelete = { confirmDelete = true })
        }
    ) {
        item(key = "status") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (state.active) {
                    Pill(
                        text = stringResource(R.string.profile_active_profile),
                        icon = SasayakiIcons.Check,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    FilledTonalButton(onClick = onActivate) { Text(stringResource(R.string.profile_use_this)) }
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
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
        }
        item(key = "speech") {
            SegmentedGroup(
                title = stringResource(R.string.profile_speech_to_text),
                items = listOf(
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.profile_language),
                            supporting = profile.language?.let { languageName(it) } ?: stringResource(R.string.profile_language_detect),
                            icon = SasayakiIcons.Translate,
                            onClick = { sheet = ProfileSheet.LANGUAGE }
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.profile_provider),
                            supporting = speech.providerLabel,
                            needsAttention = speech.provider == null,
                            icon = SasayakiIcons.Dns,
                            onClick = { sheet = ProfileSheet.SPEECH_PROVIDER }
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.profile_model),
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
        item(key = "corrections") {
            SegmentedGroup(
                title = stringResource(R.string.profile_corrections),
                items = listOf(
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.profile_replacement_rules),
                            supporting = if (state.rules.isEmpty()) {
                                stringResource(R.string.profile_none_yet)
                            } else {
                                enabledCount(state.rules.count { it.id in profile.selectedRuleIds }, state.rules.size)
                            },
                            icon = SasayakiIcons.FindReplace,
                            onClick = { sheet = ProfileSheet.RULES }
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
                        headline = stringResource(R.string.profile_clean_up_text),
                        supporting = stringResource(R.string.profile_clean_up_text_description),
                        icon = SasayakiIcons.AutoAwesome,
                        checked = profile.llmEnabled,
                        onCheckedChange = { enabled -> onUpdate { it.copy(llmEnabled = enabled) } }
                    )
                }
                if (profile.llmEnabled) {
                    add { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.profile_provider),
                            supporting = cleanup.providerLabel,
                            needsAttention = cleanup.provider == null,
                            icon = SasayakiIcons.Dns,
                            onClick = { sheet = ProfileSheet.CLEANUP_PROVIDER }
                        )
                    }
                    add { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.profile_model),
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
            SegmentedGroup(title = stringResource(R.string.profile_cleanup), items = items)
        }
        if (profile.llmEnabled) {
            item(key = "style") {
                SegmentedGroup(
                    title = stringResource(R.string.profile_writing_style),
                    items = listOf(
                        { shapes ->
                            StylePreview(shapes, profile.outputStyle, profile.rewriteMode, profile.summarizeMode, profile.emojiAllowed)
                        },
                        { shapes ->
                            StyleItem(
                                shapes,
                                SasayakiIcons.FormatQuote,
                                R.string.profile_punctuation,
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
                                R.string.profile_rewriting,
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
                                R.string.profile_shorten,
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
                                headline = stringResource(R.string.profile_emoji),
                                supporting = stringResource(R.string.profile_emoji_description),
                                icon = SasayakiIcons.Mood,
                                checked = profile.emojiAllowed,
                                onCheckedChange = { allowed -> onUpdate { it.copy(emojiAllowed = allowed) } }
                            )
                        }
                    )
                )
            }
            item(key = "instructions") {
                val activeInstructions = state.customPrompts.count { it.id in profile.selectedPromptIds }
                SegmentedGroup(
                    title = stringResource(R.string.profile_instructions),
                    items = listOf(
                        { shapes ->
                            Surface(shape = shapes.shape, color = MaterialTheme.colorScheme.surfaceBright) {
                                OutlinedTextField(
                                    value = profile.profilePrompt,
                                    onValueChange = { value -> onUpdate { it.copy(profilePrompt = value) } },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.l),
                                    label = { Text(stringResource(R.string.profile_instructions_label)) },
                                    placeholder = { Text(stringResource(R.string.profile_instructions_placeholder)) },
                                    minLines = 3,
                                    maxLines = 8,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                                )
                            }
                        },
                        { shapes ->
                            NavigationItem(
                                shapes = shapes,
                                headline = stringResource(R.string.profile_cleanup_instructions),
                                supporting = if (state.customPrompts.isEmpty()) {
                                    stringResource(R.string.profile_instructions_none_own)
                                } else {
                                    enabledCount(activeInstructions, state.customPrompts.size)
                                },
                                icon = SasayakiIcons.Notes,
                                onClick = { sheet = ProfileSheet.PROMPTS }
                            )
                        }
                    )
                )
            }
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
            title = if (profile.name.isBlank()) {
                stringResource(R.string.profile_delete_title_unnamed)
            } else {
                stringResource(R.string.profile_delete_title, profile.name)
            },
            text = stringResource(R.string.profile_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
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
                automaticLabel =
                    automaticProvider(state.providers, purpose)?.let { stringResource(R.string.profile_provider_automatic_uses, it.name) }
                        ?: stringResource(R.string.profile_provider_none_offers),
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
            val title = if (purpose == Purpose.SPEECH) R.string.profile_speech_model_title else R.string.profile_cleanup_model_title
            ModelPickerSheet(
                title = stringResource(title),
                models = models.suitableFor(purpose),
                selected = choice.override,
                noneLabel = default?.let { stringResource(R.string.profile_model_provider_default, it) },
                loading = provider.id in state.loadingModels && models.isEmpty(),
                note = if (models.isEmpty() && provider.id !in state.loadingModels) {
                    stringResource(R.string.profile_models_not_listed, provider.name)
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
            title = stringResource(R.string.profile_replacement_rules),
            description = stringResource(R.string.profile_rules_description),
            entries = state.rules.map {
                ChecklistEntry(it.id, "${it.pattern} → ${it.replacement.ifEmpty { "∅" }}", it.name.ifBlank { null })
            },
            selected = profile.selectedRuleIds,
            emptyText = stringResource(R.string.profile_rules_empty),
            manageLabel = stringResource(R.string.profile_manage_rules),
            onToggle = { id -> onUpdate { it.copy(selectedRuleIds = it.selectedRuleIds.toggle(id)) } },
            onManage = {
                onDismiss()
                navigation.onManageRules()
            },
            onDismiss = onDismiss
        )

        ProfileSheet.PROMPTS -> ChecklistSheet(
            title = stringResource(R.string.profile_cleanup_instructions),
            description = stringResource(R.string.profile_instructions_description),
            entries = state.customPrompts.map {
                ChecklistEntry(it.id, it.title.ifBlank { stringResource(R.string.profile_instructions_untitled) }, it.prompt)
            },
            selected = profile.selectedPromptIds,
            emptyText = stringResource(R.string.profile_instructions_empty),
            manageLabel = stringResource(R.string.profile_manage_instructions),
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
            Icon(SasayakiIcons.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_duplicate)) },
                leadingIcon = { Icon(SasayakiIcons.ContentCopy, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDuplicate()
                }
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.action_delete))
                        if (!canDelete) Text(stringResource(R.string.profile_delete_needs_one), style = MaterialTheme.typography.bodySmall)
                    }
                },
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
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    description: (T) -> Int,
    onSelect: (T) -> Unit
) {
    val labels = options.associateWith { stringResource(label(it)) }
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedItemColors(),
        leadingContent = { ItemIcon(icon) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = Spacing.s)) {
                ConnectedChoice(options = options, selected = selected, onSelect = onSelect, label = labels::getValue)
                Text(stringResource(description(selected)))
            }
        }
    ) {
        Text(stringResource(title))
    }
}

data class ResolvedChoice(
    val provider: Provider?,
    val override: String?,
    val model: String?,
    val providerLabel: String,
    val modelLabel: String
)

@Composable
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
            provider == null -> stringResource(R.string.profile_provider_add_first)
            chosen == null -> stringResource(R.string.profile_provider_automatic_named, provider.name)
            else -> provider.name
        },
        modelLabel = when {
            overrideModel != null -> overrideModel
            default != null -> stringResource(R.string.profile_model_default, default)
            provider == null -> stringResource(R.string.profile_model_not_available)
            else -> stringResource(R.string.profile_model_choose)
        }
    )
}

private fun automaticProvider(providers: List<Provider>, purpose: Purpose): Provider? =
    providers.firstOrNull { it.defaultModel(purpose) != null }

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@Composable
private fun enabledCount(enabled: Int, total: Int) = pluralStringResource(R.plurals.profile_enabled_count, enabled, enabled, total)

@StringRes
private fun styleLabel(style: OutputStyle) = when (style) {
    OutputStyle.STANDARD -> R.string.profile_punctuation_full
    OutputStyle.RELAXED -> R.string.profile_punctuation_light
    OutputStyle.MINIMAL -> R.string.profile_punctuation_minimal
}

@StringRes
private fun styleDescription(style: OutputStyle) = when (style) {
    OutputStyle.STANDARD -> R.string.profile_punctuation_full_description
    OutputStyle.RELAXED -> R.string.profile_punctuation_light_description
    OutputStyle.MINIMAL -> R.string.profile_punctuation_minimal_description
}

@StringRes
private fun rewriteLabel(mode: RewriteMode) = when (mode) {
    RewriteMode.NONE -> R.string.profile_rewrite_keep
    RewriteMode.FIX -> R.string.profile_rewrite_fix
    RewriteMode.POLISH -> R.string.profile_rewrite_polish
}

@StringRes
private fun rewriteDescription(mode: RewriteMode) = when (mode) {
    RewriteMode.NONE -> R.string.profile_rewrite_keep_description
    RewriteMode.FIX -> R.string.profile_rewrite_fix_description
    RewriteMode.POLISH -> R.string.profile_rewrite_polish_description
}

@StringRes
private fun summarizeLabel(mode: SummarizeMode) = when (mode) {
    SummarizeMode.NONE -> R.string.profile_shorten_off
    SummarizeMode.LIGHT -> R.string.profile_shorten_light
    SummarizeMode.HARD -> R.string.profile_shorten_strong
}

@StringRes
private fun summarizeDescription(mode: SummarizeMode) = when (mode) {
    SummarizeMode.NONE -> R.string.profile_shorten_off_description
    SummarizeMode.LIGHT -> R.string.profile_shorten_light_description
    SummarizeMode.HARD -> R.string.profile_shorten_strong_description
}
