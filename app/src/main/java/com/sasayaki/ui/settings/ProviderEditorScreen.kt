package com.sasayaki.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.data.repository.ConnectionResult
import com.sasayaki.domain.provider.ModelKind
import com.sasayaki.domain.provider.Purpose
import com.sasayaki.domain.provider.suitableFor
import com.sasayaki.ui.components.DestructiveDialog
import com.sasayaki.ui.components.ModelPickerSheet
import com.sasayaki.ui.components.NavigationItem
import com.sasayaki.ui.components.Pill
import com.sasayaki.ui.components.SegmentedGroup
import com.sasayaki.ui.components.SettingsPage
import com.sasayaki.ui.components.pageIntro
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.Spacing

private const val MODEL_PREVIEW_COUNT = 6

@Composable
fun ProviderEditorRoute(onDone: () -> Unit, viewModel: ProviderEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }
    ProviderEditorScreen(
        isNew = viewModel.isNew,
        form = viewModel.draft.form,
        state = state,
        actions = providerEditorActions(viewModel.draft, onAdd = viewModel::add, onDelete = viewModel::delete, onBack = onDone)
    )
}

fun providerEditorActions(draft: ProviderDraft, onAdd: () -> Unit, onDelete: () -> Unit, onBack: () -> Unit) = ProviderEditorActions(
    onNameChange = draft::onNameChange,
    onUrlChange = draft::onUrlChange,
    onApiKeyChange = draft::onApiKeyChange,
    onSpeechModel = draft::onSpeechModel,
    onCleanupModel = draft::onCleanupModel,
    onTest = draft::test,
    onAdd = onAdd,
    onDelete = onDelete,
    onBack = onBack
)

data class ProviderForm(val name: String, val url: String, val apiKey: String, val speechModel: String?, val cleanupModel: String?)

class ProviderEditorActions(
    val onNameChange: (String) -> Unit,
    val onUrlChange: (String) -> Unit,
    val onApiKeyChange: (String) -> Unit,
    val onSpeechModel: (String?) -> Unit,
    val onCleanupModel: (String?) -> Unit,
    val onTest: () -> Unit,
    val onAdd: () -> Unit,
    val onDelete: () -> Unit,
    val onBack: () -> Unit
)

@Composable
fun ProviderEditorScreen(isNew: Boolean, form: ProviderForm, state: ProviderEditorUiState, actions: ProviderEditorActions) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    SettingsPage(
        title = if (isNew) "Add provider" else form.name.ifBlank { "Provider" },
        onBack = actions.onBack,
        itemSpacing = Spacing.l,
        actions = {
            if (!isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(SasayakiIcons.Delete, contentDescription = "Delete provider")
                }
            }
        }
    ) {
        if (isNew) {
            pageIntro("Any OpenAI-compatible server works: your own (speaches, whisper.cpp, llama.cpp) or a hosted one.")
        }
        item(key = "connection") {
            ProviderConnectionForm(form, state, actions)
        }
        item(key = "test") {
            Button(
                onClick = actions.onTest,
                enabled = state.loaded && !state.testing && form.url.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ButtonDefaults.MediumContainerHeight)
            ) {
                if (state.testing) {
                    LoadingIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (state.models.isEmpty()) "Test connection" else "Test again")
                }
            }
        }
        item(key = "result") {
            AnimatedVisibility(
                visible = state.result != null,
                enter =
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit =
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                state.result?.let { ConnectionResultCard(it) }
            }
        }
        item(key = "defaults") {
            DefaultModels(form, state, actions)
        }
        if (isNew) {
            item(key = "add") {
                FilledTonalButton(
                    onClick = actions.onAdd,
                    enabled = form.url.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ButtonDefaults.MediumContainerHeight)
                ) {
                    Text("Add provider")
                }
            }
        }
    }

    if (confirmDelete) {
        DestructiveDialog(
            icon = SasayakiIcons.Delete,
            title = "Delete ${form.name.ifBlank { "this provider" }}?",
            text = "Profiles that use it switch to another provider. The API key is erased from this device.",
            confirmLabel = "Delete",
            onConfirm = actions.onDelete,
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
internal fun ProviderConnectionForm(form: ProviderForm, state: ProviderEditorUiState, actions: ProviderEditorActions) {
    var revealKey by rememberSaveable { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
        Column(modifier = Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinedTextField(
                value = form.url,
                onValueChange = actions.onUrlChange,
                enabled = state.loaded,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Server address") },
                placeholder = { Text("https://api.example.com") },
                singleLine = true,
                isError = state.urlError != null,
                supportingText = { Text(state.urlError ?: "Starts with https://. A trailing /v1 is fine.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false)
            )
            OutlinedTextField(
                value = form.apiKey,
                onValueChange = actions.onApiKeyChange,
                enabled = state.loaded,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                singleLine = true,
                supportingText = { Text("Leave empty if your server doesn't use one.") },
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false
                ),
                trailingIcon = {
                    IconButton(onClick = { revealKey = !revealKey }) {
                        Icon(
                            imageVector = if (revealKey) SasayakiIcons.VisibilityOff else SasayakiIcons.Visibility,
                            contentDescription = if (revealKey) "Hide key" else "Show key"
                        )
                    }
                }
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = actions.onNameChange,
                enabled = state.loaded,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
                supportingText = { Text("How it appears in profiles.") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onTest() })
            )
        }
    }
}

@Composable
internal fun DefaultModels(form: ProviderForm, state: ProviderEditorUiState, actions: ProviderEditorActions) {
    var picking by rememberSaveable { mutableStateOf<Purpose?>(null) }
    SegmentedGroup(
        title = "Default models",
        items = listOf(
            { shapes ->
                NavigationItem(
                    shapes = shapes,
                    headline = "Speech to text",
                    supporting = form.speechModel ?: "Not used",
                    icon = SasayakiIcons.Mic,
                    onClick = { picking = Purpose.SPEECH }
                )
            },
            { shapes ->
                NavigationItem(
                    shapes = shapes,
                    headline = "Cleanup",
                    supporting = form.cleanupModel ?: "Not used",
                    icon = SasayakiIcons.AutoAwesome,
                    onClick = { picking = Purpose.CLEANUP }
                )
            }
        )
    )
    Text(
        text = "Profiles use these unless you pick other models for a profile.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.l, top = Spacing.s)
    )

    picking?.let { purpose ->
        val speech = purpose == Purpose.SPEECH
        ModelPickerSheet(
            title = if (speech) "Speech to text model" else "Cleanup model",
            models = state.models.suitableFor(purpose),
            selected = if (speech) form.speechModel else form.cleanupModel,
            noneLabel = if (speech) "Don't use for speech to text" else "Don't use for cleanup",
            loading = state.loadingModels,
            note = when {
                state.models.isNotEmpty() -> null
                state.result is ConnectionResult.Connected -> "This server doesn't list its models. Type the model name below."
                else -> "Test the connection to load this server's models, or type a name below."
            },
            onSelect = { model ->
                if (speech) actions.onSpeechModel(model) else actions.onCleanupModel(model)
                picking = null
            },
            onDismiss = { picking = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConnectionResultCard(result: ConnectionResult) {
    val connected = result is ConnectionResult.Connected
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(modifier = Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (connected) SasayakiIcons.CheckCircle else SasayakiIcons.Error, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(if (connected) "Connected" else "Couldn't connect", style = MaterialTheme.typography.titleMedium)
                    Text(resultSummary(result), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (result is ConnectionResult.Connected && result.models.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    result.models.take(MODEL_PREVIEW_COUNT).forEach { model ->
                        Pill(
                            text = model.id,
                            icon = if (model.kind == ModelKind.SPEECH) SasayakiIcons.Mic else null,
                            containerColor = MaterialTheme.colorScheme.surfaceBright,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val more = result.models.size - MODEL_PREVIEW_COUNT
                    if (more >
                        0
                    ) {
                        Pill(
                            text = "+$more more",
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

private fun resultSummary(result: ConnectionResult): String = when (result) {
    is ConnectionResult.Failed -> result.message

    is ConnectionResult.Connected -> {
        val speech = result.models.count { it.kind == ModelKind.SPEECH }
        when {
            result.models.isEmpty() -> "The server answered but doesn't list its models."
            speech == 0 -> "${result.models.size} models available."
            else -> "${result.models.size} models available, $speech for speech to text."
        }
    }
}
