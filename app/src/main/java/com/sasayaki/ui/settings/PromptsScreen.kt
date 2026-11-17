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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        topBar = { scrollBehavior -> LargeTitleBar(title = "Cleanup instructions", scrollBehavior = scrollBehavior, onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(SasayakiIcons.Add, contentDescription = null) },
                text = { Text("New instruction") }
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
                    text = "Instructions the cleanup model follows. Built-in ones always apply; turn yours on in each profile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs)
                )
            }
            if (prompts != null) {
                item(key = "custom") {
                    if (custom.isEmpty()) {
                        Column {
                            SectionLabel("Yours")
                            Text(
                                text = "None yet. Write one for a habit cleanup should always follow.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.l)
                            )
                        }
                    } else {
                        SegmentedGroup(
                            title = "Yours",
                            items = custom.map { prompt -> { shapes -> PromptItem(prompt, shapes) { editingId = prompt.id } } }
                        )
                    }
                }
                if (builtIn.isNotEmpty()) {
                    item(key = "builtin") {
                        SegmentedGroup(
                            title = "Built in",
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
        Text(prompt.title.ifBlank { "Untitled" })
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
            Text(if (initial.id == 0L) "New instruction" else "Edit instruction", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                label = { Text("Instruction") },
                placeholder = { Text("Always write numbers as digits.") },
                minLines = 5,
                maxLines = 12
            )
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
                    onClick = { onSave(initial.copy(title = title.trim(), prompt = text.trim())) },
                    enabled = title.isNotBlank() && text.isNotBlank(),
                    modifier = Modifier.padding(start = Spacing.s)
                ) { Text("Save") }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        DestructiveDialog(
            icon = SasayakiIcons.Delete,
            title = "Delete ${initial.title.ifBlank { "this instruction" }}?",
            text = "Profiles that use it stop applying it.",
            confirmLabel = "Delete",
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
                "Built in. Always applied when cleanup is on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(prompt.prompt, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
