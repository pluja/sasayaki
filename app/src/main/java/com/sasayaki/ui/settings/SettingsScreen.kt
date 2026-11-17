package com.sasayaki.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sasayaki.BuildConfig
import com.sasayaki.R
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
    val onBackup: () -> Unit,
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
    SettingsPage(title = stringResource(R.string.settings_title), onBack = null) {
        if (!state.loaded) return@SettingsPage
        item(key = "providers") {
            SegmentedGroup(
                { shapes ->
                    NavigationItem(
                        shapes = shapes,
                        headline = stringResource(R.string.provider_list_title),
                        supporting = state.providers.joinToString { it.name }.ifEmpty { stringResource(R.string.settings_providers_none) },
                        needsAttention = state.providers.isEmpty(),
                        icon = SasayakiIcons.Dns,
                        onClick = destinations.onProviders
                    )
                }
            )
        }
        item(key = "input") {
            SegmentedGroup(
                title = stringResource(R.string.settings_input),
                items = listOf(
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_bubble),
                            supporting = stringResource(
                                if (status.bubbleRunning) R.string.settings_bubble_on else R.string.settings_bubble_off
                            ),
                            icon = SasayakiIcons.ChatBubble,
                            onClick = destinations.onBubble
                        )
                    },
                    { shapes ->
                        ExternalItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_keyboard_mic),
                            supporting = stringResource(
                                if (status.voiceInputEnabled) R.string.settings_keyboard_mic_on else R.string.settings_keyboard_mic_off
                            ),
                            icon = SasayakiIcons.Keyboard,
                            onClick = onOpenKeyboardSettings
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_recording),
                            supporting = stringResource(R.string.settings_recording_summary, prefs.silenceThresholdMs / 1000f),
                            icon = SasayakiIcons.Mic,
                            onClick = destinations.onRecording
                        )
                    }
                )
            )
        }
        item(key = "text") {
            SegmentedGroup(
                title = stringResource(R.string.settings_text),
                items = listOf(
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.vocabulary_title),
                            supporting = when (val count = prefs.vocabulary.size) {
                                0 -> stringResource(R.string.settings_vocabulary_empty)
                                else -> pluralStringResource(R.plurals.word_count, count, count)
                            },
                            icon = SasayakiIcons.Spellcheck,
                            onClick = destinations.onVocabulary
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.rules_title),
                            supporting = when (state.ruleCount) {
                                0 -> stringResource(R.string.settings_rules_empty)
                                else -> pluralStringResource(R.plurals.rules_count, state.ruleCount, state.ruleCount)
                            },
                            icon = SasayakiIcons.FindReplace,
                            onClick = destinations.onRules
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.instructions_title),
                            supporting = when (state.customPromptCount) {
                                0 -> stringResource(R.string.settings_instructions_empty)

                                else -> pluralStringResource(
                                    R.plurals.instructions_custom_count,
                                    state.customPromptCount,
                                    state.customPromptCount
                                )
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
                        headline = stringResource(R.string.settings_apps),
                        supporting = stringResource(appsSummary(prefs.rememberProfilePerApp, prefs.skipCleanupInAiChats)),
                        icon = SasayakiIcons.Apps,
                        onClick = destinations.onApps
                    )
                }
            )
        }
        item(key = "sasayaki") {
            SegmentedGroup(
                title = stringResource(R.string.settings_app_section),
                items = listOf(
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_permissions),
                            supporting = when (status.missingPermissions) {
                                0 -> stringResource(R.string.settings_permissions_all_set)

                                else -> pluralStringResource(
                                    R.plurals.settings_permissions_missing,
                                    status.missingPermissions,
                                    status.missingPermissions
                                )
                            },
                            needsAttention = status.missingPermissions > 0,
                            icon = SasayakiIcons.Shield,
                            onClick = destinations.onPermissions
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_history),
                            supporting = if (prefs.historyEnabled) {
                                pluralStringResource(
                                    R.plurals.settings_history_keeping,
                                    prefs.historyRetentionLimit,
                                    prefs.historyRetentionLimit
                                )
                            } else {
                                stringResource(R.string.settings_history_off)
                            },
                            icon = SasayakiIcons.History,
                            onClick = destinations.onHistory
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.backup_title),
                            supporting = stringResource(R.string.settings_backup_summary),
                            icon = SasayakiIcons.SettingsBackupRestore,
                            onClick = destinations.onBackup
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_appearance),
                            supporting = listOfNotNull(
                                stringResource(prefs.themeMode.label()),
                                stringResource(R.string.settings_wallpaper_colors_on).takeIf { prefs.wallpaperColors }
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
                title = stringResource(R.string.settings_about),
                items = listOf(
                    { shapes ->
                        ExternalItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_whats_new),
                            supporting = stringResource(R.string.settings_whats_new_summary),
                            icon = SasayakiIcons.NewReleases,
                            onClick = { onOpenLink(RELEASES_URL) }
                        )
                    },
                    { shapes ->
                        ExternalItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_report_problem),
                            supporting = stringResource(R.string.settings_report_problem_summary),
                            icon = SasayakiIcons.BugReport,
                            onClick = { onOpenLink(ISSUES_URL) }
                        )
                    },
                    { shapes ->
                        ExternalItem(
                            shapes = shapes,
                            headline = stringResource(R.string.settings_source_code),
                            supporting = stringResource(R.string.settings_source_code_summary),
                            icon = SasayakiIcons.Code,
                            onClick = { onOpenLink(SOURCE_URL) }
                        )
                    },
                    { shapes ->
                        NavigationItem(
                            shapes = shapes,
                            headline = stringResource(R.string.licenses_title),
                            supporting = stringResource(R.string.settings_licenses_summary),
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
                            Text(stringResource(R.string.settings_version))
                        }
                    }
                )
            )
        }
    }
}

@StringRes
fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system_summary
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@StringRes
private fun appsSummary(rememberProfiles: Boolean, skipAiChats: Boolean): Int = when {
    rememberProfiles && skipAiChats -> R.string.settings_apps_summary_profiles_and_skip_ai
    rememberProfiles -> R.string.settings_apps_summary_profiles
    skipAiChats -> R.string.settings_apps_summary_skip_ai
    else -> R.string.settings_apps_summary_default
}
