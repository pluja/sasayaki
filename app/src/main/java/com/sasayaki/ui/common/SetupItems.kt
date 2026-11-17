package com.sasayaki.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sasayaki.R
import com.sasayaki.ui.theme.SasayakiIcons

const val ACCESSIBILITY_KEY = "accessibility"
const val MICROPHONE_KEY = "microphone"
private const val OVERLAY_KEY = "overlay"

// BUBBLE_KEYS are the steps only the bubble needs; the keyboard mic key works without them.
val BUBBLE_KEYS = setOf(OVERLAY_KEY, ACCESSIBILITY_KEY)

@Immutable
data class SetupItem(
    val key: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val done: Boolean,
    val required: Boolean,
    val actionLabel: String,
    val onAction: () -> Unit
)

@Composable
fun rememberPermissionItems(bubbleNeeded: Boolean = true): List<SetupItem> {
    val microphone = rememberMicrophonePermissionState()
    val overlay = rememberOverlayPermissionState()
    val accessibility = rememberAccessibilityPermissionState()
    val notifications = rememberNotificationPermissionState()
    return listOf(
        microphone.toItem(MICROPHONE_KEY, SasayakiIcons.Mic, required = true),
        overlay.toItem(OVERLAY_KEY, SasayakiIcons.Layers, required = bubbleNeeded),
        accessibility.toItem(ACCESSIBILITY_KEY, SasayakiIcons.Keyboard, required = bubbleNeeded),
        notifications.toItem("notifications", SasayakiIcons.Notifications, required = false)
    )
}

@Composable
fun rememberVoiceInputItem(): SetupItem {
    val context = LocalContext.current
    return SetupItem(
        key = "voice_input",
        title = stringResource(R.string.setup_voice_input_title),
        description = stringResource(R.string.setup_voice_input_description),
        icon = SasayakiIcons.Keyboard,
        done = rememberVoiceInputEnabled(),
        required = true,
        actionLabel = stringResource(R.string.action_turn_on),
        onAction = { openKeyboardSettings(context) }
    )
}

private fun PermissionStatus.toItem(key: String, icon: ImageVector, required: Boolean) = SetupItem(
    key = key,
    title = name,
    description = description,
    icon = icon,
    done = granted,
    required = required,
    actionLabel = actionLabel,
    onAction = onRequest
)
