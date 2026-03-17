package com.sasayaki.data.api

import com.sasayaki.data.api.model.ChatCompletionRequest
import com.sasayaki.data.api.model.ChatCompletionResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface LlmApiService {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(@Body request: ChatCompletionRequest): ChatCompletionResponse
}
