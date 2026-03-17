package com.sasayaki.data.api

import com.sasayaki.data.api.model.TranscriptionResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface AsrApiService {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribe(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("prompt") prompt: RequestBody? = null,
        @Part("language") language: RequestBody? = null
    ): TranscriptionResponse
}
