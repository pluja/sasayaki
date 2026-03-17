package com.sasayaki.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

sealed class InjectionResult {
    data object Success : InjectionResult()
    data object NoFocusedNode : InjectionResult()
    data object BlockedSensitive : InjectionResult()
    data object Failed : InjectionResult()
}

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

        var focusedAppPackage: String? = null
            private set

        private val BANKING_KEYWORDS = listOf("bank", "pay", "wallet", "venmo", "zelle")
    }

    private var lastFocusedPackage: String? = null

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
                if (source.isEditable) {
                    lastFocusedPackage = source.packageName?.toString()
                    focusedAppPackage = lastFocusedPackage
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
                keyboardListener?.onKeyboardVisibilityChanged(hasIme)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking keyboard", e)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        lastFocusedPackage = null
        isKeyboardVisible = false
        keyboardListener = null
        focusedAppPackage = null
        super.onDestroy()
    }

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

    fun injectText(text: String): InjectionResult {
        val node = findCurrentFocusedEditable() ?: return InjectionResult.NoFocusedNode
        try {
            if (isSensitiveField(node)) {
                return InjectionResult.BlockedSensitive
            }

            val rawText = node.text?.toString() ?: ""
            val hintText = node.hintText?.toString() ?: ""
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

            if (setResult) {
                val newCursorPos = cursorPos + text.length
                val selectionArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursorPos)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursorPos)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
            }

            return if (setResult) InjectionResult.Success else InjectionResult.Failed
        } catch (e: Exception) {
            Log.e(TAG, "Text injection failed", e)
            return InjectionResult.Failed
        }
    }

    private fun findCurrentFocusedEditable(): AccessibilityNodeInfo? {
        return try {
            val rootNode = rootInActiveWindow ?: return null
            val focused = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                focused
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding focused editable", e)
            null
        }
    }

    private fun isSensitiveField(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true
        val packageName = node.packageName?.toString()?.lowercase() ?: return false
        return BANKING_KEYWORDS.any { packageName.contains(it) }
    }
}
