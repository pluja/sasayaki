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

            val systemPrompt = buildSystemPrompt(dictionaryWords, sourceApp, prefs.activeLanguage, prefs.preferredLanguages)

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
        activeLanguage: String?,
        preferredLanguages: List<String>
    ): String {
        val parts = mutableListOf<String>()

        parts += """You are a dictation post-processor. The input is raw speech-to-text output. Clean it into text that reads as if the person had typed it themselves. Make minimal changes:
- Remove filler words and verbal tics (um, uh, like, so, basically, I mean, well, euh, pues, bueno, えーと, あの)
- Remove discourse markers and conversational tics that only make sense in speech, not writing (you know?, right?, sabes?, verdad?, no?, saps?, quoi, hein, n'est-ce pas?, ne?)
- Remove thinking-aloud fragments and mid-sentence realizations that only happen in speech, not when typing. Examples:
  English: "huh what was I gonna say", "let me think", "wait no", "oh right", "anyway where was I", "how do I put this"
  Spanish: "que iba a decir", "ah si", "a ver", "bueno", "o sea", "es que", "pues nada"
  French: "comment dire", "attends", "je sais plus", "bref", "enfin bon"
  Catalan: "que volia dir", "doncs res", "a veure", "bueno el cas es que"
  Japanese: "何だっけ", "ちょっと待って", "あ、そうそう", "えーっと何て言うか"
  General: false starts, trailing-off ("so yeah..."), verbal pauses, self-addressed asides, sudden realizations ("oh right, that...")
- Apply self-corrections: when the speaker restates something, keep only the final version ("five no wait seven" -> "seven")
- Remove false starts and repeated words ("I I think" -> "I think")
- Fix punctuation, capitalization, and sentence boundaries
- Convert spoken forms to written forms ("dot com" -> ".com", "at sign" -> "@", "slash" -> "/", "arroba" -> "@", "punto com" -> ".com")
- Fix ASR misrecognition errors: Whisper often produces a wrong but plausible word in the same language (e.g. "their" instead of "there", "hay" instead of "ahí", "afinaranies" instead of "al final aniràs"). Use sentence context to pick the correct word
- Preserve the speaker's vocabulary, tone, and sentence structure. The result should sound like them, not like an editor
- Do not add, infer, or rephrase content beyond what was spoken
- If the input is already clean and needs no changes, return it as-is"""

        if (activeLanguage != null) {
            parts += "- The user is dictating in: $activeLanguage. Handle speech disfluencies for this language."
        } else if (preferredLanguages.isNotEmpty()) {
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
