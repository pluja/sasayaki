package com.sasayaki.ui.common

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sasayaki.R
import com.sasayaki.service.TextInjectorService

data class PermissionStatus(
    val name: String,
    val description: String,
    val granted: Boolean,
    val actionLabel: String,
    val onRequest: () -> Unit
)

fun checkOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

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

/**
 * isRestrictedSettingsLikely reports whether Android 13+ probably blocks accessibility
 * for this app ("restricted settings"). Apps cannot read the block itself (its app-op is
 * privileged), so this infers it from the app having been installed from a file, which
 * is when Android applies it.
 */
fun isRestrictedSettingsLikely(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val source = try {
        context.packageManager.getInstallSourceInfo(context.packageName).packageSource
    } catch (_: Exception) {
        // A guess must never take the screen down with it.
        return false
    }
    return source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
        source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
}

@Composable
fun rememberRestrictedSettingsLikely(): Boolean = rememberCheckedOnResume { isRestrictedSettingsLikely(it) }

fun openAppInfo(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
fun rememberVoiceInputEnabled(): Boolean = rememberCheckedOnResume { context ->
    val inputMethods = context.getSystemService(InputMethodManager::class.java)
    inputMethods.enabledInputMethodList.any { it.packageName == context.packageName }
}

fun openKeyboardSettings(context: Context) {
    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun requestAccessibilityPermission(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
fun rememberOverlayPermissionState(): PermissionStatus {
    val context = LocalContext.current

    val granted = rememberCheckedOnResume { checkOverlayPermission(it) }

    return PermissionStatus(
        name = stringResource(R.string.permission_overlay_name),
        description = stringResource(R.string.permission_overlay_description),
        granted = granted,
        actionLabel = stringResource(R.string.action_allow),
        onRequest = { requestOverlayPermission(context) }
    )
}

@Composable
fun rememberAccessibilityPermissionState(): PermissionStatus {
    val context = LocalContext.current

    val granted = rememberCheckedOnResume { checkAccessibilityPermission(it) }

    return PermissionStatus(
        name = stringResource(R.string.permission_accessibility_name),
        description = stringResource(R.string.permission_accessibility_description),
        granted = granted,
        actionLabel = stringResource(R.string.action_allow),
        onRequest = { requestAccessibilityPermission(context) }
    )
}

@Composable
fun rememberMicrophonePermissionState(): PermissionStatus = rememberRuntimePermissionState(
    permission = Manifest.permission.RECORD_AUDIO,
    name = stringResource(R.string.permission_microphone_name),
    description = stringResource(R.string.permission_microphone_description)
)

@Composable
fun rememberNotificationPermissionState(): PermissionStatus {
    val name = stringResource(R.string.permission_notifications_name)
    val description = stringResource(R.string.permission_notifications_description)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return PermissionStatus(name, description, granted = true, actionLabel = "", onRequest = {})
    }
    return rememberRuntimePermissionState(Manifest.permission.POST_NOTIFICATIONS, name, description)
}

/**
 * rememberRuntimePermissionState sends the person to App info once Android has stopped
 * showing the permission dialog (after two denials), where "Allow" would otherwise do nothing.
 */
@Composable
private fun rememberRuntimePermissionState(permission: String, name: String, description: String): PermissionStatus {
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun isBlocked(): Boolean = activity != null &&
        !context.hasPermission(permission) &&
        PermissionRequests.wasRequested(context, permission) &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    var granted by remember { mutableStateOf(context.hasPermission(permission)) }
    var blocked by remember { mutableStateOf(isBlocked()) }
    observeOnResume {
        granted = context.hasPermission(permission)
        blocked = isBlocked()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
        blocked = isBlocked()
    }

    return PermissionStatus(
        name = name,
        description = if (blocked) stringResource(R.string.permission_blocked_description) else description,
        granted = granted,
        actionLabel = stringResource(if (blocked) R.string.action_open_settings else R.string.action_allow),
        onRequest = {
            if (blocked) {
                openAppInfo(context)
            } else {
                PermissionRequests.markRequested(context, permission)
                launcher.launch(permission)
            }
        }
    )
}

// PermissionRequests remembers which permissions were asked for, because Android reports a
// permission that was never asked and one it no longer asks for in the same way.
private object PermissionRequests {
    private const val FILE = "permission_requests"

    fun wasRequested(context: Context, permission: String): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(permission, false)

    fun markRequested(context: Context, permission: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(permission, true) }
    }
}

@Composable
private fun rememberCheckedOnResume(checkPermission: (Context) -> Boolean): Boolean {
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

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
