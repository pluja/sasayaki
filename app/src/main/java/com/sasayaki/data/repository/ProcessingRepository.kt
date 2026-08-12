package com.sasayaki.data.repository

import com.sasayaki.data.db.dao.PostProcessingPromptDao
import com.sasayaki.data.db.dao.TextReplacementRuleDao
import com.sasayaki.data.db.entity.PostProcessingPromptEntity
import com.sasayaki.data.db.entity.TextReplacementRuleEntity
import com.sasayaki.data.db.entity.toEntity
import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.OutputStyle
import com.sasayaki.domain.model.PostProcessingPrompt
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.SummarizeMode
import com.sasayaki.domain.model.TextReplacementRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessingRepository @Inject constructor(
    private val ruleDao: TextReplacementRuleDao,
    private val promptDao: PostProcessingPromptDao
) {
    val rules: Flow<List<TextReplacementRule>> = ruleDao.observeAll()
        .map { rules -> rules.map(TextReplacementRuleEntity::toDomain) }

    val prompts: Flow<List<PostProcessingPrompt>> = promptDao.observeAll()
        .map { prompts -> prompts.map(PostProcessingPromptEntity::toDomain) }

    suspend fun saveRule(rule: TextReplacementRule): Long {
        return if (rule.id == 0L) ruleDao.insert(rule.toEntity()) else {
            ruleDao.update(rule.toEntity())
            rule.id
        }
    }

    suspend fun deleteRule(id: Long) {
        ruleDao.deleteById(id)
    }

    suspend fun savePrompt(prompt: PostProcessingPrompt): Long {
        return if (prompt.id == 0L) promptDao.insert(prompt.toEntity()) else {
            val existing = promptDao.getById(prompt.id) ?: return 0L
            if (!existing.builtIn) promptDao.update(prompt.copy(builtIn = false).toEntity())
            prompt.id
        }
    }

    suspend fun deletePrompt(id: Long) {
        promptDao.deleteCustomById(id)
    }

    suspend fun applySelectedRules(text: String, selectedRuleIds: Set<Long>): String {
        if (selectedRuleIds.isEmpty()) return text
        return ruleDao.getAll()
            .map(TextReplacementRuleEntity::toDomain)
            .filter { it.id in selectedRuleIds && it.pattern.isNotBlank() }
            .fold(text) { current, rule ->
                if (rule.isRegex) {
                    runCatching { Regex(rule.pattern).replace(current, rule.replacement) }
                        .getOrElse { current }
                } else {
                    current.replace(rule.pattern, rule.replacement)
                }
            }
    }

    suspend fun buildSystemPrompt(profile: Profile, appContext: AppContext?): String {
        val prompts = promptDao.getAll().map(PostProcessingPromptEntity::toDomain)
        val promptTexts = prompts
            .filter(PostProcessingPrompt::builtIn)
            .map { it.prompt }
            .toMutableList()

        if (profile.profilePrompt.isNotBlank()) {
            promptTexts += profile.profilePrompt
        }

        if (profile.language != null) {
            promptTexts += "The user is dictating in: ${profile.language}. Handle speech disfluencies for this language."
        }

        if (appContext?.hasData == true) {
            promptTexts += buildAppContextPrompt(appContext)
        }

        promptTexts += prompts
            .filterNot(PostProcessingPrompt::builtIn)
            .filter { it.id in profile.selectedPromptIds }
            .map { it.prompt }

        promptTexts += buildStylePrompt(profile)

        if (promptTexts.isEmpty()) {
            promptTexts += "Clean the raw dictation with minimal changes, preserving the speaker's meaning and tone."
        }

        promptTexts += "Return ONLY the cleaned text, nothing else."
        return promptTexts.joinToString("\n")
    }

    private fun buildStylePrompt(profile: Profile): String {
        val style = when (profile.outputStyle) {
            OutputStyle.STANDARD -> "Use normal capitalization and punctuation."
            OutputStyle.RELAXED -> "Use capitalization, but keep punctuation light."
            OutputStyle.MINIMAL -> "Final text must be lowercase; remove punctuation unless needed for clarity."
        }
        val rewrite = when (profile.rewriteMode) {
            RewriteMode.NONE -> "Do not paraphrase; preserve wording while enforcing this style."
            RewriteMode.FIX -> "Keep wording close; fix grammar, artifacts, and incoherent fragments."
            RewriteMode.POLISH -> "Polish into a more formal, correct tone."
        }
        val summarize = when (profile.summarizeMode) {
            SummarizeMode.NONE -> "Do not summarize."
            SummarizeMode.LIGHT -> "Condense lightly by removing redundancy and rambling."
            SummarizeMode.HARD -> "Return a short summary only; omit supporting details."
        }
        val emoji = if (profile.emojiAllowed) {
            "For casual messages, include one fitting emoji; otherwise add none."
        } else {
            "Do not add emoji unless explicitly dictated."
        }
        return "Style override: $style $rewrite $summarize $emoji"
    }

    private fun buildAppContextPrompt(appContext: AppContext): String {
        val lines = mutableListOf<String>()
        appContext.displayName?.let { lines += "- Target app: $it" }
        appContext.packageName?.takeIf { it.isNotBlank() }?.let { lines += "- Target package: $it" }
        inferStyleForApp(appContext)?.let { lines += "- App style hint: $it" }
        lines += "- Adapt to the target app without overriding the Style override."
        return "Context:\n${lines.joinToString("\n")}"
    }

    private fun inferStyleForApp(appContext: AppContext): String? {
        val appLower = listOfNotNull(appContext.label, appContext.packageName).joinToString(" ").lowercase()
        return when {
            appLower.containsAny("mail", "outlook", "gmail", "proton") ->
                "Use a professional written tone with proper greetings and sign-offs if present."
            appLower.containsAny("slack", "discord", "telegram", "whatsapp", "messenger", "signal", "messages", "molly", "element", "matrix") ->
                "Use a casual conversational tone. Keep it concise and natural for chat."
            appLower.containsAny("docs", "notion", "notes", "obsidian", "keep", "evernote", "writer") ->
                "Use a clear, structured writing style suitable for documents and notes."
            appLower.containsAny("twitter", "x", "mastodon", "threads", "bluesky") ->
                "Keep it concise and suitable for social media posts."
            else -> null
        }
    }

    private fun String.containsAny(vararg terms: String): Boolean {
        return terms.any { contains(it) }
    }
}
