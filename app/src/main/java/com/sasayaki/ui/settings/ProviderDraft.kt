package com.sasayaki.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sasayaki.data.repository.ConnectionResult
import com.sasayaki.data.repository.ProviderRepository
import com.sasayaki.domain.provider.ModelInfo
import com.sasayaki.domain.provider.ModelKind
import com.sasayaki.domain.provider.Provider
import com.sasayaki.domain.provider.isUnencryptedAddress
import com.sasayaki.domain.provider.serverAddressError
import com.sasayaki.domain.provider.suggestProviderName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ProviderDraft holds one provider while it is edited. Its fields are snapshot state so
 * text fields bound to them never lag a flow.
 */
class ProviderDraft(private val repository: ProviderRepository, private val scope: CoroutineScope, val id: Long = 0) {
    var name by mutableStateOf("")
        private set
    var url by mutableStateOf("")
        private set
    var apiKey by mutableStateOf("")
        private set
    var speechModel by mutableStateOf<String?>(null)
        private set
    var cleanupModel by mutableStateOf<String?>(null)
        private set
    private var nameEdited = false
    private var speechModelChosen = false

    private val _state = MutableStateFlow(ProviderEditorUiState(loaded = id == 0L))
    val state: StateFlow<ProviderEditorUiState> = _state.asStateFlow()

    val form: ProviderForm get() = ProviderForm(name, url, apiKey, speechModel, cleanupModel)

    /** load fills the draft from the saved provider and returns false when it no longer exists. */
    suspend fun load(): Boolean {
        val provider = repository.get(id) ?: return false
        name = provider.name
        nameEdited = true
        url = provider.baseUrl
        apiKey = provider.apiKey
        speechModel = provider.speechModel
        cleanupModel = provider.cleanupModel
        speechModelChosen = true
        _state.update {
            it.copy(
                loaded = true,
                loadingModels = true,
                models = repository.models.value[id].orEmpty(),
                unencrypted = isUnencryptedAddress(provider.baseUrl)
            )
        }
        val models = repository.refreshModels(id)
        _state.update { it.copy(loadingModels = false, models = models) }
        return true
    }

    fun onNameChange(value: String) {
        name = value
        nameEdited = value.isNotBlank()
    }

    fun onUrlChange(value: String) {
        url = value
        if (!nameEdited) name = suggestProviderName(value).orEmpty()
        _state.update { it.copy(result = null, urlError = null, unencrypted = isUnencryptedAddress(value)) }
    }

    fun onApiKeyChange(value: String) {
        apiKey = value
        _state.update { it.copy(result = null) }
    }

    fun onSpeechModel(model: String?) {
        speechModel = model
        speechModelChosen = true
    }

    fun onCleanupModel(model: String?) {
        cleanupModel = model
    }

    fun test() {
        if (!validateUrl()) return
        scope.launch {
            _state.update { it.copy(testing = true, result = null) }
            val result = repository.test(url, apiKey, speechModel, cleanupModel, id.takeIf { it != 0L })
            val models = (result as? ConnectionResult.Connected)?.models.orEmpty()
            // On a saved provider null means "not used for speech", which a test must not undo.
            if (!speechModelChosen) speechModel = suggestSpeechModel(models)
            _state.update { it.copy(testing = false, result = result, models = models.ifEmpty { it.models }) }
        }
    }

    fun validateUrl(): Boolean {
        val error = if (url.isBlank()) "Enter the server address." else serverAddressError(url)
        _state.update { it.copy(urlError = error) }
        return error == null
    }

    fun showUrlError(error: String?) {
        _state.update { it.copy(urlError = error) }
    }

    fun markFinished() {
        _state.update { it.copy(finished = true) }
    }

    fun toProvider() = Provider(
        id = id,
        name = name.trim().ifEmpty { suggestProviderName(url) ?: "Server" },
        baseUrl = url.trim(),
        apiKey = apiKey.trim(),
        speechModel = speechModel,
        cleanupModel = cleanupModel
    )

    private companion object {
        val preferredSpeechModels = listOf("large-v3-turbo", "large-v3", "whisper-1", "gpt-4o-mini-transcribe")

        fun suggestSpeechModel(models: List<ModelInfo>): String? {
            val speech = models.filter { it.kind == ModelKind.SPEECH }.map(ModelInfo::id)
            return preferredSpeechModels.firstNotNullOfOrNull { hint -> speech.firstOrNull { hint in it.lowercase() } }
                ?: speech.firstOrNull()
        }
    }
}
