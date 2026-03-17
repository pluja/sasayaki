package com.sasayaki.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class TextInjectorService : AccessibilityService() {
    interface KeyboardListener {
        fun onKeyboardVisibilityChanged(visible: Boolean)
    }

    companion object {
        private const val TAG = "TextInjectorService"

        var instance: TextInjectorService? = null
            private set

        var keyboardListener: KeyboardListener? = null
        var isKeyboardVisible: Boolean = false
            private set

        /** Package name of the app that currently has a focused editable field */
        var focusedAppPackage: String? = null
            private set

        private val BANKING_KEYWORDS = listOf("bank", "pay", "wallet", "venmo", "zelle")
    }

    private var lastFocusedNode: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val source = event.source ?: return
                if (source.isEditable && !isSensitiveField(source)) {
                    lastFocusedNode = source
                    focusedAppPackage = source.packageName?.toString()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                checkKeyboardVisibility()
            }
        }
    }

    private fun checkKeyboardVisibility() {
        try {
            val hasIme = windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            if (hasIme != isKeyboardVisible) {
                isKeyboardVisible = hasIme
                Log.d(TAG, "Keyboard visibility changed: $hasIme")
                keyboardListener?.onKeyboardVisibilityChanged(hasIme)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking keyboard", e)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        lastFocusedNode = null
        isKeyboardVisible = false
        keyboardListener = null
        focusedAppPackage = null
        super.onDestroy()
    }

    /** Resolve a package name to a human-readable app label */
    fun getAppName(packageName: String?): String? {
        packageName ?: return null
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun injectText(text: String): Boolean {
        val node = lastFocusedNode ?: run {
            Log.w(TAG, "No focused editable node")
            return false
        }
        try {
            if (!node.isEditable || isSensitiveField(node)) {
                Log.w(TAG, "Node not editable or sensitive")
                return false
            }

            // Refresh to get current state
            val refreshed = node.refresh()
            if (!refreshed) {
                Log.w(TAG, "Could not refresh node, trying anyway")
            }

            val rawText = node.text?.toString() ?: ""
            val hintText = node.hintText?.toString() ?: ""

            // node.text returns the hint/placeholder when the field is empty in many apps.
            // If the text exactly matches the hint, treat the field as empty.
            val existingText = if (rawText == hintText) "" else rawText

            val cursorPos = if (existingText.isNotEmpty() && node.textSelectionEnd >= 0) {
                node.textSelectionEnd.coerceAtMost(existingText.length)
            } else {
                existingText.length
            }

            val newText = buildString {
                append(existingText.substring(0, cursorPos))
                append(text)
                append(existingText.substring(cursorPos))
            }

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            val setResult = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "ACTION_SET_TEXT result=$setResult, text='${text.take(50)}'")

            if (setResult) {
                val newCursorPos = cursorPos + text.length
                val selectionArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursorPos)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursorPos)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
            }

            return setResult
        } catch (e: Exception) {
            Log.e(TAG, "Text injection failed", e)
            return false
        }
    }

    private fun isSensitiveField(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true
        val packageName = node.packageName?.toString()?.lowercase() ?: return false
        return BANKING_KEYWORDS.any { packageName.contains(it) }
    }
}
