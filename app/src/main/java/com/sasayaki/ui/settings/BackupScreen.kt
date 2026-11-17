package com.sasayaki.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.R
import com.sasayaki.ui.components.DestructiveDialog
import com.sasayaki.ui.components.ItemIcon
import com.sasayaki.ui.components.SegmentedGroup
import com.sasayaki.ui.components.SettingsPage
import com.sasayaki.ui.components.pageIntro
import com.sasayaki.ui.components.segmentedItemColors
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.SasayakiTheme

private val importTypes = arrayOf("application/json", "text/plain", "application/octet-stream")

@Composable
fun BackupSettingsRoute(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val working by viewModel.working.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(resources.getString(it)) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    BackupScreen(
        working = working,
        snackbarHostState = snackbarHostState,
        onExport = { exportLauncher.launch(viewModel.suggestedFileName()) },
        onImport = { importLauncher.launch(importTypes) },
        onBack = onBack
    )
}

@Composable
fun BackupScreen(working: Boolean, snackbarHostState: SnackbarHostState, onExport: () -> Unit, onImport: () -> Unit, onBack: () -> Unit) {
    var confirmImport by rememberSaveable { mutableStateOf(false) }
    val intro = stringResource(R.string.backup_intro)
    SettingsPage(title = stringResource(R.string.backup_title), onBack = onBack, snackbarHostState = snackbarHostState) {
        pageIntro(intro)
        item(key = "actions") {
            SegmentedGroup(
                items = listOf(
                    { shapes ->
                        ActionItem(
                            shapes = shapes,
                            icon = SasayakiIcons.Upload,
                            headline = stringResource(R.string.backup_export),
                            supporting = stringResource(R.string.backup_export_summary),
                            enabled = !working,
                            onClick = onExport
                        )
                    },
                    { shapes ->
                        ActionItem(
                            shapes = shapes,
                            icon = SasayakiIcons.Download,
                            headline = stringResource(R.string.backup_import),
                            supporting = stringResource(R.string.backup_import_summary),
                            enabled = !working,
                            onClick = { confirmImport = true }
                        )
                    }
                )
            )
        }
        if (working) {
            item(key = "working") { LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
    }

    if (confirmImport) {
        DestructiveDialog(
            icon = SasayakiIcons.Download,
            title = stringResource(R.string.backup_import_confirm_title),
            text = stringResource(R.string.backup_import_confirm_text),
            confirmLabel = stringResource(R.string.backup_replace),
            onConfirm = onImport,
            onDismiss = { confirmImport = false }
        )
    }
}

@Composable
private fun ActionItem(
    shapes: ListItemShapes,
    icon: ImageVector,
    headline: String,
    supporting: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        enabled = enabled,
        colors = segmentedItemColors(),
        leadingContent = { ItemIcon(icon, enabled = enabled) },
        supportingContent = { Text(supporting) }
    ) {
        Text(headline)
    }
}

@Preview
@Composable
private fun BackupScreenPreview() {
    SasayakiTheme {
        BackupScreen(working = false, snackbarHostState = SnackbarHostState(), onExport = {}, onImport = {}, onBack = {})
    }
}
