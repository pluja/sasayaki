package com.sasayaki.service

import org.junit.Assert.assertEquals
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
}
