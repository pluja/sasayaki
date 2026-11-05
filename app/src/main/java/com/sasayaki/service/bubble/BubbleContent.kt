package com.sasayaki.service.bubble

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sasayaki.domain.model.Profile
import com.sasayaki.service.keepScreenOnWhileRecording
import com.sasayaki.ui.theme.SasayakiIcons
import com.sasayaki.ui.theme.SasayakiMark
import com.sasayaki.ui.theme.Spacing
import kotlin.math.pow

private val BubbleSize = 52.dp
private val PillHeight = 56.dp

/** WAVEFORM_BARS is how many recent audio levels the recording pill draws. */
const val WAVEFORM_BARS = 18
private const val TRANSLUCENT_ALPHA = 0.8f

class BubbleActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onCancel: () -> Unit,
    val onTogglePause: () -> Unit,
    val onCancelProcessing: () -> Unit,
    val onRetry: () -> Unit,
    val onDismissError: () -> Unit
)

/**
 * BubbleMode is the shape the bubble takes; content within a mode changes without a size
 * morph. Each mode's window size is fixed and generous enough for its content, so the
 * overlay window never has to chase the content's animated size frame by frame.
 */
enum class BubbleMode(val windowWidth: Dp, val windowHeight: Dp) {
    IDLE(68.dp, 68.dp),
    RECORDING(284.dp, 72.dp),
    PROCESSING(224.dp, 72.dp),
    FAILED(332.dp, 72.dp),
    NO_SPEECH(244.dp, 72.dp)
}

/** BubbleScales sizes the idle bubble separately from the controls it expands into. */
@Immutable
data class BubbleScales(val bubble: Float = 1f, val controls: Float = 1f) {
    fun of(mode: BubbleMode): Float = if (mode == BubbleMode.IDLE) bubble else controls
}

val BubblePhase.mode: BubbleMode
    get() = when (this) {
        BubblePhase.Idle -> BubbleMode.IDLE
        is BubblePhase.Recording -> BubbleMode.RECORDING
        is BubblePhase.Processing -> BubbleMode.PROCESSING
        is BubblePhase.Failed -> BubbleMode.FAILED
        BubblePhase.NoSpeech -> BubbleMode.NO_SPEECH
    }

@Composable
fun BubbleContent(
    state: BubbleUiState,
    levels: () -> List<Float>,
    actions: BubbleActions,
    onShowProfiles: () -> Unit,
    alignment: Alignment = Alignment.CenterEnd,
    scales: BubbleScales = BubbleScales(),
    keepScreenOnWhileRecording: Boolean = true
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val density = LocalDensity.current
    Box(
        modifier = Modifier.fillMaxSize().keepScreenOnWhileRecording(state.phase, keepScreenOnWhileRecording),
        contentAlignment = alignment
    ) {
        // Keyed by mode but rendering the state it was given, so an outgoing pill fades out
        // showing what it showed rather than the next state's values.
        AnimatedContent(
            targetState = state,
            contentKey = { it.phase.mode },
            contentAlignment = alignment,
            transitionSpec = {
                (fadeIn(fade) + scaleIn(fade, initialScale = 0.9f)) togetherWith fadeOut(fade) using
                    SizeTransform(clip = false) { _, _ -> spatial }
            },
            label = "bubble"
        ) { shown ->
            // Scaling density grows every dimension of a mode together, touch targets included,
            // and each mode keeps its own scale while it animates out.
            val scale = scales.of(shown.phase.mode)
            CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
                // Padding leaves room for the shadow inside the overlay window's bounds.
                Box(modifier = Modifier.padding(Spacing.s)) {
                    when (val phase = shown.phase) {
                        BubblePhase.Idle -> IdleBubble(shown.justInserted, actions.onStart, onShowProfiles)
                        is BubblePhase.Recording -> RecordingPill(shown, levels, actions, onShowProfiles)
                        is BubblePhase.Processing -> ProcessingPill(phase.cleaningUp, actions)
                        is BubblePhase.Failed -> FailedPill(phase, actions)
                        BubblePhase.NoSpeech -> NoSpeechPill(actions.onStart)
                    }
                }
            }
        }
    }
}

@Composable
private fun PillSurface(
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit
) {
    // A shadow would show through a see-through pill as a dark smudge, so the border
    // alone outlines it.
    val translucent = LocalBubbleTranslucent.current
    Surface(
        shape = CircleShape,
        color = if (translucent) color.copy(alpha = TRANSLUCENT_ALPHA) else color,
        contentColor = contentColor,
        shadowElevation = if (translucent) 0.dp else 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IdleBubble(showDone: Boolean, onStart: () -> Unit, onShowProfiles: () -> Unit) {
    val scale by animateFloatAsState(if (showDone) 1.08f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "done")
    PillSurface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Box(
            modifier = Modifier
                .size(BubbleSize)
                .scale(scale)
                .combinedClickable(
                    onClickLabel = "Start dictation",
                    onLongClickLabel = "Switch profile",
                    onClick = onStart,
                    onLongClick = onShowProfiles
                )
                .semantics { contentDescription = "Dictate" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (showDone) SasayakiIcons.Check else SasayakiMark,
                contentDescription = null,
                modifier = Modifier.size(if (showDone) 26.dp else 30.dp)
            )
        }
    }
}

@Composable
private fun RecordingPill(state: BubbleUiState, levels: () -> List<Float>, actions: BubbleActions, onShowProfiles: () -> Unit) {
    val paused = (state.phase as? BubblePhase.Recording)?.paused == true
    PillSurface {
        Row(
            modifier = Modifier
                .height(PillHeight)
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = actions.onCancel) {
                Icon(SasayakiIcons.Close, contentDescription = "Discard recording")
            }
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Switch profile", onClick = onShowProfiles)
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Waveform(levels = levels, paused = paused)
                Text(
                    text = if (paused) "Paused · ${state.profileName}" else "${formatElapsed(state.elapsedSeconds)} · ${state.profileName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 104.dp)
                )
            }
            IconButton(onClick = actions.onTogglePause) {
                Icon(
                    imageVector = if (paused) SasayakiIcons.PlayArrow else SasayakiIcons.Pause,
                    contentDescription = if (paused) "Resume" else "Pause"
                )
            }
            FilledIconButton(
                onClick = actions.onStop,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(SasayakiIcons.Stop, contentDescription = "Finish and insert")
            }
        }
    }
}

@Composable
internal fun Waveform(
    levels: () -> List<Float>,
    paused: Boolean,
    modifier: Modifier = Modifier
        .width(96.dp)
        .height(22.dp),
    bars: Int = WAVEFORM_BARS,
    barWidth: Dp = 3.dp
) {
    val color = if (paused) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier) {
        val values = levels()
        val barPx = barWidth.toPx()
        val gap = (size.width - barPx * bars) / (bars - 1)
        val minHeight = barPx
        repeat(bars) { index ->
            // Newest level on the right; older ones scroll left.
            val level = values.getOrNull(values.size - bars + index) ?: 0f
            val height = (minHeight + (size.height - minHeight) * level.coerceIn(0f, 1f).pow(0.6f))
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barPx + gap), (size.height - height) / 2),
                size = Size(barPx, height),
                cornerRadius = CornerRadius(barPx / 2)
            )
        }
    }
}

@Composable
private fun ProcessingPill(cleaningUp: Boolean, actions: BubbleActions) {
    PillSurface {
        Row(
            modifier = Modifier
                .height(PillHeight)
                .padding(start = Spacing.s, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            LoadingIndicator(
                modifier = Modifier.size(36.dp),
                color = if (cleaningUp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (cleaningUp) "Cleaning up" else "Transcribing",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = Spacing.xs)
            )
            IconButton(onClick = actions.onCancelProcessing) {
                Icon(SasayakiIcons.Close, contentDescription = "Cancel")
            }
        }
    }
}

@Composable
private fun FailedPill(phase: BubblePhase.Failed, actions: BubbleActions) {
    PillSurface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(
            modifier = Modifier
                .height(PillHeight)
                .padding(start = Spacing.l, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Icon(SasayakiIcons.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = phase.message,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp)
            )
            if (phase.canRetry) {
                IconButton(onClick = actions.onRetry) {
                    Icon(SasayakiIcons.Refresh, contentDescription = "Try again")
                }
            }
            IconButton(onClick = actions.onDismissError) {
                Icon(SasayakiIcons.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun NoSpeechPill(onTryAgain: () -> Unit) {
    PillSurface {
        Row(
            modifier = Modifier
                .height(PillHeight)
                .clickable(onClickLabel = "Dictate again", onClick = onTryAgain)
                .padding(horizontal = Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Icon(
                SasayakiIcons.MicOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Didn't catch anything", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ProfileMenu(profiles: List<Profile>, onSelect: (Long) -> Unit) {
    Box(modifier = Modifier.padding(Spacing.s)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 200.dp, max = 260.dp)
                    .padding(vertical = Spacing.s)
            ) {
                Text(
                    "Profile",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)
                )
                profiles.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .clickable { onSelect(profile.id) }
                            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = profile.isActive, onClick = null)
                        Text(
                            profile.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = Spacing.m, end = Spacing.l)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DismissTarget(armed: Boolean) {
    val scale by animateFloatAsState(if (armed) 1.2f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "target")
    Box(modifier = Modifier.padding(Spacing.l), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                SasayakiIcons.Close,
                contentDescription = "Turn off bubble",
                tint = if (armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

internal fun formatElapsed(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
