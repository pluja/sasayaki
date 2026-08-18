package com.sasayaki.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * What the field already holds, ignoring placeholder text.
 *
 * Some apps report their placeholder through [android.view.accessibility.AccessibilityNodeInfo.getText]
 * rather than leaving it empty, so appending a dictation to it would paste the placeholder
 * into the message. [isShowingHintText] is the platform's own answer and is trusted first;
 * the hint comparison catches apps that pad or re-case the hint before reporting it.
 *
 * Field length and cursor position are deliberately not consulted. Plenty of editors,
 * WebView- and Flutter-backed ones especially, report no text selection while holding real
 * typed text, and discarding it on that basis would delete what the user wrote.
 */
internal fun resolveExistingText(
    rawText: String,
    hintText: String,
    isShowingHintText: Boolean
): String {
    val showsPlaceholder = isShowingHintText ||
        (hintText.isNotBlank() && rawText.trim().equals(hintText.trim(), ignoreCase = true))
    return if (showsPlaceholder) "" else rawText
}

/**
 * Leading space is dropped when the field is empty, since it would open the message with
 * whitespace. Mid-message the spacing is the speaker's to decide.
 */
internal fun insertionFor(existingText: String, dictated: String): String =
    if (existingText.isEmpty()) dictated.trimStart() else dictated

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

        private val PROBE_DELAYS_MS = longArrayOf(200, 600, 1200)
        private const val DEBOUNCE_MS = 120L
        private const val RETRY_DELAY_MS = 300L

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
    private var lastFocusedEditablePackage: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val debounceCheck = Runnable { checkKeyboardVisibility() }
    private val imeProbe = Runnable { checkKeyboardVisibility() }
    private var pendingRetry = false

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
                val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() }
                if (packageName != null) {
                    lastFocusedPackage = packageName
                    focusedAppPackage = packageName
                    if (event.source?.isEditable == true) {
                        lastFocusedEditablePackage = packageName
                        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                            scheduleImeProbe()
                        }
                    } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                        cancelAllProbes()
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                scheduleDebouncedCheck()
            }
        }
    }

    private fun scheduleDebouncedCheck() {
        handler.removeCallbacks(debounceCheck)
        handler.postDelayed(debounceCheck, DEBOUNCE_MS)
    }

    private fun scheduleImeProbe() {
        cancelAllProbes()
        PROBE_DELAYS_MS.forEach { delay ->
            handler.postDelayed(imeProbe, delay)
        }
    }

    private fun cancelAllProbes() {
        handler.removeCallbacks(imeProbe)
    }

    private fun checkKeyboardVisibility() {
        try {
            val ws = windows
            if (ws.isNullOrEmpty()) {
                scheduleRetry()
                return
            }
            pendingRetry = false
            val hasIme = ws.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            if (hasIme != isKeyboardVisible) {
                isKeyboardVisible = hasIme
                keyboardListener?.onKeyboardVisibilityChanged(hasIme)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking keyboard", e)
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (pendingRetry) return
        pendingRetry = true
        handler.postDelayed({
            pendingRetry = false
            checkKeyboardVisibility()
        }, RETRY_DELAY_MS)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        instance = null
        lastFocusedPackage = null
        lastFocusedEditablePackage = null
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

    fun getFocusedAppPackageName(): String? {
        return findCurrentFocusedEditable()?.packageName?.toString()
            ?: lastFocusedEditablePackage
            ?: focusedAppPackage
            ?: lastFocusedPackage
            ?: rootInActiveWindow?.packageName?.toString()
    }

    fun injectText(text: String): InjectionResult {
        val node = findCurrentFocusedEditable() ?: return InjectionResult.NoFocusedNode
        try {
            if (isSensitiveField(node)) {
                return InjectionResult.BlockedSensitive
            }

            val existingText = resolveExistingText(
                rawText = node.text?.toString() ?: "",
                hintText = node.hintText?.toString() ?: "",
                isShowingHintText = node.isShowingHintText
            )

            val cursorPos = if (existingText.isNotEmpty() && node.textSelectionEnd >= 0) {
                node.textSelectionEnd.coerceAtMost(existingText.length)
            } else {
                existingText.length
            }

            val textToInsert = insertionFor(existingText, text)

            val newText = buildString {
                append(existingText.substring(0, cursorPos))
                append(textToInsert)
                append(existingText.substring(cursorPos))
            }

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            val setResult = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            if (setResult) {
                val newCursorPos = cursorPos + textToInsert.length
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
