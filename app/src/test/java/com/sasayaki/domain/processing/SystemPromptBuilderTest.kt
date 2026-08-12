package com.sasayaki.domain.processing

import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.OutputStyle
import com.sasayaki.domain.model.PostProcessingPrompt
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.SummarizeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline evals for prompt assembly.
 *
 * These make no network calls and assert nothing about model output quality; they check
 * that the prompt we send is well-formed, correctly ordered, and free of directives that
 * contradict each other. Quality of the resulting text is measured by the separate
 * LLM-in-the-loop suite.
 */
class SystemPromptBuilderTest {

    private fun profile(
        outputStyle: OutputStyle = OutputStyle.STANDARD,
        rewriteMode: RewriteMode = RewriteMode.FIX,
        summarizeMode: SummarizeMode = SummarizeMode.NONE,
        emojiAllowed: Boolean = false,
        profilePrompt: String = "",
        language: String? = null,
        selectedPromptIds: Set<Long> = emptySet()
    ) = Profile(
        name = "Test",
        outputStyle = outputStyle,
        rewriteMode = rewriteMode,
        summarizeMode = summarizeMode,
        emojiAllowed = emojiAllowed,
        profilePrompt = profilePrompt,
        language = language,
        selectedPromptIds = selectedPromptIds
    )

    private val builtIn = PostProcessingPrompt(id = 1, title = "Role", prompt = "BUILT_IN_ONE", builtIn = true)
    private val custom = PostProcessingPrompt(id = 2, title = "Mine", prompt = "CUSTOM_ONE", builtIn = false)

    private fun allStyleCombinations(): List<Profile> =
        OutputStyle.entries.flatMap { style ->
            RewriteMode.entries.flatMap { rewrite ->
                SummarizeMode.entries.flatMap { summarize ->
                    listOf(true, false).map { emoji ->
                        profile(style, rewrite, summarize, emoji)
                    }
                }
            }
        }

    // ---- Contradiction sweep -------------------------------------------------------

    /**
     * Phrases that must never appear together. "Do not paraphrase" alongside "return a
     * short summary" is the regression this guards: the two were emitted verbatim in the
     * same sentence, leaving the model to pick one at random.
     */
    private val mutuallyExclusive = listOf(
        "keep the speaker's wording as-is" to "reduce to a short summary",
        "keep the speaker's wording as-is" to "cut repetition",
        "keep the speaker's wording as-is" to "rewrite into",
        "keep every point the speaker made" to "reduce to a short summary",
        "keep every point the speaker made" to "cut repetition",
        "use standard capitalisation and punctuation." to "write in all lowercase",
        "do not add emoji" to "you may add at most one",
        "do not condense" to "drop supporting detail"
    )

    @Test
    fun `no style combination emits contradictory directives`() {
        allStyleCombinations().forEach { p ->
            val prompt = SystemPromptBuilder.build(p, null, listOf(builtIn)).lowercase()
            mutuallyExclusive.forEach { (a, b) ->
                assertFalse(
                    "Contradiction for style=${p.outputStyle} rewrite=${p.rewriteMode} " +
                        "summarize=${p.summarizeMode} emoji=${p.emojiAllowed}: " +
                        "both \"$a\" and \"$b\" present",
                    prompt.contains(a) && prompt.contains(b)
                )
            }
        }
    }

    @Test
    fun `no-rewrite becomes extractive rather than contradictory when condensing`() {
        val prompt = SystemPromptBuilder.build(
            profile(rewriteMode = RewriteMode.NONE, summarizeMode = SummarizeMode.HARD),
            null,
            listOf(builtIn)
        )
        assertTrue(
            "Expected the extractive phrasing when NONE is combined with summarising",
            prompt.contains("drop material rather than rephrasing it")
        )
        assertFalse(
            "The literal no-change wording must not survive alongside summarisation",
            prompt.contains("change nothing beyond the guidance above")
        )
    }

    @Test
    fun `every style combination emits exactly one rule of each kind`() {
        allStyleCombinations().forEach { p ->
            val prompt = SystemPromptBuilder.build(p, null, listOf(builtIn))
            listOf("- Casing and punctuation:", "- Rewriting:", "- Length:", "- Emoji:").forEach { marker ->
                assertEquals(
                    "Expected exactly one \"$marker\" for $p",
                    1,
                    prompt.split(marker).size - 1
                )
            }
        }
    }

    // ---- Ordering and precedence ---------------------------------------------------

    @Test
    fun `output rules come after all guidance and the final instruction is last`() {
        val prompt = SystemPromptBuilder.build(
            profile(profilePrompt = "PROFILE_ONE", language = "Spanish", selectedPromptIds = setOf(2L)),
            AppContext(label = "Gmail", packageName = "com.google.android.gm"),
            listOf(builtIn, custom)
        )
        val rulesAt = prompt.indexOf(SystemPromptBuilder.PRECEDENCE_HEADER)
        assertTrue("Output rules missing", rulesAt >= 0)
        listOf("BUILT_IN_ONE", "PROFILE_ONE", "Spanish", "Target app: Gmail", "CUSTOM_ONE").forEach {
            val at = prompt.indexOf(it)
            assertTrue("Missing guidance fragment: $it", at >= 0)
            assertTrue("Guidance \"$it\" must precede the output rules", at < rulesAt)
        }
        assertTrue(
            "The final instruction must be the last line",
            prompt.trimEnd().endsWith(SystemPromptBuilder.FINAL_INSTRUCTION)
        )
    }

    @Test
    fun `app style hint defers to the output rules`() {
        val prompt = SystemPromptBuilder.build(
            profile(outputStyle = OutputStyle.MINIMAL),
            AppContext(label = "Gmail", packageName = "com.google.android.gm"),
            listOf(builtIn)
        )
        // The hint asks for a professional tone while the style demands lowercase; the
        // prompt has to say which wins rather than leaving both standing.
        assertTrue(prompt.contains("App style hint:"))
        assertTrue(prompt.contains("the output rules below always win"))
        assertTrue(prompt.contains("Write in all lowercase"))
    }

    // ---- Fragment inclusion --------------------------------------------------------

    @Test
    fun `built-ins always apply and custom prompts only when selected`() {
        val unselected = SystemPromptBuilder.build(profile(), null, listOf(builtIn, custom))
        assertTrue("Built-in prompts must always apply", unselected.contains("BUILT_IN_ONE"))
        assertFalse("Unselected custom prompt must not appear", unselected.contains("CUSTOM_ONE"))

        val selected = SystemPromptBuilder.build(
            profile(selectedPromptIds = setOf(2L)),
            null,
            listOf(builtIn, custom)
        )
        assertTrue(selected.contains("CUSTOM_ONE"))
    }

    @Test
    fun `optional fragments are omitted when empty`() {
        val prompt = SystemPromptBuilder.build(profile(), AppContext(null, null), listOf(builtIn))
        assertFalse("Blank app context must not emit a Context block", prompt.contains("Context:"))
        assertFalse("Null language must not emit a language line", prompt.contains("is dictating in"))
    }

    @Test
    fun `falls back to a task instruction when no guidance exists`() {
        val prompt = SystemPromptBuilder.build(profile(), null, emptyList())
        assertTrue(prompt.contains("Clean the raw dictation"))
        assertTrue(prompt.contains(SystemPromptBuilder.PRECEDENCE_HEADER))
    }

    @Test
    fun `blank prompt text is dropped rather than emitting empty lines`() {
        val blank = PostProcessingPrompt(id = 3, title = "Blank", prompt = "   ", builtIn = true)
        val prompt = SystemPromptBuilder.build(profile(), null, listOf(builtIn, blank))
        assertFalse("Prompt must not contain blank lines", prompt.lines().any { it.isBlank() })
    }
}
