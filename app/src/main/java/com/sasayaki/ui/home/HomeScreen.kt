package com.sasayaki.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.data.db.entity.DictationStats
import com.sasayaki.data.db.entity.DictationSummary
import com.sasayaki.domain.model.DictationStatus
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.languageName
import com.sasayaki.ui.common.BUBBLE_KEYS
import com.sasayaki.ui.common.SetupItem
import com.sasayaki.ui.common.copyText
import com.sasayaki.ui.common.displaySourceApp
import com.sasayaki.ui.common.formatCompactNumber
import com.sasayaki.ui.common.formatDuration
import com.sasayaki.ui.common.minutesSaved
import com.sasayaki.ui.common.rememberPermissionItems
import com.sasayaki.ui.common.rememberRestrictedSettingsLikely
import com.sasayaki.ui.common.rememberVoiceInputEnabled
import com.sasayaki.ui.common.wordsPerMinute
import com.sasayaki.ui.components.EmptyState
import com.sasayaki.ui.components.SectionLabel
import com.sasayaki.ui.components.SegmentedGroup
import com.sasayaki.ui.components.SetupGroup
import com.sasayaki.ui.components.segmentedItemColors
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.SasayakiTheme
import com.sasayaki.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(
    onOpenHistory: () -> Unit,
    onOpenDictation: (Long) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenProviders: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val providerItem = SetupItem(
        key = "provider",
        title = "Speech to text",
        description = "Add the server that turns your voice into text.",
        icon = SasayakiIcons.Dns,
        done = state.speechProviderConfigured,
        required = true,
        actionLabel = "Add",
        onAction = onOpenProviders
    )
    val voiceInputEnabled = rememberVoiceInputEnabled()
    val steps = listOf(providerItem) + rememberPermissionItems(bubbleNeeded = !voiceInputEnabled || state.serviceRunning)
    val restricted = rememberRestrictedSettingsLikely()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    HomeScreen(
        state = state,
        setupSteps = steps,
        voiceInputEnabled = voiceInputEnabled,
        restrictedSettingsLikely = restricted,
        snackbarHostState = snackbarHostState,
        onStartService = viewModel::startService,
        onStopService = viewModel::stopService,
        onSelectProfile = viewModel::activateProfile,
        onOpenProfiles = onOpenProfiles,
        onOpenHistory = onOpenHistory,
        onOpenDictation = onOpenDictation,
        onCopy = { dictation ->
            if (context.copyText(dictation.text)) {
                scope.launch { snackbarHostState.showSnackbar("Copied") }
            }
        }
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    setupSteps: List<SetupItem>,
    voiceInputEnabled: Boolean,
    restrictedSettingsLikely: Boolean,
    snackbarHostState: SnackbarHostState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSelectProfile: (Long) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDictation: (Long) -> Unit,
    onCopy: (DictationSummary) -> Unit
) {
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    val ready = state.loaded && setupSteps.none { it.required && !it.done }
    val keyboardOnly = voiceInputEnabled && !state.serviceRunning
    // Someone using only the keyboard mic key is not nagged about permissions the bubble needs.
    val pendingSteps = setupSteps.filterNot { it.done || (keyboardOnly && it.key in BUBBLE_KEYS) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = padding.calculateTopPadding() + Spacing.s,
                bottom = padding.calculateBottomPadding() + Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            item(key = "header") {
                HomeHeader(
                    activeProfile = state.activeProfile,
                    onOpenProfiles = { showProfiles = true }
                )
            }
            item(key = "hero") {
                StatusHero(
                    loaded = state.loaded,
                    ready = ready,
                    running = state.serviceRunning,
                    keyboardOnly = keyboardOnly,
                    bubbleBlocker = setupSteps.firstOrNull { it.key in BUBBLE_KEYS && !it.done },
                    setupSteps = setupSteps,
                    onStart = onStartService,
                    onStop = onStopService
                )
            }
            if (state.loaded && pendingSteps.isNotEmpty()) {
                item(key = "setup") {
                    SetupGroup(items = pendingSteps, restrictedSettingsLikely = restrictedSettingsLikely, title = "Setup")
                }
            }
            if (state.lifetime.count > 0 || state.today.count > 0) {
                item(key = "today") {
                    TodaySection(today = state.today, lifetime = state.lifetime)
                }
            }
            item(key = "recent") {
                RecentSection(
                    recent = state.recent,
                    historyEnabled = state.historyEnabled,
                    onOpenHistory = onOpenHistory,
                    onOpenDictation = onOpenDictation,
                    onCopy = onCopy
                )
            }
        }
    }

    if (showProfiles) {
        ProfileSwitcherSheet(
            profiles = state.profiles,
            onSelect = { id ->
                onSelectProfile(id)
                showProfiles = false
            },
            onManage = {
                showProfiles = false
                onOpenProfiles()
            },
            onDismiss = { showProfiles = false }
        )
    }
}

@Composable
private fun HomeHeader(activeProfile: Profile?, onOpenProfiles: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Sasayaki",
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.xs)
        )
        if (activeProfile != null) {
            AssistChip(
                onClick = onOpenProfiles,
                label = { Text(activeProfile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(SasayakiIcons.Tune, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
                trailingIcon = {
                    Icon(SasayakiIcons.ExpandMore, contentDescription = "Switch profile", Modifier.size(AssistChipDefaults.IconSize))
                },
                shape = MaterialTheme.shapes.extraLarge
            )
        }
    }
}

private enum class HeroMode { LOADING, SETUP, OFF, KEYBOARD, ON }

@Composable
private fun StatusHero(
    loaded: Boolean,
    ready: Boolean,
    running: Boolean,
    keyboardOnly: Boolean,
    bubbleBlocker: SetupItem?,
    setupSteps: List<SetupItem>,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val mode = when {
        !loaded -> HeroMode.LOADING
        running -> HeroMode.ON
        !ready -> HeroMode.SETUP
        keyboardOnly -> HeroMode.KEYBOARD
        else -> HeroMode.OFF
    }
    val active = mode == HeroMode.ON || mode == HeroMode.KEYBOARD
    val containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceBright
    val contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = containerColor,
        contentColor = contentColor
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(fadeInSpec) togetherWith fadeOut(fadeOutSpec) },
            label = "hero"
        ) { heroMode ->
            Column(
                modifier = Modifier.padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BubbleOrb(active = heroMode == HeroMode.ON)
                    if (heroMode == HeroMode.ON || heroMode == HeroMode.OFF || heroMode == HeroMode.KEYBOARD) {
                        BubbleSwitch(
                            checked = heroMode == HeroMode.ON,
                            // A keyboard-only setup may lack what the bubble needs, so that comes first.
                            onCheckedChange = { if (!it) onStop() else bubbleBlocker?.onAction?.invoke() ?: onStart() }
                        )
                    }
                }
                when (heroMode) {
                    HeroMode.LOADING -> HeroText("Sasayaki", "Getting things ready…")

                    HeroMode.ON -> HeroText("Speak, don’t type", "Tap the bubble in any text field.")

                    HeroMode.OFF -> HeroText("The bubble is off", "Turn it on to dictate into any app.")

                    HeroMode.KEYBOARD -> HeroText(
                        "Ready in your keyboard",
                        "Tap your keyboard's mic key to dictate. Turn on the bubble if you want it too."
                    )

                    HeroMode.SETUP -> {
                        val required = setupSteps.filter(SetupItem::required)
                        val done = required.count(SetupItem::done)
                        HeroText("Almost there", "Finish the steps below and you can dictate anywhere.")
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            LinearWavyProgressIndicator(
                                progress = { done / required.size.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "$done of ${required.size} done",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroText(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.78f))
    }
}

@Composable
private fun BubbleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { contentDescription = "Dictation bubble" },
        thumbContent = {
            Icon(
                imageVector = if (checked) SasayakiIcons.Check else SasayakiIcons.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    )
}

@Composable
private fun BubbleOrb(active: Boolean) {
    val rotation = if (active) {
        val transition = rememberInfiniteTransition(label = "orb")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
            label = "orbRotation"
        ).value
    } else {
        0f
    }
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(shape)
                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
        )
        Icon(
            imageVector = SasayakiIcons.Mic,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TodaySection(today: DictationStats, lifetime: DictationStats) {
    Column {
        SectionLabel("Today")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatTile(
                value = formatCompactNumber(today.wordCount),
                label = "words",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = "${minutesSaved(today.wordCount, today.durationMs)} min",
                label = "saved vs typing",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = wordsPerMinute(today.wordCount, today.durationMs)?.toString() ?: "–",
                label = "words / min",
                modifier = Modifier.weight(1f)
            )
        }
        if (lifetime.count > 0) {
            Text(
                text = "All time · ${formatCompactNumber(lifetime.wordCount)} words · ${formatDuration(lifetime.durationMs)} spoken",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.l, top = Spacing.s)
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceBright
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RecentSection(
    recent: List<DictationSummary>,
    historyEnabled: Boolean,
    onOpenHistory: () -> Unit,
    onOpenDictation: (Long) -> Unit,
    onCopy: (DictationSummary) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionLabel("Recent", modifier = Modifier.padding(top = Spacing.s))
            if (recent.isNotEmpty()) {
                TextButton(onClick = onOpenHistory) { Text("See all") }
            }
        }
        if (recent.isEmpty()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceBright) {
                EmptyState(
                    icon = if (historyEnabled) SasayakiIcons.GraphicEq else SasayakiIcons.Lock,
                    title = if (historyEnabled) "Nothing yet" else "History is off",
                    body = if (historyEnabled) {
                        "Your dictations will show up here, ready to copy again."
                    } else {
                        "Dictations are not kept on this device. Turn history on in Settings."
                    }
                )
            }
        } else {
            SegmentedGroup(
                items = recent.map { dictation ->
                    { shapes -> RecentItem(dictation, shapes, { onOpenDictation(dictation.id) }, onCopy) }
                }
            )
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
private fun RecentItem(dictation: DictationSummary, shapes: ListItemShapes, onOpen: () -> Unit, onCopy: (DictationSummary) -> Unit) {
    val failed = dictation.status == DictationStatus.FAILURE.name
    val time = remember(dictation.timestamp) {
        timeFormatter.format(Instant.ofEpochMilli(dictation.timestamp).atZone(ZoneId.systemDefault()))
    }
    val meta = listOfNotNull(time, displaySourceApp(dictation.sourceApp, dictation.sourceAppPackage)).joinToString(" · ")

    SegmentedListItem(
        onClick = onOpen,
        shapes = shapes,
        colors = segmentedItemColors(),
        supportingContent = {
            Text(
                text = if (failed) "Failed · $meta" else meta,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = if (failed || dictation.text.isBlank()) {
            null
        } else {
            {
                IconButton(onClick = { onCopy(dictation) }) {
                    Icon(SasayakiIcons.ContentCopy, contentDescription = "Copy")
                }
            }
        }
    ) {
        Text(
            text = if (failed) dictation.errorMessage ?: "Transcription failed" else dictation.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileSwitcherSheet(profiles: List<Profile>, onSelect: (Long) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Text("Active profile", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Spacing.s))
            SegmentedGroup(
                items = profiles.map { profile ->
                    { shapes ->
                        SegmentedListItem(
                            selected = profile.isActive,
                            onClick = { onSelect(profile.id) },
                            shapes = shapes,
                            colors = segmentedItemColors(highlightSelection = true),
                            leadingContent = { RadioButton(selected = profile.isActive, onClick = null) },
                            supportingContent = { Text(profileSummary(profile), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        ) {
                            Text(profile.name)
                        }
                    }
                }
            )
            TextButton(onClick = onManage, modifier = Modifier.align(Alignment.End)) {
                Text("Manage profiles")
            }
        }
    }
}

private fun profileSummary(profile: Profile): String = listOfNotNull(
    profile.language?.let { languageName(it) } ?: "Any language",
    "Cleanup".takeIf { profile.llmEnabled }
).joinToString(" · ")

@Preview
@Composable
private fun HomeScreenPreview() {
    SasayakiTheme {
        HomeScreen(
            state = HomeUiState(
                loaded = true,
                serviceRunning = true,
                speechProviderConfigured = true,
                profiles = listOf(Profile(id = 1, name = "Chat", isActive = true)),
                today = DictationStats(count = 12, wordCount = 640, durationMs = 240_000),
                lifetime = DictationStats(count = 340, wordCount = 18_200, durationMs = 7_800_000),
                recent = listOf(
                    DictationSummary(
                        id = 1,
                        text = "Let's meet at nine tomorrow at the usual place.",
                        wordCount = 9,
                        timestamp = 0,
                        sourceApp = "Signal",
                        sourceAppPackage = null,
                        durationMs = 4000,
                        status = "SUCCESS",
                        errorMessage = null,
                        profileId = 1,
                        audioPath = null
                    )
                )
            ),
            setupSteps = emptyList(),
            voiceInputEnabled = false,
            restrictedSettingsLikely = false,
            snackbarHostState = SnackbarHostState(),
            onStartService = {},
            onStopService = {},
            onSelectProfile = {},
            onOpenProfiles = {},
            onOpenHistory = {},
            onOpenDictation = {},
            onCopy = {}
        )
    }
}
