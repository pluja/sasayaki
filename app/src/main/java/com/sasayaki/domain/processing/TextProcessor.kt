package com.sasayaki.domain.processing

import com.sasayaki.data.api.ApiClientFactory
import com.sasayaki.data.api.LlmApiService
import com.sasayaki.data.api.model.ChatCompletionRequest
import com.sasayaki.data.api.model.ChatMessage
import com.sasayaki.data.preferences.PreferencesDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextProcessor @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val preferencesDataStore: PreferencesDataStore
) {
    suspend fun process(
        rawText: String,
        dictionaryWords: List<String>,
        sourceApp: String? = null
    ): String {
        return try {
            val prefs = preferencesDataStore.preferences.first()
            if (!prefs.llmEnabled || prefs.llmBaseUrl.isBlank() || prefs.llmApiKey.isBlank()) {
                return rawText
            }

            val service = apiClientFactory.create(
                LlmApiService::class.java,
                prefs.llmBaseUrl,
                prefs.llmApiKey
            )

            val systemPrompt = buildSystemPrompt(dictionaryWords, sourceApp, prefs.preferredLanguages)

            val request = ChatCompletionRequest(
                model = prefs.llmModel,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = rawText)
                )
            )

            val response = service.chatCompletion(request)
            response.text.ifBlank { rawText }
        } catch (e: Exception) {
            rawText
        }
    }

    private fun buildSystemPrompt(
        dictionaryWords: List<String>,
        sourceApp: String?,
        preferredLanguages: List<String>
    ): String {
        val parts = mutableListOf<String>()

        parts += """You are a dictation post-processor. The input is raw speech-to-text output. Transform it from spoken-style into clean written text with minimal changes:
- Remove filler words and verbal tics (um, uh, like, you know, so, basically, I mean, well, right)
- Remove thinking-aloud fragments that wouldn't appear in writing. Examples:
  English: "huh what was I gonna say", "let me think", "wait no", "oh right", "anyway where was I", "how do I put this"
  Spanish: "que iba a decir", "ah si", "a ver", "bueno", "o sea", "es que", "pues nada"
  General: false starts, trailing-off ("so yeah..."), verbal pauses, self-addressed asides
- Apply self-corrections: when the speaker restates something, keep only the final version ("five no wait seven" becomes "seven")
- Remove false starts and repeated words ("I I think" becomes "I think")
- Fix punctuation, capitalization, and sentence boundaries
- Convert spoken forms to written forms ("dot com" to ".com", "at sign" to "@", "slash" to "/")
- Preserve the speaker's original wording, tone, and intent. Only fix what's needed for speech to read naturally as written text
- Do not add, infer, or rephrase content beyond what was spoken"""

        if (preferredLanguages.isNotEmpty()) {
            val langList = preferredLanguages.joinToString(", ")
            parts += "- The user dictates in: $langList. Handle speech disfluencies in all of these languages."
        }

        if (dictionaryWords.isNotEmpty()) {
            parts += "- Use these known terms with their exact spelling: ${dictionaryWords.joinToString(", ")}"
        }

        if (!sourceApp.isNullOrBlank()) {
            val style = inferStyleForApp(sourceApp)
            if (style != null) {
                parts += "- The user is dictating into $sourceApp. $style"
            }
        }

        parts += "Return ONLY the cleaned text, nothing else."
        return parts.joinToString("\n")
    }

    private fun inferStyleForApp(sourceApp: String): String? {
        val appLower = sourceApp.lowercase()
        return when {
            appLower.containsAny("mail", "outlook", "gmail", "proton") ->
                "Use a professional written tone with proper greetings and sign-offs if present."
            appLower.containsAny("slack", "discord", "telegram", "whatsapp", "messenger", "signal", "messages") ->
                "Use a casual conversational tone. Keep it concise and natural for chat."
            appLower.containsAny("docs", "notion", "notes", "obsidian", "keep", "evernote", "writer") ->
                "Use a clear, structured writing style suitable for documents and notes."
            appLower.containsAny("twitter", "x", "mastodon", "threads", "bluesky") ->
                "Keep it very concise and punchy, suitable for social media posts."
            else -> null
        }
    }

    private fun String.containsAny(vararg terms: String): Boolean {
        return terms.any { this.contains(it) }
    }
}
