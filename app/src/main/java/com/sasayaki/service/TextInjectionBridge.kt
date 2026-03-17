package com.sasayaki.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.sasayaki.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextInjectionBridge @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TextInjectionBridge"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun inject(text: String): Boolean {
        val injector = TextInjectorService.instance
        if (injector != null) {
            val result = injector.injectText(text)
            Log.d(TAG, "Accessibility injection result: $result")
            if (result) return true
        } else {
            Log.d(TAG, "No accessibility service, using clipboard fallback")
        }

        // Clipboard fallback
        copyToClipboard(text)
        return false
    }

    val isAccessibilityServiceActive: Boolean
        get() = TextInjectorService.instance != null

    /** Get the human-readable name of the app that currently has focus */
    val focusedAppName: String?
        get() {
            val injector = TextInjectorService.instance ?: return null
            return injector.getAppName(TextInjectorService.focusedAppPackage)
        }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("dictation", text))
        // Toast must be on main thread
        mainHandler.post {
            Toast.makeText(context, R.string.clipboard_fallback_toast, Toast.LENGTH_SHORT).show()
        }
    }
}
