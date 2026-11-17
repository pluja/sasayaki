package com.sasayaki.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.BuildConfig
import com.sasayaki.data.preferences.ThemeMode
import com.sasayaki.ui.common.openKeyboardSettings
import com.sasayaki.ui.common.rememberPermissionItems
import com.sasayaki.ui.common.rememberVoiceInputEnabled
import com.sasayaki.ui.components.ExternalItem
import com.sasayaki.ui.components.ItemIcon
import com.sasayaki.ui.components.NavigationItem
import com.sasayaki.ui.components.SegmentedGroup
import com.sasayaki.ui.components.SettingsPage
import com.sasayaki.ui.components.segmentedItemColors
import com.sasayaki.ui.theme.SasayakiIcons
import java.util.Locale

private const val SOURCE_URL = "https://github.com/pluja/sasayaki"
private const val RELEASES_URL = "$SOURCE_URL/releases"
private const val ISSUES_URL = "$SOURCE_URL/issues"

class SettingsDestinations(
    val onProviders: () -> Unit,
    val onBubble: () -> Unit,
    val onRecording: () -> Unit,
    val onApps: () -> Unit,
    val onRules: () -> Unit,
    val onVocabulary: () -> Unit,
    val onPrompts: () -> Unit,
    val onPermissions: () -> Unit,
    val onHistory: () -> Unit,
    val onAppearance: () -> Unit,
    val onLicenses: () -> Unit
)

@Composable
fun SettingsRoute(destinations: SettingsDestinations, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bubbleRunning by viewModel.bubbleRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voiceInputEnabled = rememberVoiceInputEnabled()
    val missingPermissions = rememberPermissionItems(bubbleNeeded = !voiceInputEnabled).count { it.required && !it.done }
    SettingsScreen(
        state = state,
        status = SettingsStatus(missingPermissions, voiceInputEnabled, bubbleRunning),
        destinations = destinations,
        onOpenKeyboardSettings = { openKeyboardSettings(context) },
        onOpenLink = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    )
}

@Immutable
data class SettingsStatus(val missingPermissions: Int, val voiceInputEnabled: Boolean, val bubbleRunning: Boolean)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    status: SettingsStatus,
    destinations: SettingsDestinations,
    onOpenKeyboardSettings: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    val prefs = state.preferences
    SettingsPage(title = "Settings", onBack = null) {
        if (!state.loaded) return@SettingsPage
        item(key = "providers") {
            SegmentedGroup(
                { shapes ->
                    NavigationItem(
                        shapes = shapes,
                        headline = "Providers",
                        supporting = state.providers.joinToString { it.name }.ifEmpty { "None yet. Add one to start dictating." },
                        needsAttention = state.providers.isEmpty(),
                        icon = SasayakiIcons.Dns,
                        onClick = destinations.onProviders
                    )
                }
            )
        }
        item(key = "input") {
            SegmentedGroup(
                title = "Input",
                items = listOf(
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Bubble",
                            supporting = if (status.bubbleRunning) "On. Appears when your keyboard opens." else "Off",
                            icon = SasayakiIcons.ChatBubble,
                            onClick = destinations.onBubble
                        )
                    },
                    { shapes ->
                        ExternalItem(
                            shapes = shapes,
                            headline = "Keyboard mic key",
                            supporting = if (status.voiceInputEnabled) {
                                "On. Your keyboard's mic key opens Sasayaki."
                            } else {
                                "Turn on Sasayaki voice input in Android settings"
                            },
                            icon = SasayakiIcons.Keyboard,
                            onClick = onOpenKeyboardSettings
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Recording",
                            supporting = "Stops after " +
                                "${String.format(Locale.getDefault(), "%.1f", prefs.silenceThresholdMs / 1000f)} s of silence",
                            icon = SasayakiIcons.Mic,
                            onClick = destinations.onRecording
                        )
                    }
                )
            )
        }
        item(key = "text") {
            SegmentedGroup(
                title = "Text",
                items = listOf(
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Vocabulary",
                            supporting = when (val count = prefs.vocabulary.size) {
                                0 -> "Names and terms to spell right"
                                1 -> "1 word"
                                else -> "$count words"
                            },
                            icon = SasayakiIcons.Spellcheck,
                            onClick = destinations.onVocabulary
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Replacement rules",
                            supporting = when (state.ruleCount) {
                                0 -> "Fix words your speech model gets wrong"
                                1 -> "1 rule"
                                else -> "${state.ruleCount} rules"
                            },
                            icon = SasayakiIcons.FindReplace,
                            onClick = destinations.onRules
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Cleanup instructions",
                            supporting = when (state.customPromptCount) {
                                0 -> "Habits the cleanup model should follow"
                                1 -> "1 of your own"
                                else -> "${state.customPromptCount} of your own"
                            },
                            icon = SasayakiIcons.Notes,
                            onClick = destinations.onPrompts
                        )
                    }
                )
            )
        }
        item(key = "apps") {
            SegmentedGroup(
                { shapes ->
                    NavigationItem(
                        shapes = shapes,
                        headline = "Apps",
                        supporting = appsSummary(prefs.rememberProfilePerApp, prefs.skipCleanupInAiChats),
                        icon = SasayakiIcons.Apps,
                        onClick = destinations.onApps
                    )
                }
            )
        }
        item(key = "sasayaki") {
            SegmentedGroup(
                title = "Sasayaki",
                items = listOf(
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Permissions",
                            supporting = when (status.missingPermissions) {
                                0 -> "All set"
                                1 -> "1 needs your attention"
                                else -> "${status.missingPermissions} need your attention"
                            },
                            needsAttention = status.missingPermissions > 0,
                            icon = SasayakiIcons.Shield,
                            onClick = destinations.onPermissions
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "History and privacy",
                            supporting = if (prefs.historyEnabled) {
                                "Keeping the last ${prefs.historyRetentionLimit}"
                            } else {
                                "Not keeping history"
                            },
                            icon = SasayakiIcons.History,
                            onClick = destinations.onHistory
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Appearance",
                            supporting = listOfNotNull(
                                prefs.themeMode.label(),
                                "wallpaper colors".takeIf { prefs.wallpaperColors }
                            ).joinToString(" · "),
                            icon = SasayakiIcons.Palette,
                            onClick = destinations.onAppearance
                        )
                    }
                )
            )
        }
        item(key = "about") {
            SegmentedGroup(
                title = "About",
                items = listOf(
                    { shapes ->
                        ExternalItem(
                            shapes = shapes,
                            headline = "What's new",
                            supporting = "Release notes for every version",
                            icon = SasayakiIcons.NewReleases,
                            onClick = { onOpenLink(RELEASES_URL) }
                        )
                    },
                    { shapes ->
                        ExternalItem(
                            shapes = shapes,
                            headline = "Report a problem",
                            supporting = "Open an issue on GitHub",
                            icon = SasayakiIcons.BugReport,
                            onClick = { onOpenLink(ISSUES_URL) }
                        )
                    },
                    { shapes ->
                        ExternalItem(
                            shapes = shapes,
                            headline = "Source code",
                            supporting = "Free software under the GPL-3.0",
                            icon = SasayakiIcons.Code,
                            onClick = { onOpenLink(SOURCE_URL) }
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = "Open-source licenses",
                            supporting = "The libraries Sasayaki is built with",
                            icon = SasayakiIcons.License,
                            onClick = destinations.onLicenses
                        )
                    },
                    { shapes ->
                        SegmentedListItem(
                            shapes = shapes,
                            colors = segmentedItemColors(),
                            leadingContent = { ItemIcon(SasayakiIcons.Info) },
                            trailingContent = { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelLarge) }
                        ) {
                            Text("Version")
                        }
                    }
                )
            )
        }
    }
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System theme"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun appsSummary(rememberProfiles: Boolean, skipAiChats: Boolean): String = when {
    rememberProfiles && skipAiChats -> "Per-app profiles, no AI chat cleanup"
    rememberProfiles -> "Remembers each app's profile"
    skipAiChats -> "Skips cleanup in AI chats"
    else -> "Same everywhere"
}
