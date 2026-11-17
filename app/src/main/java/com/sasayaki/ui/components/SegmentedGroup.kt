package com.sasayaki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.Spacing

typealias SegmentedItem = @Composable (ListItemShapes) -> Unit

@Composable
fun SegmentedGroup(items: List<SegmentedItem>, modifier: Modifier = Modifier, title: String? = null) {
    Column(modifier = modifier) {
        if (title != null) SectionLabel(title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            items.forEachIndexed { index, item ->
                item(ListItemDefaults.segmentedShapes(index = index, count = items.size))
            }
        }
    }
}

@Composable
fun SegmentedGroup(vararg items: SegmentedItem, modifier: Modifier = Modifier, title: String? = null) =
    SegmentedGroup(items.toList(), modifier, title)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.s)
            .semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * segmentedItemColors puts rows on surfaceBright. Checked switch rows keep that color
 * unless [highlightSelection]: a toggle being on is not the row being selected.
 */
@Composable
fun segmentedItemColors(highlightSelection: Boolean = false): ListItemColors {
    val colors = MaterialTheme.colorScheme
    return if (highlightSelection) {
        ListItemDefaults.segmentedColors(containerColor = colors.surfaceBright)
    } else {
        ListItemDefaults.segmentedColors(
            containerColor = colors.surfaceBright,
            selectedContainerColor = colors.surfaceBright,
            selectedContentColor = colors.onSurface,
            selectedLeadingContentColor = colors.onSurfaceVariant,
            selectedTrailingContentColor = colors.onSurfaceVariant,
            selectedSupportingContentColor = colors.onSurfaceVariant
        )
    }
}

@Composable
fun NavigationItem(
    shapes: ListItemShapes,
    headline: String,
    onClick: () -> Unit,
    supporting: String? = null,
    icon: ImageVector? = null,
    trailingText: String? = null,
    enabled: Boolean = true,
    needsAttention: Boolean = false
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        enabled = enabled,
        colors = segmentedItemColors(),
        leadingContent = icon?.let { { ItemIcon(it) } },
        supportingContent = supporting?.let {
            { Text(it, color = if (needsAttention) MaterialTheme.colorScheme.error else Color.Unspecified) }
        },
        trailingContent = {
            if (trailingText != null) {
                Text(trailingText, style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(SasayakiIcons.ChevronRight, contentDescription = null)
            }
        }
    ) {
        Text(headline)
    }
}

/** ExternalItem is a row that leaves the app, to system settings or a web page. */
@Composable
fun ExternalItem(shapes: ListItemShapes, headline: String, onClick: () -> Unit, supporting: String? = null, icon: ImageVector? = null) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = segmentedItemColors(),
        leadingContent = icon?.let { { ItemIcon(it) } },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Icon(SasayakiIcons.OpenInNew, contentDescription = null) }
    ) {
        Text(headline)
    }
}

@Composable
fun SwitchItem(
    shapes: ListItemShapes,
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = shapes,
        enabled = enabled,
        colors = segmentedItemColors(),
        leadingContent = icon?.let { { ItemIcon(it) } },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    ) {
        Text(headline)
    }
}

@Composable
fun ItemIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(containerColor)
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
    }
}
