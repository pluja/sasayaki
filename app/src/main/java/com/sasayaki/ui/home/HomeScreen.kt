package com.sasayaki.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.data.db.entity.DictationSummary
import com.sasayaki.domain.model.DictationStatus
import com.sasayaki.service.BubbleService
import com.sasayaki.ui.common.EmptyStateCard
import com.sasayaki.ui.common.PermissionCard
import com.sasayaki.ui.common.PermissionStatus
import com.sasayaki.ui.common.SectionCard
import com.sasayaki.ui.common.StatusPill
import com.sasayaki.ui.common.rememberAccessibilityPermissionState
import com.sasayaki.ui.common.rememberMicrophonePermissionState
import com.sasayaki.ui.common.rememberNotificationPermissionState
import com.sasayaki.ui.common.rememberOverlayPermissionState
import com.sasayaki.ui.history.DayGroup
import com.sasayaki.ui.history.HistoryViewModel
import com.sasayaki.ui.theme.SasayakiIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    outerPadding: PaddingValues,
    viewModel: HomeViewModel = hiltViewModel(),
    historyViewModel: HistoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val todayStats by viewModel.todayStats.collectAsStateWithLifecycle()
    val totalStats by viewModel.totalStats.collectAsStateWithLifecycle()
    val dayGroups by historyViewModel.dayGroups.collectAsStateWithLifecycle()
    val retryingIds by historyViewModel.retryingIds.collectAsStateWithLifecycle()
    val serviceRunning by BubbleService.runningState.collectAsStateWithLifecycle()

    val overlayPermission = rememberOverlayPermissionState()
    val accessibilityPermission = rememberAccessibilityPermissionState()
    val microphonePermission = rememberMicrophonePermissionState()
    val notificationPermission = rememberNotificationPermissionState()
    val setupStatuses = listOf(overlayPermission, accessibilityPermission, microphonePermission, notificationPermission)
    val missingPermissions = setupStatuses.filterNot(PermissionStatus::granted)
    val serviceReady = overlayPermission.granted && accessibilityPermission.granted && microphonePermission.granted

    LazyColumn(
        contentPadding = homeContentPadding(outerPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item("summary") {
            SummaryCard(
                totalWords = totalStats.wordCount,
                totalCount = totalStats.count,
                serviceRunning = serviceRunning,
                serviceReady = serviceReady,
                onToggleService = {
                    when {
                        serviceRunning -> BubbleService.stop(context)
                        !microphonePermission.granted -> microphonePermission.onRequest()
                        !overlayPermission.granted -> overlayPermission.onRequest()
                        !accessibilityPermission.granted -> accessibilityPermission.onRequest()
                        else -> BubbleService.start(context)
                    }
                }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (!overlayPermission.granted || !accessibilityPermission.granted)
        ) {
            item("restricted") {
                RestrictedSettingsCard(
                    onOpenAppInfo = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }

        if (missingPermissions.isNotEmpty()) {
            item("setup_header") { Text("Finish setup", style = MaterialTheme.typography.titleLarge) }
            items(missingPermissions, key = { it.name }) { status -> PermissionCard(status = status) }
        }

        item("today_header") {
            Text(
                text = "Today",
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Serif
            )
        }

        if (dayGroups.isEmpty()) {
            item("empty") {
                EmptyStateCard(
                    icon = SasayakiIcons.History,
                    title = "No dictations yet",
                    description = "Recent transcripts and failures will appear here when history is enabled."
                )
            }
        } else {
            dayGroups.forEach { group ->
                item("header_${group.key}") {
                    DayHeader(group)
                }
                items(group.dictations, key = { it.id }) { dictation ->
                    TranscriptCard(
                        dictation = dictation,
                        isRetrying = dictation.id in retryingIds,
                        onCopy = { copyToClipboard(context, dictation.text) },
                        onRetry = { historyViewModel.retry(dictation.id) },
                        onDelete = { historyViewModel.removeFromHistory(dictation.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    totalWords: Int,
    totalCount: Int,
    serviceRunning: Boolean,
    serviceReady: Boolean,
    onToggleService: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${formatCompactNumber(totalWords)} words",
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.primary
            )
            Text("spoken so far", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (totalCount == 0) "Start a dictation to build your history." else "You've written $totalCount transcriptions.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(if (serviceRunning) "Bubble active" else "Bubble off")
                if (!serviceReady && !serviceRunning) StatusPill("Setup needed")
            }
            Button(onClick = onToggleService, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Icon(if (serviceRunning) SasayakiIcons.StopCircle else SasayakiIcons.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text(if (serviceRunning) "Stop dictation service" else "Start dictation service")
            }
        }
    }
}

@Composable
private fun DayHeader(group: DayGroup) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(group.date, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        StatusPill("${group.totalWords} words")
    }
}

@Composable
private fun TranscriptCard(
    dictation: DictationSummary,
    isRetrying: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val failed = dictation.status == DictationStatus.FAILURE.name

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (failed) dictation.errorMessage ?: "Transcription failed" else dictation.text,
                style = MaterialTheme.typography.titleMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(dictation.timestamp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                dictation.sourceApp?.takeIf(String::isNotBlank)?.let {
                    StatusPill(displaySourceApp(it))
                }
                if (isRetrying) {
                    StatusPill("Retrying")
                }
                if (failed) {
                    StatusPill(
                        label = "Failed",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCopy, enabled = !isRetrying && !failed && dictation.text.isNotBlank()) {
                    Icon(SasayakiIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Copy")
                }
                OutlinedButton(onClick = onRetry, enabled = !isRetrying && !dictation.audioPath.isNullOrBlank()) {
                    if (isRetrying) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(SasayakiIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(if (isRetrying) "Retrying" else "Retry")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete dictation", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this dictation?") },
            text = { Text("This removes the transcript and any saved retry audio.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RestrictedSettingsCard(onOpenAppInfo: () -> Unit) {
    SectionCard(
        title = "Restricted settings on Android 13+",
        subtitle = "Sideloaded apps need one extra step before overlay and accessibility permissions can be enabled."
    ) {
        OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) {
            Icon(SasayakiIcons.Widgets, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("Open App Info")
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

private fun formatTime(timestamp: Long): String = timeFormat.format(Instant.ofEpochMilli(timestamp).atZone(defaultZoneId))

private fun formatCompactNumber(number: Int): String {
    return when {
        number >= 1_000_000 -> "${"%.1f".format(number / 1_000_000.0)}M"
        number >= 10_000 -> "${"%.1f".format(number / 1_000.0)}K"
        else -> number.toString()
    }
}

private fun displaySourceApp(sourceApp: String): String {
    val trimmed = sourceApp.trim()
    if (trimmed.isBlank()) return "Unknown app"
    if (!trimmed.contains('.')) return trimmed.take(24)

    return when {
        trimmed.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
        trimmed.contains("signal", ignoreCase = true) -> "Signal"
        trimmed.contains("telegram", ignoreCase = true) -> "Telegram"
        trimmed.contains("gmail", ignoreCase = true) -> "Gmail"
        trimmed.contains("outlook", ignoreCase = true) -> "Outlook"
        trimmed.contains("discord", ignoreCase = true) -> "Discord"
        trimmed.contains("slack", ignoreCase = true) -> "Slack"
        else -> trimmed.substringAfterLast('.').replaceFirstChar { it.uppercase() }.take(24)
    }
}

private fun homeContentPadding(padding: PaddingValues): PaddingValues {
    return PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = padding.calculateTopPadding() + 20.dp,
        bottom = padding.calculateBottomPadding() + 96.dp
    )
}
