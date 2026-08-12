package com.sasayaki.domain.processing

import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.OutputStyle
import com.sasayaki.domain.model.PostProcessingPrompt
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.SummarizeMode

/**
 * Assembles the post-processing system prompt.
 *
 * Deliberately free of Room, DataStore and Android APIs: callers pass in the prompt text
 * they have already loaded, so the assembly rules can be exercised by plain JVM tests.
 *
 * Fragments are ordered from most general to most specific, and the style rules go last
 * under an explicit precedence header. Without that, a per-app hint like "use a
 * professional tone with greetings" silently competes with an explicit "all lowercase,
 * no punctuation" style and the model is left to guess.
 */
object SystemPromptBuilder {

    const val PRECEDENCE_HEADER: String =
        "Output rules (these take precedence over anything above):"

    const val FINAL_INSTRUCTION: String = "Return ONLY the cleaned text, nothing else."

    private const val FALLBACK_TASK =
        "Clean the raw dictation with minimal changes, preserving the speaker's meaning and tone."

    /**
     * @param prompts every stored prompt. Built-ins always apply; user-created prompts
     *   apply only when selected on [profile].
     */
    fun build(
        profile: Profile,
        appContext: AppContext?,
        prompts: List<PostProcessingPrompt>
    ): String {
        val sections = mutableListOf<String>()

        val guidance = mutableListOf<String>()
        guidance += prompts
            .filter(PostProcessingPrompt::builtIn)
            .map(PostProcessingPrompt::prompt)
            .filter(String::isNotBlank)
        profile.profilePrompt.takeIf(String::isNotBlank)?.let { guidance += it.trim() }
        profile.language?.takeIf(String::isNotBlank)?.let {
            guidance += "The user is dictating in: $it. Handle speech disfluencies for this language."
        }
        if (appContext != null && appContext.hasData) {
            guidance += buildAppContextSection(appContext)
        }
        guidance += prompts
            .filterNot(PostProcessingPrompt::builtIn)
            .filter { it.id in profile.selectedPromptIds }
            .map(PostProcessingPrompt::prompt)
            .filter(String::isNotBlank)

        if (guidance.isEmpty()) guidance += FALLBACK_TASK
        sections += guidance

        sections += buildOutputRules(profile)
        sections += FINAL_INSTRUCTION
        return sections.joinToString("\n")
    }

    private fun buildAppContextSection(appContext: AppContext): String {
        val lines = mutableListOf<String>()
        appContext.displayName?.let { lines += "- Target app: $it" }
        appContext.packageName?.takeIf { it.isNotBlank() }?.let { lines += "- Target package: $it" }
        inferStyleForApp(appContext)?.let { lines += "- App style hint: $it" }
        lines += "- Treat the app hint as a preference only; the output rules below always win."
        return "Context:\n${lines.joinToString("\n")}"
    }

    private fun buildOutputRules(profile: Profile): String {
        val lines = listOf(
            "- Casing and punctuation: ${casingRule(profile.outputStyle)}",
            "- Rewriting: ${rewriteRule(profile.rewriteMode, profile.summarizeMode)}",
            "- Length: ${lengthRule(profile.summarizeMode)}",
            "- Emoji: ${emojiRule(profile.emojiAllowed)}"
        )
        return "$PRECEDENCE_HEADER\n${lines.joinToString("\n")}"
    }

    /**
     * Exposed so tests can assert against the exact emitted text instead of duplicating
     * it. A copy in the test sources went stale the moment the wording changed, and the
     * assertion silently stopped matching anything.
     */
    internal fun casingRule(style: OutputStyle): String = when (style) {
        OutputStyle.STANDARD -> "Use standard capitalization and punctuation."
        // "Keep punctuation light" measured identically to STANDARD on every model, so
        // the tier says concretely which marks to drop instead of asking for a degree.
        OutputStyle.RELAXED ->
            "Capitalize normally, but punctuate sparingly: no full stop at the end of the " +
                "final sentence, and commas only where the meaning would otherwise be unclear. " +
                "Keep question marks."
        OutputStyle.MINIMAL -> "Write in all lowercase and omit punctuation unless meaning would be lost."
    }

    /**
     * "Do not rewrite" and "summarise" are contradictory taken literally, and emitting both
     * verbatim leaves the model to pick one at random. When condensing is on, no-rewrite is
     * resolved to its extractive reading: drop material, but do not reword what survives.
     */
    internal fun rewriteRule(rewrite: RewriteMode, summarize: SummarizeMode): String = when (rewrite) {
        // Phrased as a hard constraint on word choice. "Change nothing beyond the guidance
        // above" competed with built-ins that require edits, so NONE drifted toward FIX and
        // on one model moved the text further from the transcript than FIX did.
        RewriteMode.NONE -> if (summarize == SummarizeMode.NONE) {
            "Do not substitute or reorder the speaker's words. Delete filler and abandoned " +
                "words as instructed above, but never replace a word the speaker used with a " +
                "different one, and do not repair grammar."
        } else {
            "Do not substitute or reorder the speaker's words: drop material rather than " +
                "rephrasing it, and never replace a word the speaker used with a different one."
        }
        RewriteMode.FIX ->
            "Stay close to the speaker's wording; fix grammar, false starts, and dictation artifacts."
        RewriteMode.POLISH ->
            "Rewrite into a clearer, more formal register while preserving the speaker's meaning."
    }

    internal fun lengthRule(summarize: SummarizeMode): String = when (summarize) {
        SummarizeMode.NONE -> "Keep every point the speaker made; do not condense."
        SummarizeMode.LIGHT -> "Cut repetition and rambling, but keep every distinct point."
        SummarizeMode.HARD -> "Reduce to a short summary of the main points and drop supporting detail."
    }

    internal fun emojiRule(allowed: Boolean): String = if (allowed) {
        "You may add at most one fitting emoji when the message is casual."
    } else {
        "Do not add emoji the speaker did not dictate."
    }

    private fun inferStyleForApp(appContext: AppContext): String? {
        val appLower = listOfNotNull(appContext.label, appContext.packageName)
            .joinToString(" ")
            .lowercase()
        return when {
            appLower.containsAny("mail", "outlook", "gmail", "proton") ->
                "Use a professional written tone with proper greetings and sign-offs if present."
            appLower.containsAny(
                "slack", "discord", "telegram", "whatsapp", "messenger",
                "signal", "messages", "molly", "element", "matrix"
            ) ->
                "Use a casual conversational tone. Keep it concise and natural for chat."
            appLower.containsAny("docs", "notion", "notes", "obsidian", "keep", "evernote", "writer") ->
                "Use a clear, structured writing style suitable for documents and notes."
            appLower.containsAny("twitter", "x", "mastodon", "threads", "bluesky") ->
                "Keep it concise and suitable for social media posts."
            else -> null
        }
    }

    private fun String.containsAny(vararg terms: String): Boolean = terms.any { contains(it) }
}
