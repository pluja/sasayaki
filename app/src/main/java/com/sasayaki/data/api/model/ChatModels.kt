package com.sasayaki.data.api.model

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("temperature") val temperature: Double = 0.3,
    @SerializedName("max_tokens") val maxTokens: Int = 2048
)

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class ChatCompletionResponse(
    @SerializedName("choices") val choices: List<Choice>
) {
    val text: String get() = choices.firstOrNull()?.message?.content ?: ""
}

data class Choice(
    @SerializedName("index") val index: Int,
    @SerializedName("message") val message: ChatMessage
)
