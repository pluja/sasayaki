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
    suspend fun process(rawText: String, dictionaryWords: List<String>): String {
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

            val dictTerms = if (dictionaryWords.isNotEmpty()) {
                "\n- Use these known terms correctly: ${dictionaryWords.joinToString(", ")}"
            } else ""

            val systemPrompt = """You are a dictation post-processor. Clean up the following transcribed speech:
- Remove filler words (um, uh, like, you know)
- Apply self-corrections ("seven, no sorry, five" → "five")
- Fix grammar and punctuation$dictTerms
Return ONLY the cleaned text, nothing else."""

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
}
