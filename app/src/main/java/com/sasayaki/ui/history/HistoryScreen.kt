package com.sasayaki.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.data.db.entity.DictationSummary
import com.sasayaki.domain.model.DictationStatus
import com.sasayaki.ui.common.copyText
import com.sasayaki.ui.common.displaySourceApp
import com.sasayaki.ui.components.DestructiveDialog
import com.sasayaki.ui.components.EmptyState
import com.sasayaki.ui.components.LargeTitleBar
import com.sasayaki.ui.components.Pill
import com.sasayaki.ui.components.SasayakiScaffold
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    HistoryScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::onQueryChange,
        onCopy = { dictation ->
            if (context.copyText(dictation.text)) {
                scope.launch { snackbarHostState.showSnackbar("Copied") }
            }
        },
        onRetry = viewModel::retry,
        onDelete = { id ->
            viewModel.requestDelete(id)
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                var undone = false
                try {
                    undone = snackbarHostState.showSnackbar(
                        message = "Dictation deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    ) == SnackbarResult.ActionPerformed
                } finally {
                    // Leaving the tab cancels the snackbar; that counts as not undoing.
                    if (undone) viewModel.undoDelete(id) else viewModel.commitDelete(id)
                }
            }
        },
        onClearAll = viewModel::clearAll,
        onEnableHistory = viewModel::enableHistory,
        loadRawText = viewModel::rawText
    )
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onCopy: (DictationSummary) -> Unit,
    onRetry: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onEnableHistory: () -> Unit,
    loadRawText: suspend (Long) -> String?
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var focusHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loaded, state.focusId) {
        if (focusHandled || !state.loaded || state.focusId == 0L) return@LaunchedEffect
        state.listIndexOf(state.focusId)?.let { listState.scrollToItem(it) }
        focusHandled = true
    }

    SasayakiScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { scrollBehavior ->
            LargeTitleBar(
                title = "History",
                scrollBehavior = scrollBehavior,
                actions = {
                    if (state.hasHistory) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(SasayakiIcons.DeleteSweep, contentDescription = "Clear history")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            if (state.loaded && !state.historyEnabled) {
                item(key = "off") { HistoryOffBanner(onEnableHistory) }
            }
            if (state.hasHistory) {
                item(key = "search") { SearchField(state.query, onQueryChange) }
            }
            when {
                !state.loaded -> Unit

                !state.hasHistory -> item(key = "empty") {
                    EmptyState(
                        icon = SasayakiIcons.History,
                        title = "No dictations yet",
                        body = if (state.historyEnabled) {
                            "Everything you dictate lands here, ready to copy or retry."
                        } else {
                            "Turn history on to keep your dictations on this device."
                        }
                    )
                }

                state.groups.isEmpty() -> item(key = "no-match") {
                    EmptyState(
                        icon = SasayakiIcons.Search,
                        title = "No matches",
                        body = "Nothing you dictated contains “${state.query.trim()}”."
                    )
                }

                else -> dayGroups(state, onCopy, onRetry, onDelete, loadRawText)
            }
        }
    }

    if (confirmClear) {
        DestructiveDialog(
            icon = SasayakiIcons.DeleteSweep,
            title = "Clear all history?",
            text = "Saved text and retry audio are erased from this device. Your word counts are kept.",
            confirmLabel = "Clear",
            onConfirm = onClearAll,
            onDismiss = { confirmClear = false }
        )
    }
}

// listIndexOf mirrors the items HistoryScreen emits: the off banner, the search field, then a
// header and the rows of each day.
internal fun HistoryUiState.listIndexOf(id: Long): Int? {
    var index = (if (loaded && !historyEnabled) 1 else 0) + (if (hasHistory) 1 else 0)
    groups.forEach { group ->
        index += 1
        val position = group.dictations.indexOfFirst { it.id == id }
        if (position >= 0) return index + position
        index += group.dictations.size
    }
    return null
}

private fun LazyListScope.dayGroups(
    state: HistoryUiState,
    onCopy: (DictationSummary) -> Unit,
    onRetry: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    loadRawText: suspend (Long) -> String?
) {
    state.groups.forEach { group ->
        stickyHeader(key = "day-${group.key}", contentType = "day") {
            DayHeader(group)
        }
        itemsIndexed(group.dictations, key = { _, it -> it.id }, contentType = { _, _ -> "dictation" }) { index, dictation ->
            HistoryItem(
                dictation = dictation,
                shapes = ListItemDefaults.segmentedShapes(index, group.dictations.size),
                retrying = dictation.id in state.retryingIds,
                initiallyExpanded = dictation.id == state.focusId,
                onCopy = { onCopy(dictation) },
                onRetry = { onRetry(dictation.id) },
                onDelete = { onDelete(dictation.id) },
                loadRawText = { loadRawText(dictation.id) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun DayHeader(group: DayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = Spacing.l, end = Spacing.xs, top = Spacing.l, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(group.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            "${group.totalWords} words",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.xs),
        placeholder = { Text("Search dictations") },
        leadingIcon = { Icon(SasayakiIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(SasayakiIcons.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun HistoryOffBanner(onEnable: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.m),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Icon(SasayakiIcons.Lock, contentDescription = null)
            Text(
                "History is off. New dictations are not saved.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onEnable) { Text("Turn on") }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private const val COLLAPSED_LINES = 4

@Composable
private fun HistoryItem(
    dictation: DictationSummary,
    shapes: ListItemShapes,
    retrying: Boolean,
    initiallyExpanded: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    loadRawText: suspend () -> String?,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(dictation.id) { mutableStateOf(initiallyExpanded) }
    var rawText by remember(dictation.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(expanded) {
        if (expanded && rawText == null) rawText = loadRawText()
    }

    val failed = dictation.status == DictationStatus.FAILURE.name
    val time = remember(dictation.timestamp) {
        timeFormatter.format(Instant.ofEpochMilli(dictation.timestamp).atZone(ZoneId.systemDefault()))
    }
    val meta = listOfNotNull(
        time,
        displaySourceApp(dictation.sourceApp, dictation.sourceAppPackage),
        if (failed) null else "${dictation.wordCount} words"
    ).joinToString(" · ")
    val original = rawText?.takeIf { !failed && it.isNotBlank() && it != dictation.text }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shapes.shape,
        color = MaterialTheme.colorScheme.surfaceBright
    ) {
        Column(
            modifier = Modifier
                .clickable(onClickLabel = if (expanded) "Show less" else "Show more") { expanded = !expanded }
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                .padding(start = Spacing.l, top = Spacing.m, end = Spacing.xs, bottom = Spacing.xs)
        ) {
            Text(
                text = if (failed) dictation.errorMessage ?: "Transcription failed" else dictation.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Spacing.m)
            )
            if (expanded && original != null) {
                Column(
                    modifier = Modifier
                        .padding(top = Spacing.m, end = Spacing.m)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text("What you said", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(original, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (failed) {
                    Pill(
                        text = "Failed",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(end = Spacing.s)
                    )
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                HistoryActions(
                    canCopy = !failed && dictation.text.isNotBlank(),
                    canRetry = !dictation.audioPath.isNullOrBlank(),
                    retrying = retrying,
                    emphasizeRetry = failed,
                    onCopy = onCopy,
                    onRetry = onRetry,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun HistoryActions(
    canCopy: Boolean,
    canRetry: Boolean,
    retrying: Boolean,
    emphasizeRetry: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    if (canCopy) {
        IconButton(onClick = onCopy) {
            Icon(SasayakiIcons.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
        }
    }
    when {
        retrying -> Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator(modifier = Modifier.size(28.dp))
        }

        canRetry && emphasizeRetry -> FilledTonalIconButton(onClick = onRetry) {
            Icon(SasayakiIcons.Refresh, contentDescription = "Retry", modifier = Modifier.size(20.dp))
        }

        canRetry -> IconButton(onClick = onRetry) {
            Icon(SasayakiIcons.Refresh, contentDescription = "Retry", modifier = Modifier.size(20.dp))
        }
    }
    IconButton(onClick = onDelete) {
        Icon(SasayakiIcons.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp))
    }
}
