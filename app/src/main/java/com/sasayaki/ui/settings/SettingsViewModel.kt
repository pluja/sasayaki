package com.sasayaki.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sasayaki.data.api.ApiClientFactory
import com.sasayaki.data.api.AsrApiService
import com.sasayaki.data.api.LlmApiService
import com.sasayaki.data.api.model.ChatCompletionRequest
import com.sasayaki.data.api.model.ChatMessage
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import javax.inject.Inject

sealed class TestState {
    data object Idle : TestState()
    data object Testing : TestState()
    data class Success(val message: String) : TestState()
    data class Error(val message: String) : TestState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
    private val apiClientFactory: ApiClientFactory
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = preferencesDataStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val _asrTestState = MutableStateFlow<TestState>(TestState.Idle)
    val asrTestState: StateFlow<TestState> = _asrTestState.asStateFlow()

    private val _llmTestState = MutableStateFlow<TestState>(TestState.Idle)
    val llmTestState: StateFlow<TestState> = _llmTestState.asStateFlow()

    private val _asrSaved = MutableStateFlow(false)
    val asrSaved: StateFlow<Boolean> = _asrSaved.asStateFlow()

    private val _llmSaved = MutableStateFlow(false)
    val llmSaved: StateFlow<Boolean> = _llmSaved.asStateFlow()

    fun saveAsrConfig(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            preferencesDataStore.updateAsrConfig(baseUrl, apiKey, model)
            apiClientFactory.invalidate()
            _asrSaved.value = true
            delay(2000)
            _asrSaved.value = false
        }
    }

    fun saveLlmConfig(baseUrl: String, apiKey: String, model: String, enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.updateLlmConfig(baseUrl, apiKey, model, enabled)
            apiClientFactory.invalidate()
            _llmSaved.value = true
            delay(2000)
            _llmSaved.value = false
        }
    }

    fun saveGeneralSettings(autoClipboard: Boolean, vibrateOnRecord: Boolean, silenceThresholdMs: Long) {
        viewModelScope.launch {
            preferencesDataStore.updateGeneralSettings(autoClipboard, vibrateOnRecord, silenceThresholdMs)
        }
    }

    fun testAsrConnection(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            _asrTestState.value = TestState.Testing
            try {
                val service = apiClientFactory.create(AsrApiService::class.java, baseUrl, apiKey)
                // Send a tiny silent WAV to actually test the endpoint
                val silentWav = createSilentWav()
                val filePart = MultipartBody.Part.createFormData(
                    "file", "test.wav",
                    silentWav.toRequestBody("audio/wav".toMediaType())
                )
                val modelPart = model.toRequestBody("text/plain".toMediaType())
                service.transcribe(filePart, modelPart)
                _asrTestState.value = TestState.Success("Connected")
            } catch (e: HttpException) {
                val errorBody = try {
                    e.response()?.errorBody()?.string()?.take(300) ?: "No details"
                } catch (_: Exception) { "Could not read error body" }
                _asrTestState.value = TestState.Error("HTTP ${e.code()}: $errorBody")
            } catch (e: Exception) {
                _asrTestState.value = TestState.Error(e.message ?: "Unknown error")
            }
            delay(5000)
            _asrTestState.value = TestState.Idle
        }
    }

    fun testLlmConnection(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            _llmTestState.value = TestState.Testing
            try {
                val service = apiClientFactory.create(LlmApiService::class.java, baseUrl, apiKey)
                val response = service.chatCompletion(
                    ChatCompletionRequest(
                        model = model,
                        messages = listOf(ChatMessage("user", "Say 'ok'"))
                    )
                )
                _llmTestState.value = TestState.Success("OK: ${response.text.take(50)}")
            } catch (e: HttpException) {
                val errorBody = try {
                    e.response()?.errorBody()?.string()?.take(300) ?: "No details"
                } catch (_: Exception) { "Could not read error body" }
                _llmTestState.value = TestState.Error("HTTP ${e.code()}: $errorBody")
            } catch (e: Exception) {
                _llmTestState.value = TestState.Error(e.message ?: "Unknown error")
            }
            delay(5000)
            _llmTestState.value = TestState.Idle
        }
    }

    /** Create a minimal valid WAV file (0.1s of silence) for testing the ASR endpoint */
    private fun createSilentWav(): ByteArray {
        val sampleRate = 16000
        val numSamples = sampleRate / 10 // 0.1 second
        val dataSize = numSamples * 2 // 16-bit = 2 bytes per sample
        val buffer = java.nio.ByteBuffer.allocate(44 + dataSize)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // byte rate
        buffer.putShort(2) // block align
        buffer.putShort(16) // bits per sample
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        // silence = zeros (already initialized to 0)
        return buffer.array()
    }
}
