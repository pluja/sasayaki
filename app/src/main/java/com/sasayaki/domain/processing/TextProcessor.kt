package com.sasayaki.domain.processing

import android.util.Log
import com.sasayaki.data.api.ApiClientFactory
import com.sasayaki.data.api.LlmApiService
import com.sasayaki.data.api.model.ChatCompletionRequest
import com.sasayaki.data.api.model.ChatMessage
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.repository.ProcessingRepository
import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextProcessor @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val preferencesDataStore: PreferencesDataStore,
    private val processingRepository: ProcessingRepository
) {
    /**
     * @param onPostProcessing invoked immediately before the LLM request, so callers can
     *   distinguish the network-bound post-processing phase from transcription.
     */
    suspend fun process(
        rawText: String,
        profile: Profile,
        appContext: AppContext? = null,
        onPostProcessing: (() -> Unit)? = null
    ): String {
        val ruleProcessedText = try {
            processingRepository.applySelectedRules(rawText, profile.selectedRuleIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Replacement rules failed", e)
            rawText
        }

        return try {
            val prefs = preferencesDataStore.preferences.first()
            if (!profile.llmEnabled || prefs.llmBaseUrl.isBlank() || prefs.llmApiKey.isBlank()) {
                return ruleProcessedText
            }

            onPostProcessing?.invoke()

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
        } catch (e: CancellationException) {
            // The user cancelled mid-request; never fall through to injecting text.
            throw e
        } catch (e: Exception) {
            // Fall back to the rule-processed text rather than the raw transcript, so a
            // failed LLM call does not also discard the local replacement rules.
            Log.e(TAG, "Post-processing failed; using rule-processed text", e)
            ruleProcessedText
        }
    }

    private companion object {
        const val TAG = "TextProcessor"
    }
}
