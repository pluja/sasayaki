package com.sasayaki.ui.common

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sasayaki.service.TextInjectorService

data class PermissionStatus(
    val name: String,
    val description: String,
    val granted: Boolean,
    val actionLabel: String,
    val onRequest: () -> Unit
)

@Composable
fun PermissionCard(status: PermissionStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (status.granted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = status.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = status.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (status.granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = if (status.granted) "Granted" else "Not granted",
                    tint = if (status.granted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            if (!status.granted) {
                Button(onClick = status.onRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(status.actionLabel)
                }
            }
        }
    }
}

fun checkOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

fun requestOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun checkAccessibilityPermission(context: Context): Boolean {
    val expectedService = ComponentName(context, TextInjectorService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.split(':').any { it.equals(expectedService, ignoreCase = true) }
}

fun requestAccessibilityPermission(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
fun rememberOverlayPermissionState(): PermissionStatus {
    val context = LocalContext.current

    val granted = rememberExternalPermissionGranted { checkOverlayPermission(it) }

    return PermissionStatus(
        name = "Display over other apps",
        description = "Shows the floating dictation bubble on top of the app you are using.",
        granted = granted,
        actionLabel = "Open overlay settings",
        onRequest = { requestOverlayPermission(context) }
    )
}

@Composable
fun rememberAccessibilityPermissionState(): PermissionStatus {
    val context = LocalContext.current

    val granted = rememberExternalPermissionGranted { checkAccessibilityPermission(it) }

    return PermissionStatus(
        name = "Direct text insertion",
        description = "Allows Sasayaki to insert dictated text into the focused field in other apps.",
        granted = granted,
        actionLabel = "Open accessibility settings",
        onRequest = { requestAccessibilityPermission(context) }
    )
}

@Composable
fun rememberMicrophonePermissionState(): PermissionStatus {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.hasPermission(Manifest.permission.RECORD_AUDIO))
    }

    observeOnResume {
        granted = context.hasPermission(Manifest.permission.RECORD_AUDIO)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted = isGranted }

    return PermissionStatus(
        name = "Microphone",
        description = "Required to record your voice before sending it to transcription.",
        granted = granted,
        actionLabel = "Allow microphone access",
        onRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    )
}

@Composable
fun rememberNotificationPermissionState(): PermissionStatus {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        )
    }

    observeOnResume {
        granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted = isGranted }

    return PermissionStatus(
        name = "Notifications",
        description = "Keeps the foreground service visible so Android does not stop it unexpectedly.",
        granted = granted,
        actionLabel = "Allow notifications",
        onRequest = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    )
}

@Composable
private fun rememberExternalPermissionGranted(
    checkPermission: (Context) -> Boolean
): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(checkPermission(context)) }

    observeOnResume {
        granted = checkPermission(context)
    }

    return granted
}

@Composable
private fun observeOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResume()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

private fun Context.hasPermission(permission: String): Boolean {
    return checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
