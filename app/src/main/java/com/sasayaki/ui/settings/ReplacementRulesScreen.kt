package com.sasayaki.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.domain.model.TextReplacementRule
import com.sasayaki.domain.processing.applyTo
import com.sasayaki.domain.processing.patternError
import com.sasayaki.ui.components.DestructiveDialog
import com.sasayaki.ui.components.EmptyState
import com.sasayaki.ui.components.LargeTitleBar
import com.sasayaki.ui.components.Pill
import com.sasayaki.ui.components.SasayakiScaffold
import com.sasayaki.ui.components.segmentedItemColors
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.Spacing

@Composable
fun ReplacementRulesRoute(onBack: () -> Unit, viewModel: ReplacementRulesViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    ReplacementRulesScreen(
        rules = rules,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onBack
    )
}

@Composable
fun ReplacementRulesScreen(
    rules: List<TextReplacementRule>?,
    onSave: (TextReplacementRule) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit
) {
    var editing by rememberSaveable(stateSaver = RuleDraftSaver) { mutableStateOf<TextReplacementRule?>(null) }

    SasayakiScaffold(
        topBar = { scrollBehavior -> LargeTitleBar(title = "Replacement rules", scrollBehavior = scrollBehavior, onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = TextReplacementRule(name = "", pattern = "", replacement = "") },
                icon = { Icon(SasayakiIcons.Add, contentDescription = null) },
                text = { Text("New rule") }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.xxxl * 2
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            item(key = "intro") {
                Text(
                    text = "Rules fix words before cleanup runs. Choose which ones each profile uses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.l)
                )
            }
            when {
                rules == null -> Unit

                rules.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = SasayakiIcons.FindReplace,
                        title = "No rules yet",
                        body = "Add one for a name or term your speech model keeps getting wrong."
                    )
                }

                else -> itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    SegmentedListItem(
                        onClick = { editing = rule },
                        shapes = ListItemDefaults.segmentedShapes(index, rules.size),
                        colors = segmentedItemColors(),
                        modifier = Modifier.animateItem(),
                        leadingContent = { Pill(if (rule.isRegex) ".*" else "Aa") },
                        supportingContent = rule.name.takeIf { it.isNotBlank() }?.let { { Text(it) } }
                    ) {
                        Text(
                            text = "${rule.pattern}  →  ${rule.replacement.ifEmpty { "∅" }}",
                            fontFamily = if (rule.isRegex) FontFamily.Monospace else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    editing?.let { rule ->
        RuleEditorSheet(
            initial = rule,
            onSave = {
                onSave(it)
                editing = null
            },
            onDelete = if (rule.id != 0L) {
                (
                    {
                        onDelete(rule.id)
                        editing = null
                    }
                    )
            } else {
                null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun RuleEditorSheet(
    initial: TextReplacementRule,
    onSave: (TextReplacementRule) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var confirmDelete by rememberSaveable(initial.id) { mutableStateOf(false) }
    var pattern by rememberSaveable(initial.id) { mutableStateOf(initial.pattern) }
    var replacement by rememberSaveable(initial.id) { mutableStateOf(initial.replacement) }
    var isRegex by rememberSaveable(initial.id) { mutableStateOf(initial.isRegex) }
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var sample by rememberSaveable(initial.id) { mutableStateOf("") }

    val draft = initial.copy(name = name.trim(), pattern = pattern, replacement = replacement, isRegex = isRegex)
    val error = draft.patternError()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Text(if (initial.id == 0L) "New rule" else "Edit rule", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } }
            )
            OutlinedTextField(
                value = replacement,
                onValueChange = { replacement = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Replace with") },
                singleLine = true,
                supportingText = { Text(if (isRegex) "Use \$1, \$2… for captured groups." else "Leave empty to remove the match.") }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Regular expression", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Match patterns instead of exact text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = isRegex, onCheckedChange = { isRegex = it })
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                singleLine = true
            )
            OutlinedTextField(
                value = sample,
                onValueChange = { sample = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Try it") },
                placeholder = { Text("Type a sentence to test the rule") },
                leadingIcon = { Icon(SasayakiIcons.Science, contentDescription = null) }
            )
            if (sample.isNotBlank() && pattern.isNotBlank() && error == null) {
                Text(
                    text = draft.applyTo(sample),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(Spacing.m),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.s)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { onSave(draft) },
                    enabled = pattern.isNotBlank() && error == null,
                    modifier = Modifier.padding(start = Spacing.s)
                ) { Text("Save") }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        DestructiveDialog(
            icon = SasayakiIcons.Delete,
            title = "Delete this rule?",
            text = "Profiles that use it stop applying it.",
            confirmLabel = "Delete",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }
}

private val RuleDraftSaver = Saver<TextReplacementRule?, List<Any>>(
    save = { rule -> rule?.let { listOf(it.id, it.name, it.pattern, it.replacement, it.isRegex, it.createdAt) } },
    restore = { values ->
        TextReplacementRule(
            id = values[0] as Long,
            name = values[1] as String,
            pattern = values[2] as String,
            replacement = values[3] as String,
            isRegex = values[4] as Boolean,
            createdAt = values[5] as Long
        )
    }
)
