package com.sasayaki.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.sasayaki.R
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.domain.model.AppContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextInjectionBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataStore: PreferencesDataStore
) {
    companion object {
        private const val TAG = "TextInjectionBridge"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun inject(text: String): Boolean {
        val injector = TextInjectorService.instance
        if (injector != null) {
            val injectionResult = injector.injectText(text)
            if (injectionResult is InjectionResult.Success) return true

            if (injectionResult is InjectionResult.BlockedSensitive) {
                Log.d(TAG, "Injection blocked: sensitive field")
                mainHandler.post {
                    Toast.makeText(context, "Dictation blocked in sensitive field", Toast.LENGTH_SHORT).show()
                }
                return false
            }
        }

        val autoClipboard = preferencesDataStore.preferences.first().autoClipboard
        if (autoClipboard) {
            copyToClipboard(text)
        } else {
            mainHandler.post {
                Toast.makeText(context, "Could not inject text (clipboard fallback disabled)", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    val isAccessibilityServiceActive: Boolean
        get() = TextInjectorService.instance != null

    val focusedAppName: String?
        get() = currentAppContext?.displayName

    val currentAppContext: AppContext?
        get() {
            val injector = TextInjectorService.instance ?: return null
            val packageName = injector.getFocusedAppPackageName()?.takeIf { isUsableTargetPackage(it) }
                ?: return null
            val appLabel = injector.getAppName(packageName)?.takeIf { isMeaningfulLabel(it) }
                ?: labelFromPackage(packageName)
            return AppContext(label = appLabel, packageName = packageName)
        }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("dictation", text))
        mainHandler.post {
            Toast.makeText(context, R.string.clipboard_fallback_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isUsableTargetPackage(packageName: String): Boolean {
        val trimmed = packageName.trim()
        return trimmed.isNotBlank() && trimmed != context.packageName
    }

    private fun isMeaningfulLabel(label: String): Boolean {
        val trimmed = label.trim()
        return trimmed.isNotBlank() && !trimmed.equals("App", ignoreCase = true)
    }

    private fun labelFromPackage(packageName: String): String? {
        val ignoredSegments = setOf(
            "android",
            "app",
            "apps",
            "client",
            "com",
            "debug",
            "im",
            "io",
            "mobile",
            "net",
            "org",
            "release",
            "x"
        )
        val segment = packageName.split('.')
            .firstOrNull { part ->
                val normalized = part.lowercase()
                normalized.length > 1 && normalized !in ignoredSegments
            }
            ?: packageName.substringAfterLast('.').takeIf { it.isNotBlank() }
        return segment?.replace('_', ' ')?.replace('-', ' ')?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.split(' ')
            ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}
