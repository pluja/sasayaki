package com.sasayaki.ui.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.R
import com.sasayaki.data.preferences.BUBBLE_SIZES
import com.sasayaki.data.preferences.BubbleSize
import com.sasayaki.data.preferences.CONTROLS_SIZES
import com.sasayaki.data.preferences.ThemeMode
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.service.bubble.BubbleAppearance
import com.sasayaki.service.bubble.BubbleScales
import com.sasayaki.ui.common.rememberPermissionItems
import com.sasayaki.ui.common.rememberRestrictedSettingsLikely
import com.sasayaki.ui.common.rememberVoiceInputEnabled
import com.sasayaki.ui.components.ConnectedChoice
import com.sasayaki.ui.components.DestructiveDialog
import com.sasayaki.ui.components.ItemIcon
import com.sasayaki.ui.components.NavigationItem
import com.sasayaki.ui.components.SegmentedGroup
import com.sasayaki.ui.components.SegmentedItem
import com.sasayaki.ui.components.SettingsPage
import com.sasayaki.ui.components.SetupGroup
import com.sasayaki.ui.components.SwitchItem
import com.sasayaki.ui.components.pageIntro
import com.sasayaki.ui.components.segmentedItemColors
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.Spacing

private val retentionOptions = listOf(50, 100, 500, 1000, 5000)

@Composable
fun RecordingSettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecordingSettingsScreen(
        loaded = state.loaded,
        prefs = state.preferences,
        onSilenceThreshold = viewModel::setSilenceThreshold,
        onPauseOtherAudio = viewModel::setPauseOtherAudio,
        onKeepScreenOnWhileRecording = viewModel::setKeepScreenOnWhileRecording,
        onVibrateOnRecord = viewModel::setVibrateOnRecord,
        onBack = onBack
    )
}

@Composable
fun RecordingSettingsScreen(
    loaded: Boolean,
    prefs: UserPreferences,
    onSilenceThreshold: (Long) -> Unit,
    onPauseOtherAudio: (Boolean) -> Unit,
    onKeepScreenOnWhileRecording: (Boolean) -> Unit,
    onVibrateOnRecord: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    SettingsPage(title = stringResource(R.string.settings_recording), onBack = onBack) {
        if (!loaded) return@SettingsPage
        item(key = "listening") {
            SegmentedGroup(
                title = stringResource(R.string.settings_listening),
                items = listOf(
                    { shapes -> SilenceItem(shapes, prefs.silenceThresholdMs, onSilenceThreshold) },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_pause_other_audio),
                            supporting = stringResource(R.string.settings_pause_other_audio_summary),
                            icon = SasayakiIcons.MusicOff,
                            checked = prefs.pauseOtherAudio,
                            onCheckedChange = onPauseOtherAudio
                        )
                    },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_keep_screen_on),
                            supporting = stringResource(R.string.settings_keep_screen_on_summary),
                            icon = SasayakiIcons.Visibility,
                            checked = prefs.keepScreenOnWhileRecording,
                            onCheckedChange = onKeepScreenOnWhileRecording
                        )
                    },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_haptic_feedback),
                            supporting = stringResource(R.string.settings_haptic_feedback_summary),
                            icon = SasayakiIcons.Vibration,
                            checked = prefs.vibrateOnRecord,
                            onCheckedChange = onVibrateOnRecord
                        )
                    }
                )
            )
        }
    }
}

@Composable
fun AppsSettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppsSettingsScreen(
        loaded = state.loaded,
        prefs = state.preferences,
        onRememberProfilesChange = viewModel::setRememberProfilePerApp,
        onForgetApps = viewModel::forgetAppProfiles,
        onSkipAiChatsChange = viewModel::setSkipCleanupInAiChats,
        onBack = onBack
    )
}

@Composable
fun AppsSettingsScreen(
    loaded: Boolean,
    prefs: UserPreferences,
    onRememberProfilesChange: (Boolean) -> Unit,
    onForgetApps: () -> Unit,
    onSkipAiChatsChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    val intro = stringResource(R.string.settings_apps_intro)
    SettingsPage(title = stringResource(R.string.settings_apps), onBack = onBack) {
        if (!loaded) return@SettingsPage
        pageIntro(intro)
        item(key = "profiles") {
            val remembered = prefs.appProfiles.size
            SegmentedGroup(
                title = stringResource(R.string.settings_apps_profiles),
                items = listOf(
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_remember_profile),
                            supporting = stringResource(R.string.settings_remember_profile_summary),
                            icon = SasayakiIcons.SwitchAccount,
                            checked = prefs.rememberProfilePerApp,
                            onCheckedChange = onRememberProfilesChange
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_remembered_apps),
                            supporting = when (remembered) {
                                0 -> stringResource(R.string.settings_remembered_apps_none)
                                else -> pluralStringResource(R.plurals.settings_remembered_apps_count, remembered, remembered)
                            },
                            icon = SasayakiIcons.DeleteSweep,
                            trailingText = if (remembered > 0) stringResource(R.string.settings_forget) else "",
                            enabled = remembered > 0,
                            onClick = { confirmForget = true }
                        )
                    }
                )
            )
        }
        item(key = "cleanup") {
            SegmentedGroup(
                title = stringResource(R.string.settings_apps_cleanup),
                items = listOf(
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_skip_ai_chats),
                            supporting = stringResource(R.string.settings_skip_ai_chats_summary),
                            icon = SasayakiIcons.SmartToy,
                            checked = prefs.skipCleanupInAiChats,
                            onCheckedChange = onSkipAiChatsChange
                        )
                    }
                )
            )
        }
    }
    if (confirmForget) {
        val remembered = prefs.appProfiles.size
        DestructiveDialog(
            icon = SasayakiIcons.DeleteSweep,
            title = pluralStringResource(R.plurals.settings_forget_apps_title, remembered, remembered),
            text = stringResource(R.string.settings_forget_apps_text),
            confirmLabel = stringResource(R.string.settings_forget),
            onConfirm = onForgetApps,
            onDismiss = { confirmForget = false }
        )
    }
}

@Composable
fun HistorySettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = state.preferences
    val intro = stringResource(R.string.settings_history_intro)
    SettingsPage(title = stringResource(R.string.settings_history), onBack = onBack) {
        if (!state.loaded) return@SettingsPage
        pageIntro(intro)
        item(key = "history") {
            SegmentedGroup(
                items = listOf(
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_keep_history),
                            supporting = stringResource(R.string.settings_keep_history_summary),
                            icon = SasayakiIcons.History,
                            checked = prefs.historyEnabled,
                            onCheckedChange = viewModel::setHistoryEnabled
                        )
                    },
                    { shapes ->
                        RetentionItem(
                            shapes = shapes,
                            limit = prefs.historyRetentionLimit,
                            historyCount = state.historyCount,
                            enabled = prefs.historyEnabled,
                            onSelect = viewModel::setHistoryRetention
                        )
                    },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_count_words),
                            supporting = stringResource(R.string.settings_count_words_summary),
                            icon = SasayakiIcons.Speed,
                            checked = prefs.keepStatsWithoutHistory,
                            onCheckedChange = viewModel::setKeepStatsWithoutHistory,
                            enabled = !prefs.historyEnabled
                        )
                    }
                )
            )
        }
    }
}

@Composable
fun AppearanceSettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppearanceSettingsScreen(
        loaded = state.loaded,
        prefs = state.preferences,
        onThemeMode = viewModel::setThemeMode,
        onWallpaperColors = viewModel::setWallpaperColors,
        onBack = onBack
    )
}

@Composable
fun AppearanceSettingsScreen(
    loaded: Boolean,
    prefs: UserPreferences,
    onThemeMode: (ThemeMode) -> Unit,
    onWallpaperColors: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    SettingsPage(title = stringResource(R.string.settings_appearance), onBack = onBack) {
        if (!loaded) return@SettingsPage
        item(key = "app") {
            val items = buildList<SegmentedItem> {
                add { shapes -> ThemeItem(shapes, prefs.themeMode, onThemeMode) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_wallpaper_colors),
                            supporting = stringResource(R.string.settings_wallpaper_colors_summary),
                            icon = SasayakiIcons.Wallpaper,
                            checked = prefs.wallpaperColors,
                            onCheckedChange = onWallpaperColors
                        )
                    }
                }
            }
            SegmentedGroup(items = items)
        }
    }
}

@Composable
fun BubbleSettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bubbleRunning by viewModel.bubbleRunning.collectAsStateWithLifecycle()
    val bubbleBlocker = rememberPermissionItems().firstOrNull { it.required && !it.done }
    BubbleSettingsScreen(
        loaded = state.loaded,
        prefs = state.preferences,
        bubbleRunning = bubbleRunning,
        actions = BubbleSettingsActions(
            // The bubble cannot start without its permissions, so a missing one is asked for first.
            onBubbleEnabled = { enabled ->
                if (!enabled) viewModel.stopBubble() else bubbleBlocker?.onAction?.invoke() ?: viewModel.startBubble()
            },
            onStartOnBoot = viewModel::setStartOnBoot,
            onAutoClipboard = viewModel::setAutoClipboard,
            onBubbleSize = viewModel::setBubbleSize,
            onControlsSize = viewModel::setControlsSize,
            onBubbleTranslucent = viewModel::setBubbleTranslucent,
            onBubbleMonochrome = viewModel::setBubbleMonochrome
        ),
        onBack = onBack
    )
}

class BubbleSettingsActions(
    val onBubbleEnabled: (Boolean) -> Unit,
    val onStartOnBoot: (Boolean) -> Unit,
    val onAutoClipboard: (Boolean) -> Unit,
    val onBubbleSize: (BubbleSize) -> Unit,
    val onControlsSize: (BubbleSize) -> Unit,
    val onBubbleTranslucent: (Boolean) -> Unit,
    val onBubbleMonochrome: (Boolean) -> Unit
)

@Composable
fun BubbleSettingsScreen(
    loaded: Boolean,
    prefs: UserPreferences,
    bubbleRunning: Boolean,
    actions: BubbleSettingsActions,
    onBack: () -> Unit
) {
    val intro = stringResource(R.string.settings_bubble_intro)
    SettingsPage(title = stringResource(R.string.settings_bubble), onBack = onBack) {
        if (!loaded) return@SettingsPage
        pageIntro(intro)
        item(key = "behavior") {
            SegmentedGroup(
                items = listOf(
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_show_bubble),
                            supporting = stringResource(R.string.settings_show_bubble_summary),
                            icon = SasayakiIcons.PowerSettings,
                            checked = bubbleRunning,
                            onCheckedChange = actions.onBubbleEnabled
                        )
                    },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_start_on_boot),
                            supporting = stringResource(R.string.settings_start_on_boot_summary),
                            icon = SasayakiIcons.Refresh,
                            checked = prefs.startOnBoot,
                            onCheckedChange = actions.onStartOnBoot
                        )
                    },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_copy_on_failure),
                            supporting = stringResource(R.string.settings_copy_on_failure_summary),
                            icon = SasayakiIcons.ContentPaste,
                            checked = prefs.autoClipboard,
                            onCheckedChange = actions.onAutoClipboard
                        )
                    }
                )
            )
        }
        item(key = "look") {
            val appearance = BubbleAppearance(prefs.wallpaperColors, prefs.bubbleTranslucent, prefs.bubbleMonochrome)
            SegmentedGroup(
                title = stringResource(R.string.settings_bubble_look),
                items = listOf(
                    { shapes -> BubblePreview(shapes, appearance, BubbleScales(prefs.bubbleSize.scale, prefs.controlsSize.scale)) },
                    { shapes ->
                        SizeItem(
                            shapes = shapes,
                            icon = SasayakiIcons.ChatBubble,
                            headline = stringResource(R.string.settings_bubble_size),
                            supporting = stringResource(R.string.settings_bubble_size_summary),
                            options = BUBBLE_SIZES,
                            selected = prefs.bubbleSize,
                            onSelect = actions.onBubbleSize
                        )
                    },
                    { shapes ->
                        SizeItem(
                            shapes = shapes,
                            icon = SasayakiIcons.GraphicEq,
                            headline = stringResource(R.string.settings_controls_size),
                            supporting = stringResource(R.string.settings_controls_size_summary),
                            options = CONTROLS_SIZES,
                            selected = prefs.controlsSize,
                            onSelect = actions.onControlsSize
                        )
                    },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_see_through),
                            supporting = stringResource(R.string.settings_see_through_summary),
                            icon = SasayakiIcons.Opacity,
                            checked = prefs.bubbleTranslucent,
                            onCheckedChange = actions.onBubbleTranslucent
                        )
                    },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_monochrome),
                            supporting = stringResource(R.string.settings_monochrome_summary),
                            icon = SasayakiIcons.FilterBAndW,
                            checked = prefs.bubbleMonochrome,
                            onCheckedChange = actions.onBubbleMonochrome
                        )
                    }
                )
            )
        }
    }
}

@Composable
fun PermissionsRoute(onBack: () -> Unit) {
    val items = rememberPermissionItems(bubbleNeeded = !rememberVoiceInputEnabled())
    val restricted = rememberRestrictedSettingsLikely()
    val intro = stringResource(R.string.settings_permissions_intro)
    SettingsPage(title = stringResource(R.string.settings_permissions), onBack = onBack) {
        pageIntro(intro)
        item(key = "permissions") {
            SetupGroup(items = items, restrictedSettingsLikely = restricted)
        }
    }
}

@Composable
private fun SilenceItem(shapes: ListItemShapes, thresholdMs: Long, onChange: (Long) -> Unit) {
    val state = rememberSliderState(value = thresholdMs / 1000f, steps = 8, trackRange = 0.5f..5f)
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedItemColors(),
        leadingContent = { ItemIcon(SasayakiIcons.Timer) },
        trailingContent = {
            Text(stringResource(R.string.settings_silence_seconds, state.value), style = MaterialTheme.typography.labelLarge)
        },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_silence_summary))
                Slider(
                    state = state,
                    onValueChange = { state.value = it },
                    onValueChangeFinished = { onChange((state.value * 1000).toLong()) },
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
        }
    ) {
        Text(stringResource(R.string.settings_silence))
    }
}

@Composable
private fun RetentionItem(shapes: ListItemShapes, limit: Int, historyCount: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var pendingLimit by rememberSaveable { mutableStateOf<Int?>(null) }
    Box {
        NavigationItem(
            shapes = shapes,
            headline = stringResource(R.string.settings_retention),
            supporting = stringResource(R.string.settings_retention_summary),
            icon = SasayakiIcons.DeleteSweep,
            trailingText = "$limit",
            enabled = enabled,
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            retentionOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(pluralStringResource(R.plurals.settings_retention_option, option, option)) },
                    onClick = {
                        expanded = false
                        if (historyCount > option) pendingLimit = option else onSelect(option)
                    },
                    trailingIcon = if (option == limit) {
                        { Icon(SasayakiIcons.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }
    }
    pendingLimit?.let { newLimit ->
        val erased = historyCount - newLimit
        DestructiveDialog(
            icon = SasayakiIcons.DeleteSweep,
            title = pluralStringResource(R.plurals.settings_retention_confirm_title, newLimit, newLimit),
            text = pluralStringResource(R.plurals.settings_retention_confirm_text, erased, erased),
            confirmLabel = stringResource(R.string.settings_erase),
            onConfirm = { onSelect(newLimit) },
            onDismiss = { pendingLimit = null }
        )
    }
}

@Composable
private fun SizeItem(
    shapes: ListItemShapes,
    icon: ImageVector,
    headline: String,
    supporting: String,
    options: List<BubbleSize>,
    selected: BubbleSize,
    onSelect: (BubbleSize) -> Unit
) {
    val labels = options.associateWith { stringResource(it.label()) }
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedItemColors(),
        leadingContent = { ItemIcon(icon) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(top = Spacing.xs)) {
                Text(supporting)
                ConnectedChoice(
                    options = options,
                    selected = selected,
                    onSelect = onSelect,
                    label = labels::getValue
                )
            }
        }
    ) {
        Text(headline)
    }
}

@StringRes
internal fun BubbleSize.label(): Int = when (this) {
    BubbleSize.TINY -> R.string.settings_size_tiny
    BubbleSize.SMALL -> R.string.settings_size_small
    BubbleSize.STANDARD -> R.string.settings_size_normal
    BubbleSize.LARGE -> R.string.settings_size_large
    BubbleSize.LARGER -> R.string.settings_size_huge
}

@Composable
private fun ThemeItem(shapes: ListItemShapes, mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val labels = ThemeMode.entries.associateWith {
        stringResource(
            when (it) {
                ThemeMode.SYSTEM -> R.string.settings_theme_system
                ThemeMode.LIGHT -> R.string.settings_theme_light
                ThemeMode.DARK -> R.string.settings_theme_dark
            }
        )
    }
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedItemColors(),
        leadingContent = { ItemIcon(SasayakiIcons.Palette) },
        supportingContent = {
            ConnectedChoice(
                options = ThemeMode.entries,
                selected = mode,
                onSelect = onSelect,
                label = labels::getValue,
                modifier = Modifier.padding(top = Spacing.s)
            )
        }
    ) {
        Text(stringResource(R.string.settings_theme))
    }
}
