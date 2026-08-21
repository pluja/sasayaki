package com.sasayaki.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi

/**
 * What the field already holds, ignoring placeholder text.
 *
 * Some apps report their placeholder through
 * [android.view.accessibility.AccessibilityNodeInfo.getText] rather than leaving the field
 * empty. [isShowingHintText] is the platform's own answer and is trusted first; the hint
 * comparison catches apps that pad or re-case the hint before reporting it. An app that
 * reports a placeholder while reporting no hint at all cannot be caught here, because
 * nothing distinguishes it from an app holding a draft of the same words.
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

/**
 * A null count is the editor declining to answer rather than an empty field, so the
 * speaker's spacing is left alone rather than guessed at.
 */
internal fun insertionAtCursor(charsBeforeCursor: Int?, dictated: String): String =
    if (charsBeforeCursor == 0) dictated.trimStart() else dictated

/**
 * [AccessibilityNodeInfo.isPassword] is set from the view, and web password inputs reach the
 * accessibility tree as ordinary editables, so the editor's own input type is checked too.
 */
internal fun isSensitiveInputType(inputType: Int): Boolean {
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT ->
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}

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
    private var lastFocusedEditablePackage: String? = null

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
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                commitAtCursor(text)?.let { return it }
            }

            return setWholeFieldText(node, text)
        } catch (e: Exception) {
            Log.e(TAG, "Text injection failed", e)
            return InjectionResult.Failed
        }
    }

    /**
     * Inserts at the cursor through the input connection, which never reads the field, so an
     * app reporting its placeholder as the node's text cannot leak it into the dictation.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun commitAtCursor(text: String): InjectionResult? {
        val method = inputMethod ?: return null
        if (!method.currentInputStarted) return null
        val connection = method.currentInputConnection ?: return null

        // An editor that will not describe itself cannot be told apart from a password
        // field, so the node path takes over rather than committing unchecked.
        val editorInfo = method.currentInputEditorInfo ?: return null
        if (isSensitiveInputType(editorInfo.inputType) || isBankingPackage(editorInfo.packageName)) {
            return InjectionResult.BlockedSensitive
        }

        // Asking for a single character keeps a long draft from crossing processes on
        // every dictation; only whether anything precedes the cursor matters.
        val charsBeforeCursor = connection.getSurroundingText(1, 0, 0)?.selectionStart
        connection.commitText(insertionAtCursor(charsBeforeCursor, text), 1, null)
        return InjectionResult.Success
    }

    /**
     * Rewrites the whole field, used below Android 13 and whenever no editor session is
     * running.
     */
    private fun setWholeFieldText(node: AccessibilityNodeInfo, text: String): InjectionResult {
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

    private fun isSensitiveField(node: AccessibilityNodeInfo): Boolean =
        node.isPassword || isBankingPackage(node.packageName?.toString())

    private fun isBankingPackage(packageName: CharSequence?): Boolean {
        val normalized = packageName?.toString()?.lowercase() ?: return false
        return BANKING_KEYWORDS.any { normalized.contains(it) }
    }
}
