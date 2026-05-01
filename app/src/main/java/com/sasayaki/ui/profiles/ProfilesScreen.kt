package com.sasayaki.ui.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.domain.model.PostProcessingPrompt
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.TextReplacementRule
import com.sasayaki.ui.common.SasayakiScaffold
import com.sasayaki.ui.common.SasayakiTopBar
import com.sasayaki.ui.common.StatusPill
import com.sasayaki.ui.theme.SasayakiIcons

private sealed interface ProfilesMode {
    data object List : ProfilesMode
    data class Edit(val profile: Profile) : ProfilesMode
    data class Rules(val profile: Profile) : ProfilesMode
    data class Prompts(val profile: Profile) : ProfilesMode
}

@Composable
fun ProfilesScreen(
    outerPadding: PaddingValues,
    viewModel: ProfilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf<ProfilesMode>(ProfilesMode.List) }

    BackHandler(enabled = mode is ProfilesMode.Edit) {
        mode = ProfilesMode.List
    }

    when (val currentMode = mode) {
        ProfilesMode.List -> ProfilesListScreen(
            profiles = uiState.profiles,
            onActivate = viewModel::activate,
            onEdit = { mode = ProfilesMode.Edit(it) },
            onDuplicate = viewModel::duplicate,
            onDelete = viewModel::delete,
            onCreate = { mode = ProfilesMode.Edit(Profile(name = "New profile")) },
            outerPadding = outerPadding
        )
        is ProfilesMode.Edit -> ProfileEditScreen(
            profile = currentMode.profile,
            rules = uiState.rules,
            prompts = uiState.prompts,
            onBack = { mode = ProfilesMode.List },
            onSave = {
                viewModel.save(it)
                mode = ProfilesMode.List
            },
            onRules = { mode = ProfilesMode.Rules(it) },
            onPrompts = { mode = ProfilesMode.Prompts(it) },
            outerPadding = outerPadding
        )
        is ProfilesMode.Rules -> ProfileRulePickerScreen(
            profile = currentMode.profile,
            rules = uiState.rules,
            onBack = { updated -> mode = ProfilesMode.Edit(updated) },
            outerPadding = outerPadding
        )
        is ProfilesMode.Prompts -> ProfilePromptPickerScreen(
            profile = currentMode.profile,
            prompts = uiState.prompts,
            onBack = { updated -> mode = ProfilesMode.Edit(updated) },
            outerPadding = outerPadding
        )
    }
}

@Composable
private fun ProfilesListScreen(
    profiles: List<Profile>,
    onActivate: (Long) -> Unit,
    onEdit: (Profile) -> Unit,
    onDuplicate: (Profile) -> Unit,
    onDelete: (Long) -> Unit,
    onCreate: () -> Unit,
    outerPadding: PaddingValues
) {
    SasayakiScaffold(
        topBar = {
            SasayakiTopBar(
                title = "Profiles",
                subtitle = "Language, model, and cleanup presets for different dictation contexts."
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New") },
                elevation = FloatingActionButtonDefaults.elevation()
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = profilesContentPadding(padding, outerPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    onActivate = { onActivate(profile.id) },
                    onEdit = { onEdit(profile) },
                    onDuplicate = { onDuplicate(profile) },
                    onDelete = { onDelete(profile.id) }
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (profile.isActive) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        } else {
            null
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⋮⋮",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profile.isActive) StatusPill("Active")
                }
                ModelLine(icon = "▥", text = profile.asrModel)
                ModelLine(icon = "✦", text = if (profile.llmEnabled) profile.llmModel else "Off")
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButtonSurface(onClick = onDuplicate) {
                        Icon(SasayakiIcons.ContentCopy, contentDescription = "Duplicate ${profile.name}")
                    }
                    IconButtonSurface(onClick = onEdit, prominent = true) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${profile.name}")
                    }
                    if (!profile.isActive) {
                        IconButtonSurface(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${profile.name}")
                        }
                    }
                }
                if (!profile.isActive) {
                    TextButton(onClick = onActivate) { Text("Set active") }
                } else {
                    TextButton(onClick = onDelete, enabled = false) { Text("Active") }
                }
            }
        }
    }
}

@Composable
private fun IconButtonSurface(
    onClick: () -> Unit,
    prominent: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (prominent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (prominent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(modifier = Modifier.size(width = 56.dp, height = 48.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun ModelLine(icon: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileEditScreen(
    profile: Profile,
    rules: List<TextReplacementRule>,
    prompts: List<PostProcessingPrompt>,
    onBack: () -> Unit,
    onSave: (Profile) -> Unit,
    onRules: (Profile) -> Unit,
    onPrompts: (Profile) -> Unit,
    outerPadding: PaddingValues
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var asrModel by rememberSaveable(profile.id) { mutableStateOf(profile.asrModel) }
    var language by rememberSaveable(profile.id) { mutableStateOf(profile.language.orEmpty()) }
    var llmEnabled by rememberSaveable(profile.id) { mutableStateOf(profile.llmEnabled) }
    var llmModel by rememberSaveable(profile.id) { mutableStateOf(profile.llmModel) }
    var profilePrompt by rememberSaveable(profile.id) { mutableStateOf(profile.profilePrompt) }
    var selectedRuleIds by remember(profile.id, profile.selectedRuleIds) { mutableStateOf(profile.selectedRuleIds) }
    var selectedPromptIds by remember(profile.id, profile.selectedPromptIds) { mutableStateOf(profile.selectedPromptIds) }

    fun draft(): Profile = profile.copy(
        name = name,
        asrModel = asrModel,
        language = language.ifBlank { null },
        llmEnabled = llmEnabled,
        llmModel = llmModel,
        profilePrompt = profilePrompt,
        selectedRuleIds = selectedRuleIds,
        selectedPromptIds = selectedPromptIds
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EditorTopBar(
                title = if (profile.id == 0L) "Create profile" else "Edit profile",
                onBack = onBack,
                onSave = { onSave(draft()) }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = profileEditorPadding(padding, outerPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsGroup {
                    FieldRow("Profile name", name, onClick = null)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                        singleLine = true
                    )
                }
            }
            item {
                SectionTitle("Transcription Settings")
                SettingsGroup {
                    FieldRow("Transcription provider", "Global provider")
                    EditableInlineRow("Transcription model", asrModel, onValueChange = { asrModel = it })
                    EditableInlineRow("Language", language, placeholder = "Auto", onValueChange = {
                        language = it.lowercase().filter(Char::isLetter).take(3)
                    })
                }
            }
            item {
                SectionTitle("Post-Processing Settings")
                SettingsGroup {
                    FieldRow(
                        title = "Text replacement rules",
                        value = "${selectedRuleIds.size} of ${rules.size} enabled",
                        onClick = { onRules(draft()) }
                    )
                    ToggleRow("Enable post-processing", llmEnabled, onCheckedChange = { llmEnabled = it })
                    EditableInlineRow("Post-processing model", llmModel, enabled = llmEnabled, onValueChange = { llmModel = it })
                    EditableInlineRow(
                        title = "Profile-specific prompt",
                        value = profilePrompt,
                        placeholder = "Not configured",
                        minLines = 2,
                        onValueChange = { profilePrompt = it }
                    )
                    FieldRow(
                        title = "Post-processing prompts",
                        value = "${selectedPromptIds.size} of ${prompts.size} enabled",
                        onClick = { onPrompts(draft()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRulePickerScreen(
    profile: Profile,
    rules: List<TextReplacementRule>,
    onBack: (Profile) -> Unit,
    outerPadding: PaddingValues
) {
    var selectedIds by remember(profile.id, profile.selectedRuleIds) { mutableStateOf(profile.selectedRuleIds) }
    BackHandler {
        onBack(profile.copy(selectedRuleIds = selectedIds))
    }
    PickerScreen(
        title = "Text replacement rules",
        onBack = { onBack(profile.copy(selectedRuleIds = selectedIds)) },
        floatingAction = null,
        outerPadding = outerPadding
    ) {
        items(rules, key = { it.id }) { rule ->
            RulePickerRow(
                rule = rule,
                selected = rule.id in selectedIds,
                onToggle = { selectedIds = selectedIds.toggle(rule.id) }
            )
        }
    }
}

@Composable
private fun ProfilePromptPickerScreen(
    profile: Profile,
    prompts: List<PostProcessingPrompt>,
    onBack: (Profile) -> Unit,
    outerPadding: PaddingValues
) {
    var selectedIds by remember(profile.id, profile.selectedPromptIds) { mutableStateOf(profile.selectedPromptIds) }
    BackHandler {
        onBack(profile.copy(selectedPromptIds = selectedIds))
    }
    PickerScreen(
        title = "Post-processing prompts",
        onBack = { onBack(profile.copy(selectedPromptIds = selectedIds)) },
        floatingAction = null,
        outerPadding = outerPadding
    ) {
        items(prompts, key = { it.id }) { prompt ->
            ToggleTextRow(
                title = prompt.prompt.ifBlank { prompt.title },
                selected = prompt.id in selectedIds,
                onToggle = { selectedIds = selectedIds.toggle(prompt.id) }
            )
        }
    }
}

@Composable
private fun PickerScreen(
    title: String,
    onBack: () -> Unit,
    floatingAction: (@Composable () -> Unit)?,
    outerPadding: PaddingValues,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SasayakiTopBar(title = title, onBack = onBack)
        },
        floatingActionButton = { floatingAction?.invoke() }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + outerPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun RulePickerRow(
    rule: TextReplacementRule,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
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
            Switch(checked = selected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun ToggleTextRow(title: String, selected: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Switch(checked = selected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit, onSave: () -> Unit) {
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
        IconButton(onClick = onSave) {
            Icon(Icons.Default.Check, contentDescription = "Save")
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(content = content)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun FieldRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value.ifBlank { "Not configured" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
}

@Composable
private fun EditableInlineRow(
    title: String,
    value: String,
    placeholder: String = "",
    enabled: Boolean = true,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            enabled = enabled,
            minLines = minLines
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

private fun profilesContentPadding(padding: PaddingValues, outerPadding: PaddingValues): PaddingValues {
    return PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = padding.calculateTopPadding() + 16.dp,
        bottom = padding.calculateBottomPadding() + outerPadding.calculateBottomPadding() + 96.dp
    )
}

private fun profileEditorPadding(padding: PaddingValues, outerPadding: PaddingValues): PaddingValues {
    return PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = padding.calculateTopPadding() + 16.dp,
        bottom = padding.calculateBottomPadding() + outerPadding.calculateBottomPadding() + 32.dp
    )
}
