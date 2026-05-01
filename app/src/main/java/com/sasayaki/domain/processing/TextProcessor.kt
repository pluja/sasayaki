package com.sasayaki.domain.processing

import com.sasayaki.data.api.ApiClientFactory
import com.sasayaki.data.api.LlmApiService
import com.sasayaki.data.api.model.ChatCompletionRequest
import com.sasayaki.data.api.model.ChatMessage
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.repository.ProcessingRepository
import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.Profile
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextProcessor @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val preferencesDataStore: PreferencesDataStore,
    private val processingRepository: ProcessingRepository
) {
    suspend fun process(
        rawText: String,
        profile: Profile,
        appContext: AppContext? = null
    ): String {
        return try {
            val ruleProcessedText = processingRepository.applySelectedRules(rawText, profile.selectedRuleIds)
            val prefs = preferencesDataStore.preferences.first()
            if (!profile.llmEnabled || prefs.llmBaseUrl.isBlank() || prefs.llmApiKey.isBlank()) {
                return ruleProcessedText
            }

            val service = apiClientFactory.create(
                LlmApiService::class.java,
                prefs.llmBaseUrl,
                prefs.llmApiKey
            )

            val systemPrompt = processingRepository.buildSystemPrompt(profile, appContext)

            val request = ChatCompletionRequest(
                model = profile.llmModel,
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = ruleProcessedText)
                )
            )

            val response = service.chatCompletion(request)
            response.text.ifBlank { ruleProcessedText }
        } catch (e: Exception) {
            rawText
        }
    }
}
