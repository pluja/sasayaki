package com.sasayaki.domain.transcription

import com.sasayaki.data.api.ApiClientFactory
import com.sasayaki.data.api.AsrApiService
import com.sasayaki.data.preferences.PreferencesDataStore
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperEngine @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val preferencesDataStore: PreferencesDataStore,
    private val networkMonitor: NetworkMonitor
) : TranscriptionEngine {

    override suspend fun transcribe(audioFile: File, model: String, language: String?): Result<String> {
        return try {
            val prefs = preferencesDataStore.preferences.first()
            val service = apiClientFactory.create(
                AsrApiService::class.java,
                prefs.asrBaseUrl,
                prefs.asrApiKey
            )

            val filePart = MultipartBody.Part.createFormData(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaType())
            )
            val modelPart = model.toRequestBody("text/plain".toMediaType())
            val languagePart = language?.let {
                it.toRequestBody("text/plain".toMediaType())
            }

            val response = service.transcribe(filePart, modelPart, language = languagePart)
            Result.success(response.text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isAvailable(): Boolean {
        return networkMonitor.isOnline.value
    }
}
