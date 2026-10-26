package com.sasayaki.ui.settings

import android.os.Build
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.data.preferences.BUBBLE_SIZES
import com.sasayaki.data.preferences.BubbleSize
import com.sasayaki.data.preferences.CONTROLS_SIZES
import com.sasayaki.data.preferences.ThemeMode
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.service.bubble.BubbleAppearance
import com.sasayaki.service.bubble.BubbleScales
import com.sasayaki.ui.common.openKeyboardSettings
import com.sasayaki.ui.common.rememberPermissionItems
import com.sasayaki.ui.common.rememberRestrictedSettingsLikely
import com.sasayaki.ui.common.rememberVoiceInputEnabled
import com.sasayaki.ui.components.ConnectedChoice
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
    val prefs = state.preferences
    SettingsPage(title = "Recording", onBack = onBack) {
        if (!state.loaded) return@SettingsPage
        item(key = "listening") {
            SegmentedGroup(
                title = "Listening",
                items = listOf(
                    { shapes -> SilenceItem(shapes, prefs.silenceThresholdMs, viewModel::setSilenceThreshold) },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "Pause other audio",
                            supporting = "Music and videos pause while you speak",
                            icon = SasayakiIcons.MusicOff,
                            checked = prefs.pauseOtherAudio,
                            onCheckedChange = viewModel::setPauseOtherAudio
                        )
                    },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "Keep listening when the keyboard hides",
                            supporting = "Hide the keyboard or switch apps while you speak, and the text lands where you stop",
                            icon = SasayakiIcons.Keyboard,
                            checked = prefs.keepListeningWhenKeyboardHides,
                            onCheckedChange = viewModel::setKeepListeningWhenKeyboardHides
                        )
                    },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "Haptic feedback",
                            supporting = "A short buzz when recording starts and stops",
                            icon = SasayakiIcons.Vibration,
                            checked = prefs.vibrateOnRecord,
                            onCheckedChange = viewModel::setVibrateOnRecord
                        )
                    }
                )
            )
        }
        item(key = "delivery") {
            SegmentedGroup(
                title = "Delivery",
                items = listOf(
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "Copy when typing fails",
                            supporting = "If the text can't be typed into the field, it is copied instead",
                            icon = SasayakiIcons.ContentPaste,
                            checked = prefs.autoClipboard,
                            onCheckedChange = viewModel::setAutoClipboard
                        )
                    },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "Start on boot",
                            supporting = "Turn the bubble back on after a restart",
                            icon = SasayakiIcons.PowerSettings,
                            checked = prefs.startOnBoot,
                            onCheckedChange = viewModel::setStartOnBoot
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
    SettingsPage(title = "Apps", onBack = onBack) {
        if (!loaded) return@SettingsPage
        pageIntro("Sasayaki can adapt to the app you are dictating into.")
        item(key = "profiles") {
            val remembered = prefs.appProfiles.size
            SegmentedGroup(
                title = "Profiles",
                items = listOf(
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "Remember profile per app",
                            supporting = "Switch to the profile you last picked from the bubble in each app",
                            icon = SasayakiIcons.SwitchAccount,
                            checked = prefs.rememberProfilePerApp,
                            onCheckedChange = onRememberProfilesChange
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Remembered apps",
                            supporting = when (remembered) {
                                0 -> "None yet"
                                1 -> "1 app"
                                else -> "$remembered apps"
                            },
                            icon = SasayakiIcons.DeleteSweep,
                            trailingText = if (remembered > 0) "Forget" else "",
                            enabled = remembered > 0,
                            onClick = onForgetApps
                        )
                    }
                )
            )
        }
        item(key = "cleanup") {
            SegmentedGroup(
                title = "Cleanup",
                items = listOf(
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "Skip cleanup in AI chats",
                            supporting = "Assistants like ChatGPT and Claude get your words as spoken, " +
                                "so cleanup never answers the prompt for them",
                            icon = SasayakiIcons.SmartToy,
                            checked = prefs.skipCleanupInAiChats,
                            onCheckedChange = onSkipAiChatsChange
                        )
                    }
                )
            )
        }
    }
}

@Composable
fun HistorySettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = state.preferences
    SettingsPage(title = "History and privacy", onBack = onBack) {
        if (!state.loaded) return@SettingsPage
        pageIntro("Dictations never leave this device except to reach your providers.")
        item(key = "history") {
            SegmentedGroup(
                items = listOf(
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "Keep history",
                            supporting = "Saves text and audio so you can copy or retry later",
                            icon = SasayakiIcons.History,
                            checked = prefs.historyEnabled,
                            onCheckedChange = viewModel::setHistoryEnabled
                        )
                    },
                    { shapes ->
                        RetentionItem(shapes, prefs.historyRetentionLimit, prefs.historyEnabled, viewModel::setHistoryRetention)
                    },
                    { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "Count words without history",
                            supporting = "Keep your totals when nothing else is saved",
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
    val bubbleRunning by viewModel.bubbleRunning.collectAsStateWithLifecycle()
    val bubbleBlocker = rememberPermissionItems().firstOrNull { it.required && !it.done }
    AppearanceSettingsScreen(
        loaded = state.loaded,
        prefs = state.preferences,
        bubbleRunning = bubbleRunning,
        actions = AppearanceActions(
            // The bubble cannot start without its permissions, so a missing one is asked for first.
            onBubbleEnabled = { enabled ->
                if (!enabled) viewModel.stopBubble() else bubbleBlocker?.onAction?.invoke() ?: viewModel.startBubble()
            },
            onCarryKeyboardDictation = viewModel::setCarryKeyboardDictation,
            onThemeMode = viewModel::setThemeMode,
            onWallpaperColors = viewModel::setWallpaperColors,
            onBubbleSize = viewModel::setBubbleSize,
            onControlsSize = viewModel::setControlsSize,
            onBubbleTranslucent = viewModel::setBubbleTranslucent,
            onBubbleMonochrome = viewModel::setBubbleMonochrome
        ),
        onBack = onBack
    )
}

class AppearanceActions(
    val onBubbleEnabled: (Boolean) -> Unit,
    val onCarryKeyboardDictation: (Boolean) -> Unit,
    val onThemeMode: (ThemeMode) -> Unit,
    val onWallpaperColors: (Boolean) -> Unit,
    val onBubbleSize: (BubbleSize) -> Unit,
    val onControlsSize: (BubbleSize) -> Unit,
    val onBubbleTranslucent: (Boolean) -> Unit,
    val onBubbleMonochrome: (Boolean) -> Unit
)

@Composable
fun AppearanceSettingsScreen(
    loaded: Boolean,
    prefs: UserPreferences,
    bubbleRunning: Boolean,
    actions: AppearanceActions,
    onBack: () -> Unit
) {
    SettingsPage(title = "Appearance", onBack = onBack) {
        if (!loaded) return@SettingsPage
        item(key = "app") {
            val items = buildList<SegmentedItem> {
                add { shapes -> ThemeItem(shapes, prefs.themeMode, actions.onThemeMode) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "Wallpaper colors",
                            supporting = "Match your wallpaper instead of Sasayaki green",
                            icon = SasayakiIcons.Wallpaper,
                            checked = prefs.wallpaperColors,
                            onCheckedChange = actions.onWallpaperColors
                        )
                    }
                }
            }
            SegmentedGroup(title = "App", items = items)
        }
        item(key = "bubble") {
            val appearance = BubbleAppearance(prefs.wallpaperColors, prefs.bubbleTranslucent, prefs.bubbleMonochrome)
            val items = buildList<SegmentedItem> {
                add { shapes ->
                    SwitchItem(
                        shapes = shapes,
                        headline = "Show the bubble",
                        supporting = if (bubbleRunning) {
                            "Appears whenever your keyboard opens"
                        } else {
                            "Turn it on to set its size and look"
                        },
                        icon = SasayakiIcons.PowerSettings,
                        checked = bubbleRunning,
                        onCheckedChange = actions.onBubbleEnabled
                    )
                }
                if (!bubbleRunning) {
                    add { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "Carry keyboard dictations",
                            supporting = "Show the bubble while a dictation runs after you leave the keyboard panel",
                            icon = SasayakiIcons.Keyboard,
                            checked = prefs.carryKeyboardDictation,
                            onCheckedChange = actions.onCarryKeyboardDictation
                        )
                    }
                }
                if (bubbleRunning) {
                    add { shapes -> BubblePreview(shapes, appearance, BubbleScales(prefs.bubbleSize.scale, prefs.controlsSize.scale)) }
                    add { shapes ->
                        SizeItem(
                            shapes = shapes,
                            icon = SasayakiIcons.ChatBubble,
                            headline = "Bubble size",
                            supporting = "The bubble that waits by your keyboard.",
                            options = BUBBLE_SIZES,
                            selected = prefs.bubbleSize,
                            onSelect = actions.onBubbleSize
                        )
                    }
                    add { shapes ->
                        SizeItem(
                            shapes = shapes,
                            icon = SasayakiIcons.GraphicEq,
                            headline = "Controls size",
                            supporting = "The controls while recording. Bigger buttons are easier to hit on the move.",
                            options = CONTROLS_SIZES,
                            selected = prefs.controlsSize,
                            onSelect = actions.onControlsSize
                        )
                    }
                    add { shapes ->
                        SwitchItem(
                            shapes = shapes,
                            headline = "See-through",
                            supporting = "Let what is underneath show through the bubble",
                            icon = SasayakiIcons.Opacity,
                            checked = prefs.bubbleTranslucent,
                            onCheckedChange = actions.onBubbleTranslucent
                        )
                    }
                }
                add { shapes ->
                    SwitchItem(
                        shapes = shapes,
                        headline = "Monochrome",
                        supporting = "Gray instead of color, for the bubble and the keyboard panel",
                        icon = SasayakiIcons.FilterBAndW,
                        checked = prefs.bubbleMonochrome,
                        onCheckedChange = actions.onBubbleMonochrome
                    )
                }
            }
            SegmentedGroup(title = "Bubble", items = items)
        }
    }
}

@Composable
fun PermissionsRoute(onBack: () -> Unit) {
    val items = rememberPermissionItems(bubbleNeeded = !rememberVoiceInputEnabled())
    val restricted = rememberRestrictedSettingsLikely()
    SettingsPage(title = "Permissions", onBack = onBack) {
        pageIntro("Sasayaki needs these to listen when you ask and type the result where you are.")
        item(key = "permissions") {
            SetupGroup(items = items, restrictedSettingsLikely = restricted)
        }
    }
}

@Composable
private fun SilenceItem(shapes: ListItemShapes, thresholdMs: Long, onChange: (Long) -> Unit) {
    val state = rememberSliderState(value = thresholdMs / 1000f, steps = 8, trackRange = 0.5f..5f)
    val locale = LocalConfiguration.current.locales[0]
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedItemColors(),
        leadingContent = { ItemIcon(SasayakiIcons.Timer) },
        trailingContent = {
            Text(String.format(locale, "%.1f s", state.value), style = MaterialTheme.typography.labelLarge)
        },
        supportingContent = {
            Column {
                Text("Recording stops after this much quiet")
                Slider(
                    state = state,
                    onValueChange = { state.value = it },
                    onValueChangeFinished = { onChange((state.value * 1000).toLong()) },
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
        }
    ) {
        Text("Stop after silence")
    }
}

@Composable
private fun RetentionItem(shapes: ListItemShapes, limit: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        NavigationItem(
            shapes = shapes,
            headline = "Keep the last",
            supporting = "Older dictations are removed automatically",
            icon = SasayakiIcons.DeleteSweep,
            trailingText = "$limit",
            enabled = enabled,
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            retentionOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text("$option dictations") },
                    onClick = {
                        onSelect(option)
                        expanded = false
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
                    label = BubbleSize::label
                )
            }
        }
    ) {
        Text(headline)
    }
}

internal fun BubbleSize.label(): String = when (this) {
    BubbleSize.TINY -> "Tiny"
    BubbleSize.SMALL -> "Small"
    BubbleSize.STANDARD -> "Normal"
    BubbleSize.LARGE -> "Large"
    BubbleSize.LARGER -> "Huge"
}

@Composable
private fun ThemeItem(shapes: ListItemShapes, mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SegmentedListItem(
        shapes = shapes,
        colors = segmentedItemColors(),
        leadingContent = { ItemIcon(SasayakiIcons.Palette) },
        supportingContent = {
            ConnectedChoice(
                options = ThemeMode.entries,
                selected = mode,
                onSelect = onSelect,
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                },
                modifier = Modifier.padding(top = Spacing.s)
            )
        }
    ) {
        Text("Theme")
    }
}
