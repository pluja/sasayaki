package com.sasayaki.service

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rules for deciding what an input field already contains before a dictation is appended.
 *
 * Getting this wrong is expensive in both directions: treat real text as a placeholder and
 * the user's typing is destroyed, treat a placeholder as real text and the placeholder ends
 * up inside the message.
 */
class TextInjectionTest {

    @Test
    fun `platform hint flag means the field is empty`() {
        assertEquals(
            "",
            resolveExistingText(rawText = "Message", hintText = "Message", isShowingHintText = true)
        )
    }

    @Test
    fun `hint reported as text is treated as empty`() {
        assertEquals(
            "",
            resolveExistingText(rawText = "Message", hintText = "Message", isShowingHintText = false)
        )
    }

    @Test
    fun `hint match ignores case and padding`() {
        assertEquals(
            "",
            resolveExistingText(rawText = " message ", hintText = "Message", isShowingHintText = false)
        )
    }

    @Test
    fun `real text is kept when a hint exists`() {
        assertEquals(
            "see you at 8",
            resolveExistingText(rawText = "see you at 8", hintText = "Message", isShowingHintText = false)
        )
    }

    @Test
    fun `real text is kept when there is no hint at all`() {
        assertEquals(
            "see you at 8",
            resolveExistingText(rawText = "see you at 8", hintText = "", isShowingHintText = false)
        )
    }

    /**
     * Guards the heuristic rejected from #6: short text in a field reporting no cursor was
     * to be treated as a placeholder. Editors that report no selection while holding real
     * text are common, and the rule would have wiped whatever the user had typed.
     */
    @Test
    fun `short text is kept regardless of length`() {
        listOf("ok", "see you at 8", "a".repeat(63), "a".repeat(64))
            .forEach { typed ->
                assertEquals(
                    "Text of length ${typed.length} must survive",
                    typed,
                    resolveExistingText(rawText = typed, hintText = "Message", isShowingHintText = false)
                )
            }
    }

    @Test
    fun `blank hint never blanks the field`() {
        assertEquals(
            "   ",
            resolveExistingText(rawText = "   ", hintText = "   ", isShowingHintText = false)
        )
    }

    @Test
    fun `leading space is dropped only into an empty field`() {
        assertEquals("hello", insertionFor(existingText = "", dictated = " hello"))
        assertEquals(" hello", insertionFor(existingText = "hi", dictated = " hello"))
    }

    @Test
    fun `leading space is dropped only at the start of the field`() {
        assertEquals("hello", insertionAtCursor(charsBeforeCursor = 0, dictated = " hello"))
        assertEquals(" hello", insertionAtCursor(charsBeforeCursor = 1, dictated = " hello"))
        assertEquals("hello", insertionAtCursor(charsBeforeCursor = 1, dictated = "hello"))
        // An unanswered request must not read as an empty field.
        assertEquals(" hello", insertionAtCursor(charsBeforeCursor = null, dictated = " hello"))
    }

    @Test
    fun `password editors are refused whatever their class`() {
        listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        ).forEach { inputType ->
            assertTrue("inputType $inputType must be refused", isSensitiveInputType(inputType))
        }
    }

    /**
     * The variation bits are only meaningful within a class, and the password variations of
     * text and number collide numerically with ordinary variations of the other classes.
     */
    @Test
    fun `ordinary editors are accepted`() {
        listOf(
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
        ).forEach { inputType ->
            assertFalse("inputType $inputType must be accepted", isSensitiveInputType(inputType))
        }
    }
}
