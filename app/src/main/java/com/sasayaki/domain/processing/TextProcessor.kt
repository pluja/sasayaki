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

            val systemPrompt = buildSystemPrompt(dictionaryWords, sourceApp)

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

    private fun buildSystemPrompt(dictionaryWords: List<String>, sourceApp: String?): String {
        val parts = mutableListOf<String>()

        parts += """You are a dictation post-processor. The input is raw speech-to-text output. Transform it into clean written text:
- Remove filler words and verbal tics (um, uh, like, you know, so, basically, I mean)
- Apply self-corrections: when the speaker restates something, keep only the final version ("five no wait seven" becomes "seven")
- Remove false starts and repeated words ("I I think" becomes "I think")
- Fix punctuation, capitalization, and sentence boundaries
- Convert spoken forms to written forms ("dot com" to ".com", "at sign" to "@", "slash" to "/")
- Do not add, infer, or rephrase content beyond what was spoken"""

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
