package com.sasayaki.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.R
import com.sasayaki.domain.model.PostProcessingPrompt
import com.sasayaki.ui.components.DestructiveDialog
import com.sasayaki.ui.components.LargeTitleBar
import com.sasayaki.ui.components.SasayakiScaffold
import com.sasayaki.ui.components.SectionLabel
import com.sasayaki.ui.components.SegmentedGroup
import com.sasayaki.ui.components.segmentedItemColors
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.Spacing

@Composable
fun PromptsRoute(onBack: () -> Unit, viewModel: PromptsViewModel = hiltViewModel()) {
    val prompts by viewModel.prompts.collectAsStateWithLifecycle()
    PromptsScreen(
        prompts = prompts,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onBack
    )
}

@Composable
fun PromptsScreen(
    prompts: List<PostProcessingPrompt>?,
    onSave: (PostProcessingPrompt) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit
) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var viewingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val custom = prompts.orEmpty().filterNot(PostProcessingPrompt::builtIn)
    val builtIn = prompts.orEmpty().filter(PostProcessingPrompt::builtIn)

    SasayakiScaffold(
        topBar = { scrollBehavior ->
            LargeTitleBar(title = stringResource(R.string.instructions_title), scrollBehavior = scrollBehavior, onBack = onBack)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(SasayakiIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.instructions_new)) }
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
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.instructions_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs)
                )
            }
            if (prompts != null) {
                item(key = "custom") {
                    if (custom.isEmpty()) {
                        Column {
                            SectionLabel(stringResource(R.string.instructions_yours))
                            Text(
                                text = stringResource(R.string.instructions_yours_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.l)
                            )
                        }
                    } else {
                        SegmentedGroup(
                            title = stringResource(R.string.instructions_yours),
                            items = custom.map { prompt -> { shapes -> PromptItem(prompt, shapes) { editingId = prompt.id } } }
                        )
                    }
                }
                if (builtIn.isNotEmpty()) {
                    item(key = "builtin") {
                        SegmentedGroup(
                            title = stringResource(R.string.instructions_built_in),
                            items = builtIn.map { prompt -> { shapes -> PromptItem(prompt, shapes) { viewingId = prompt.id } } }
                        )
                    }
                }
            }
        }
    }

    val editing = custom.firstOrNull { it.id == editingId }
    if (creating || editing != null) {
        PromptEditorSheet(
            initial = editing ?: PostProcessingPrompt(title = "", prompt = ""),
            onSave = {
                onSave(it)
                creating = false
                editingId = null
            },
            onDelete = editing?.let { prompt ->
                {
                    onDelete(prompt.id)
                    editingId = null
                }
            },
            onDismiss = {
                creating = false
                editingId = null
            }
        )
    }

    builtIn.firstOrNull { it.id == viewingId }?.let { prompt ->
        BuiltInPromptSheet(prompt, onDismiss = { viewingId = null })
    }
}

@Composable
private fun PromptItem(prompt: PostProcessingPrompt, shapes: ListItemShapes, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = segmentedItemColors(),
        supportingContent = { Text(prompt.prompt, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    ) {
        Text(prompt.title.ifBlank { stringResource(R.string.instructions_untitled) })
    }
}

@Composable
private fun PromptEditorSheet(
    initial: PostProcessingPrompt,
    onSave: (PostProcessingPrompt) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var confirmDelete by rememberSaveable(initial.id) { mutableStateOf(false) }
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var text by rememberSaveable(initial.id) { mutableStateOf(initial.prompt) }

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
            Text(
                stringResource(if (initial.id == 0L) R.string.instructions_new else R.string.instructions_edit),
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.instructions_name)) },
                singleLine = true
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                label = { Text(stringResource(R.string.instructions_instruction)) },
                placeholder = { Text(stringResource(R.string.instructions_placeholder)) },
                minLines = 5,
                maxLines = 12
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.s)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.action_delete)) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = { onSave(initial.copy(title = title.trim(), prompt = text.trim())) },
                    enabled = title.isNotBlank() && text.isNotBlank(),
                    modifier = Modifier.padding(start = Spacing.s)
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        DestructiveDialog(
            icon = SasayakiIcons.Delete,
            title = if (initial.title.isBlank()) {
                stringResource(R.string.instructions_delete_unnamed_title)
            } else {
                stringResource(R.string.instructions_delete_named_title, initial.title)
            },
            text = stringResource(R.string.instructions_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun BuiltInPromptSheet(prompt: PostProcessingPrompt, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Text(prompt.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.instructions_built_in_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(prompt.prompt, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
