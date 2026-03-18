package com.sasayaki.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.data.db.entity.Dictation
import com.sasayaki.ui.common.EmptyStateCard
import com.sasayaki.ui.common.SasayakiScaffold
import com.sasayaki.ui.common.SasayakiTopBar
import com.sasayaki.ui.common.StatusPill
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val dayGroups by viewModel.dayGroups.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SasayakiScaffold(
        topBar = {
            SasayakiTopBar(
                title = "History",
                subtitle = "Review, copy, or clear previous dictations stored on this device.",
                onBack = onBack
            )
        }
    ) { padding ->
        if (dayGroups.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(historyContentPadding(padding)),
                verticalArrangement = Arrangement.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Default.History,
                    title = "No dictations yet",
                    description = "When history is enabled, your recent transcripts will appear here for quick reuse."
                )
            }
        } else {
            LazyColumn(
                contentPadding = historyContentPadding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                dayGroups.forEach { group ->
                    item(key = "header_${group.key}", contentType = "header") {
                        DayHeader(group = group)
                    }

                    items(group.dictations, key = { it.id }, contentType = { "card" }) { dictation ->
                        HistoryCard(
                            dictation = dictation,
                            onCopy = { copyToClipboard(context, dictation.text) },
                            onDelete = { viewModel.delete(dictation.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(group: DayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.date,
            style = MaterialTheme.typography.titleLarge
        )
        StatusPill(
            label = "${group.totalWords} words",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun HistoryCard(
    dictation: Dictation,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = dictation.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(label = formatTime(dictation.timestamp))
                        StatusPill(label = "${dictation.wordCount} words")
                        dictation.sourceApp?.takeIf(String::isNotBlank)?.let { sourceApp ->
                            StatusPill(
                                label = displaySourceApp(sourceApp),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy dictation"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete dictation",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (expanded && dictation.rawText != dictation.text) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Raw transcript",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dictation.rawText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("dictation", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val defaultZoneId = ZoneId.systemDefault()

private fun formatTime(timestamp: Long): String {
    return timeFormat.format(Instant.ofEpochMilli(timestamp).atZone(defaultZoneId))
}

private fun displaySourceApp(sourceApp: String): String {
    val compactName = sourceApp.substringAfterLast('.')
    return compactName.take(20)
}

private fun historyContentPadding(padding: PaddingValues): PaddingValues {
    return PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = padding.calculateTopPadding() + 12.dp,
        bottom = padding.calculateBottomPadding() + 24.dp
    )
}
