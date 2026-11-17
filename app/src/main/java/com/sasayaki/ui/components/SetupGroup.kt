package com.sasayaki.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sasayaki.R
import com.sasayaki.ui.common.ACCESSIBILITY_KEY
import com.sasayaki.ui.common.SetupItem
import com.sasayaki.ui.common.openAppInfo
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.Spacing

@Composable
fun SetupGroup(items: List<SetupItem>, restrictedSettingsLikely: Boolean, modifier: Modifier = Modifier, title: String? = null) {
    val context = LocalContext.current
    val rows = buildList<SegmentedItem> {
        items.forEach { item ->
            add { shapes -> SetupRow(item, shapes) }
            if (item.key == ACCESSIBILITY_KEY && !item.done) {
                when {
                    restrictedSettingsLikely -> add { shapes -> RestrictedSettingsRow(shapes) { openAppInfo(context) } }

                    // Installs from a store or another installer can still be blocked, so they get a hint.
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> add { shapes ->
                        RestrictedSettingsHint(shapes) { openAppInfo(context) }
                    }
                }
            }
        }
    }
    SegmentedGroup(items = rows, modifier = modifier, title = title)
}

@Composable
private fun SetupRow(item: SetupItem, shapes: ListItemShapes) {
    if (item.done) {
        SegmentedListItem(
            shapes = shapes,
            colors = segmentedItemColors(),
            leadingContent = {
                ItemIcon(
                    SasayakiIcons.Check,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            },
            supportingContent = { Text(item.description) }
        ) {
            Text(item.title)
        }
        return
    }
    SegmentedListItem(
        onClick = item.onAction,
        shapes = shapes,
        colors = segmentedItemColors(),
        leadingContent = { ItemIcon(item.icon) },
        supportingContent = { Text(item.description) },
        trailingContent = {
            if (item.required) {
                FilledTonalButton(onClick = item.onAction) { Text(item.actionLabel) }
            } else {
                TextButton(onClick = item.onAction) { Text(item.actionLabel) }
            }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(item.title)
            if (!item.required) {
                Text(
                    stringResource(R.string.setup_optional),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RestrictedSettingsRow(shapes: ListItemShapes, onOpenAppInfo: () -> Unit) {
    SegmentedListItem(
        shapes = shapes,
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            supportingContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        leadingContent = {
            ItemIcon(
                SasayakiIcons.Lock,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.setup_restricted_intro))
                Text(stringResource(R.string.setup_restricted_steps))
                Button(onClick = onOpenAppInfo, contentPadding = PaddingValues(horizontal = Spacing.l)) {
                    Text(stringResource(R.string.setup_open_app_info))
                }
            }
        }
    ) {
        Text(stringResource(R.string.setup_restricted_title))
    }
}

@Composable
private fun RestrictedSettingsHint(shapes: ListItemShapes, onOpenAppInfo: () -> Unit) {
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedItemColors(),
        supportingContent = {
            Text(stringResource(R.string.setup_restricted_hint))
        },
        trailingContent = { TextButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.setup_app_info)) } }
    ) {
        Text(stringResource(R.string.setup_greyed_out), style = MaterialTheme.typography.bodyMedium)
    }
}
