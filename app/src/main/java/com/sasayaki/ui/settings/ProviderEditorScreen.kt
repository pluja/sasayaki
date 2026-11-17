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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.R
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
    val intro = stringResource(R.string.provider_intro)

    SettingsPage(
        title = if (isNew) stringResource(R.string.provider_add) else form.name.ifBlank { stringResource(R.string.provider_untitled) },
        onBack = actions.onBack,
        itemSpacing = Spacing.l,
        actions = {
            if (!isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(SasayakiIcons.Delete, contentDescription = stringResource(R.string.provider_delete))
                }
            }
        }
    ) {
        if (isNew) {
            pageIntro(intro)
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
                    Text(stringResource(if (state.models.isEmpty()) R.string.provider_test else R.string.provider_test_again))
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
                    Text(stringResource(R.string.provider_add))
                }
            }
        }
    }

    if (confirmDelete) {
        DestructiveDialog(
            icon = SasayakiIcons.Delete,
            title = if (form.name.isBlank()) {
                stringResource(R.string.provider_delete_unnamed_title)
            } else {
                stringResource(R.string.provider_delete_named_title, form.name)
            },
            text = stringResource(R.string.provider_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
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
                label = { Text(stringResource(R.string.provider_server_address)) },
                placeholder = { Text("https://api.example.com") },
                singleLine = true,
                isError = state.urlError != null,
                supportingText = {
                    when {
                        state.urlError != null -> Text(stringResource(state.urlError))

                        state.unencrypted -> Text(
                            stringResource(R.string.provider_unencrypted),
                            color = MaterialTheme.colorScheme.tertiary
                        )

                        else -> Text(stringResource(R.string.provider_server_address_hint))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false)
            )
            OutlinedTextField(
                value = form.apiKey,
                onValueChange = actions.onApiKeyChange,
                enabled = state.loaded,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.provider_api_key)) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.provider_api_key_hint)) },
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
                            contentDescription = stringResource(if (revealKey) R.string.provider_hide_key else R.string.provider_show_key)
                        )
                    }
                }
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = actions.onNameChange,
                enabled = state.loaded,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.provider_name)) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.provider_name_hint)) },
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
        title = stringResource(R.string.provider_default_models),
        items = listOf(
            { shapes ->
                NavigationItem(
                    shapes = shapes,
                    headline = stringResource(R.string.provider_speech_to_text),
                    supporting = form.speechModel ?: stringResource(R.string.provider_not_used),
                    icon = SasayakiIcons.Mic,
                    onClick = { picking = Purpose.SPEECH }
                )
            },
            { shapes ->
                NavigationItem(
                    shapes = shapes,
                    headline = stringResource(R.string.provider_cleanup),
                    supporting = form.cleanupModel ?: stringResource(R.string.provider_not_used),
                    icon = SasayakiIcons.AutoAwesome,
                    onClick = { picking = Purpose.CLEANUP }
                )
            }
        )
    )
    Text(
        text = stringResource(R.string.provider_default_models_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.l, top = Spacing.s)
    )

    picking?.let { purpose ->
        val speech = purpose == Purpose.SPEECH
        ModelPickerSheet(
            title = stringResource(if (speech) R.string.provider_speech_model else R.string.provider_cleanup_model),
            models = state.models.suitableFor(purpose),
            selected = if (speech) form.speechModel else form.cleanupModel,
            noneLabel = stringResource(if (speech) R.string.provider_no_speech_model else R.string.provider_no_cleanup_model),
            loading = state.loadingModels,
            note = when {
                state.models.isNotEmpty() -> null
                state.result is ConnectionResult.Connected -> stringResource(R.string.provider_models_unlisted_note)
                else -> stringResource(R.string.provider_models_untested_note)
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
                    Text(
                        stringResource(if (connected) R.string.provider_connected else R.string.provider_connection_failed),
                        style = MaterialTheme.typography.titleMedium
                    )
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
                            text = pluralStringResource(R.plurals.provider_more_models, more, more),
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun resultSummary(result: ConnectionResult): String = when (result) {
    is ConnectionResult.Failed -> result.message

    is ConnectionResult.Connected -> {
        val total = result.models.size
        val speech = result.models.count { it.kind == ModelKind.SPEECH }
        when {
            result.models.isEmpty() -> stringResource(R.string.provider_models_unlisted)
            speech == 0 -> pluralStringResource(R.plurals.provider_models_available, total, total)
            else -> pluralStringResource(R.plurals.provider_models_available_speech, total, total, speech)
        }
    }
}
