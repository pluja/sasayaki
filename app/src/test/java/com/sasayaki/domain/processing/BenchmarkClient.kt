package com.sasayaki.domain.processing

import com.google.gson.Gson
import com.sasayaki.data.api.model.ChatCompletionRequest
import com.sasayaki.data.api.model.ChatCompletionResponse
import com.sasayaki.data.api.model.ChatMessage
import com.sasayaki.domain.model.PostProcessingPrompt
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

data class Completion(val text: String, val latencyMs: Long, val error: String? = null)

/**
 * Shared plumbing for the two LLM-backed suites.
 *
 * Everything is configured from the environment and nothing is logged, so credentials do
 * not reach the report. Requests are issued one at a time: running them concurrently
 * would make the latency figures a measure of the connection pool rather than the model.
 */
class BenchmarkClient {
    private val endpoint: String? = System.getenv("OPENAI_ENDPOINT")?.trimEnd('/')
    private val apiKey: String? = System.getenv("OPENAI_API_KEY")

    val models: List<String> = (
        System.getenv("BENCH_MODELS") ?: System.getenv("OPENAI_MODEL") ?: ""
        ).split(",").map(String::trim).filter(String::isNotEmpty)

    /** Repetitions per measurement. Outputs vary run to run, so one sample proves little. */
    val reps: Int = System.getenv("BENCH_REPS")?.toIntOrNull()?.coerceAtLeast(1) ?: 3

    val isConfigured: Boolean
        get() = !endpoint.isNullOrBlank() && !apiKey.isNullOrBlank() && models.isNotEmpty()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    val builtInPrompts: List<PostProcessingPrompt> =
        BuiltInPrompts.ALL.mapIndexed { index, seed ->
            PostProcessingPrompt(
                id = index + 1L,
                title = seed.title,
                prompt = seed.prompt,
                builtIn = true
            )
        }

    fun complete(model: String, systemPrompt: String, userText: String): Completion {
        var text = ""
        var failure: String? = null
        val latency = measureTimeMillis {
            try {
                val payload = ChatCompletionRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage(role = "system", content = systemPrompt),
                        ChatMessage(role = "user", content = userText)
                    )
                )
                val http = Request.Builder()
                    .url("$endpoint/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(http).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        failure = "HTTP ${response.code}"
                    } else {
                        text = gson.fromJson(raw, ChatCompletionResponse::class.java).text.trim()
                    }
                }
            } catch (e: Exception) {
                failure = e::class.simpleName + ": " + (e.message ?: "")
            }
        }
        return Completion(text, latency, failure)
    }

    fun completeRepeatedly(model: String, systemPrompt: String, userText: String): List<Completion> =
        (1..reps).map { complete(model, systemPrompt, userText) }
}
