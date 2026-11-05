package com.sasayaki.service.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sasayaki.domain.model.Profile
import com.sasayaki.service.bubble.BubbleActions
import com.sasayaki.service.bubble.BubbleAppearance
import com.sasayaki.service.bubble.BubblePhase
import com.sasayaki.service.bubble.BubbleTheme
import com.sasayaki.service.bubble.BubbleUiState
import com.sasayaki.service.bubble.Waveform
import com.sasayaki.service.bubble.formatElapsed
import com.sasayaki.service.keepScreenOnWhileRecording
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.SasayakiMark
import com.sasayaki.ui.theme.Spacing

/** KEYBOARD_WAVEFORM_BARS is how many recent audio levels the panel's waveform draws. */
const val KEYBOARD_WAVEFORM_BARS = 40

// PanelHeight is close to a keyboard's own, so the app above does not jump when it switches.
private val PanelHeight = 264.dp
private val MainButtonSize = 80.dp
private val SideButtonSize = 56.dp

@Composable
fun VoiceInputPanel(
    state: BubbleUiState,
    levels: () -> List<Float>,
    profiles: List<Profile>,
    actions: BubbleActions,
    appearance: BubbleAppearance,
    onSelectProfile: (Long) -> Unit,
    onSwitchKeyboard: () -> Unit,
    keepScreenOnWhileRecording: Boolean = true
) {
    BubbleTheme(appearance) {
        Surface(
            modifier = Modifier.keepScreenOnWhileRecording(state.phase, keepScreenOnWhileRecording),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(PanelHeight)
                    .padding(vertical = Spacing.s)
            ) {
                TopRow(profiles, onSelectProfile)
                Status(state, levels, modifier = Modifier.weight(1f))
                Controls(state.phase, actions, onSwitchKeyboard)
            }
        }
    }
}

// TopRow names Sasayaki, because the panel replaces the keyboard and could otherwise be mistaken for it.
@Composable
private fun TopRow(profiles: List<Profile>, onSelect: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.l)) {
        Icon(SasayakiMark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(
            text = "Sasayaki",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.xs, end = Spacing.s)
        )
        ProfileChips(profiles, onSelect, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ProfileChips(profiles: List<Profile>, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(start = Spacing.xs, end = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        items(profiles, key = Profile::id) { profile ->
            FilterChip(
                selected = profile.isActive,
                onClick = { onSelect(profile.id) },
                label = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun Status(state: BubbleUiState, levels: () -> List<Float>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        when (val phase = state.phase) {
            is BubblePhase.Recording -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                Waveform(
                    levels = levels,
                    paused = phase.paused,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    bars = KEYBOARD_WAVEFORM_BARS,
                    barWidth = 4.dp
                )
                StatusText(if (phase.paused) "Paused" else formatElapsed(state.elapsedSeconds))
            }

            is BubblePhase.Processing -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(40.dp),
                    color = if (phase.cleaningUp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
                StatusText(if (phase.cleaningUp) "Cleaning up" else "Transcribing")
            }

            is BubblePhase.Failed -> StatusText(phase.message, color = MaterialTheme.colorScheme.error)

            BubblePhase.NoSpeech -> StatusText("Didn't catch anything. Tap the mic to try again.")

            BubblePhase.Idle -> if (state.justInserted) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Icon(SasayakiIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    StatusText("Typed. Tap the mic to add more.")
                }
            } else {
                StatusText("Tap the mic to dictate")
            }
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Controls keeps every button in a fixed slot, so the main button never moves under a thumb
 * as the dictation goes from recording to processing.
 */
@Composable
private fun Controls(phase: BubblePhase, actions: BubbleActions, onSwitchKeyboard: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SideButton(SasayakiIcons.Keyboard, "Back to keyboard", onSwitchKeyboard)
        when (phase) {
            is BubblePhase.Recording -> SideButton(SasayakiIcons.Close, "Discard recording", actions.onCancel)
            is BubblePhase.Processing -> SideButton(SasayakiIcons.Close, "Cancel", actions.onCancelProcessing)
            else -> Spacer(Modifier.size(SideButtonSize))
        }
        MainButton(phase, actions)
        if (phase is BubblePhase.Recording) {
            SideButton(
                icon = if (phase.paused) SasayakiIcons.PlayArrow else SasayakiIcons.Pause,
                description = if (phase.paused) "Resume" else "Pause",
                onClick = actions.onTogglePause
            )
        } else {
            Spacer(Modifier.size(SideButtonSize))
        }
        Spacer(Modifier.size(SideButtonSize))
    }
}

private class MainAction(val icon: ImageVector, val description: String, val onClick: () -> Unit)

@Composable
private fun MainButton(phase: BubblePhase, actions: BubbleActions) {
    val action = when (phase) {
        is BubblePhase.Recording -> MainAction(SasayakiIcons.Stop, "Finish and insert", actions.onStop)

        is BubblePhase.Processing -> null

        is BubblePhase.Failed -> if (phase.canRetry) {
            MainAction(SasayakiIcons.Refresh, "Try again", actions.onRetry)
        } else {
            MainAction(SasayakiIcons.Mic, "Dictate", actions.onStart)
        }

        BubblePhase.NoSpeech, BubblePhase.Idle -> MainAction(SasayakiIcons.Mic, "Dictate", actions.onStart)
    }
    if (action == null) {
        Spacer(Modifier.size(MainButtonSize))
        return
    }
    val recording = phase is BubblePhase.Recording
    FilledIconButton(
        onClick = action.onClick,
        modifier = Modifier.size(MainButtonSize),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (recording) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            contentColor = if (recording) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(action.icon, contentDescription = action.description, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun SideButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(SideButtonSize)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(28.dp))
    }
}
