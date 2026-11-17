package com.sasayaki.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.sasayaki.R
import com.sasayaki.data.api.ApiClientFactory
import com.sasayaki.data.api.AsrApiService
import com.sasayaki.data.api.LlmApiService
import com.sasayaki.data.api.ModelsApiService
import com.sasayaki.data.api.model.ChatCompletionRequest
import com.sasayaki.data.api.model.ChatMessage
import com.sasayaki.data.db.SasayakiDatabase
import com.sasayaki.data.db.dao.ProfileDao
import com.sasayaki.data.db.dao.ProviderDao
import com.sasayaki.data.db.entity.ProviderEntity
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.preferences.SecurePreferencesStore
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.provider.LegacyEndpoint
import com.sasayaki.domain.provider.ModelInfo
import com.sasayaki.domain.provider.Provider
import com.sasayaki.domain.provider.Purpose
import com.sasayaki.domain.provider.Resolution
import com.sasayaki.domain.provider.classifyModel
import com.sasayaki.domain.provider.describeConnectionError
import com.sasayaki.domain.provider.planLegacyMigration
import com.sasayaki.domain.provider.resolveEndpoint
import com.sasayaki.domain.provider.sameServer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

sealed interface ConnectionResult {
    /** Connected.models is empty when the server works but does not publish a model list. */
    data class Connected(val models: List<ModelInfo>) : ConnectionResult
    data class Failed(val message: String) : ConnectionResult
}

@Singleton
class ProviderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SasayakiDatabase,
    private val providerDao: ProviderDao,
    private val profileDao: ProfileDao,
    private val secureStore: SecurePreferencesStore,
    private val preferencesDataStore: PreferencesDataStore,
    private val apiClientFactory: ApiClientFactory
) {
    val providers: Flow<List<Provider>> = providerDao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    private val _models = MutableStateFlow<Map<Long, List<ModelInfo>>>(emptyMap())

    /** models holds the model lists fetched this session, by provider id. */
    val models: StateFlow<Map<Long, List<ModelInfo>>> = _models.asStateFlow()

    suspend fun getAll(): List<Provider> = withContext(Dispatchers.IO) { providerDao.getAll().map { it.toDomain() } }

    suspend fun get(id: Long): Provider? = withContext(Dispatchers.IO) { providerDao.getById(id)?.toDomain() }

    /** save returns the provider's id, or 0 when it no longer exists. */
    suspend fun save(provider: Provider): Long = withContext(Dispatchers.IO) {
        val entity = provider.toEntity()
        val id = if (provider.id == 0L) {
            providerDao.insert(entity)
        } else {
            // A late save for a deleted provider must not bring its key back.
            val existing = providerDao.getById(provider.id) ?: return@withContext 0L
            providerDao.update(entity.copy(createdAt = existing.createdAt))
            provider.id
        }
        secureStore.setProviderApiKey(id, provider.apiKey.trim())
        id
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        providerDao.delete(id)
        secureStore.setProviderApiKey(id, "")
        _models.update { it - id }
    }

    suspend fun resolve(profile: Profile, purpose: Purpose): Resolution {
        val chosen = if (purpose == Purpose.SPEECH) profile.speechProviderId else profile.cleanupProviderId
        val override = if (purpose == Purpose.SPEECH) profile.asrModel else profile.llmModel
        return resolveEndpoint(getAll(), purpose, chosen, override)
    }

    /** refreshModels updates the cached model list of a saved provider; empty when unavailable. */
    suspend fun refreshModels(providerId: Long): List<ModelInfo> {
        val provider = get(providerId) ?: return emptyList()
        val models = try {
            listModels(provider.baseUrl, provider.apiKey)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return _models.value[providerId].orEmpty()
        }
        _models.update { it + (providerId to models) }
        return models
    }

    /**
     * test checks that [baseUrl] answers with [apiKey]. Servers without /v1/models are
     * probed with a minimal request to the endpoint the given model would use instead.
     */
    suspend fun test(
        baseUrl: String,
        apiKey: String,
        speechModel: String?,
        cleanupModel: String?,
        providerId: Long? = null
    ): ConnectionResult = try {
        val models = listModels(baseUrl, apiKey)
        if (providerId != null) _models.update { it + (providerId to models) }
        ConnectionResult.Connected(models)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        if (e.code() in MODELS_UNSUPPORTED) {
            probe(baseUrl, apiKey, speechModel, cleanupModel)
        } else {
            ConnectionResult.Failed(describeConnectionError(e, context.resources))
        }
    } catch (e: Exception) {
        ConnectionResult.Failed(describeConnectionError(e, context.resources))
    }

    /**
     * migrateLegacyEndpoints moves the single speech and cleanup endpoints from before
     * providers existed into providers. Rows are written in one transaction; if a previous
     * run stopped before saving the keys, they are matched back to their providers.
     */
    suspend fun migrateLegacyEndpoints() = withContext(Dispatchers.IO) {
        val legacy = preferencesDataStore.legacyEndpoints()
        if (legacy == null) {
            preferencesDataStore.clearLegacyApiKeysIfPresent()
            return@withContext
        }
        val (speech, cleanup) = legacy
        if (providerDao.count() == 0) {
            val plan = planLegacyMigration(speech, cleanup)
            val ids = database.withTransaction {
                val ids = plan.providers.map { providerDao.insert(it.toEntity()) }
                val speechProvider = plan.speechProviderIndex?.let(plan.providers::get)
                val cleanupProvider = plan.cleanupProviderIndex?.let(plan.providers::get)
                profileDao.getAll().forEach { profile ->
                    profileDao.update(
                        profile.copy(
                            speechProviderId = plan.speechProviderIndex?.let(ids::get),
                            asrModel = profile.asrModel.takeUnless { it == speechProvider?.speechModel }.orEmpty(),
                            cleanupProviderId = plan.cleanupProviderIndex?.let(ids::get),
                            llmModel = profile.llmModel.takeUnless { it == cleanupProvider?.cleanupModel }.orEmpty()
                        )
                    )
                }
                ids
            }
            plan.providers.zip(ids).forEach { (provider, id) -> secureStore.setProviderApiKey(id, provider.apiKey) }
        } else {
            restoreLegacyKeys(listOfNotNull(speech, cleanup))
        }
        preferencesDataStore.clearLegacyEndpoints()
    }

    private suspend fun restoreLegacyKeys(endpoints: List<LegacyEndpoint>) {
        val providers = providerDao.getAll()
        endpoints.filter { it.apiKey.isNotBlank() }.forEach { endpoint ->
            providers
                .filter { sameServer(it.baseUrl, endpoint.baseUrl) && secureStore.getProviderApiKey(it.id).isEmpty() }
                .forEach { secureStore.setProviderApiKey(it.id, endpoint.apiKey.trim()) }
        }
    }

    private suspend fun listModels(baseUrl: String, apiKey: String): List<ModelInfo> =
        apiClientFactory.create(ModelsApiService::class.java, baseUrl, apiKey)
            .listModels()
            .data.orEmpty()
            .mapNotNull { entry -> entry.id?.takeIf(String::isNotBlank)?.let { ModelInfo(it, classifyModel(it, entry.task)) } }
            .distinctBy(ModelInfo::id)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, ModelInfo::id))

    private suspend fun probe(baseUrl: String, apiKey: String, speechModel: String?, cleanupModel: String?): ConnectionResult {
        val speech = speechModel?.takeIf(String::isNotBlank)
        val cleanup = cleanupModel?.takeIf(String::isNotBlank)
        return try {
            when {
                speech != null -> {
                    val service = apiClientFactory.create(AsrApiService::class.java, baseUrl, apiKey)
                    val file = MultipartBody.Part.createFormData("file", "probe.wav", silentWav().toRequestBody(WAV))
                    service.transcribe(file, speech.toRequestBody(TEXT))
                }

                cleanup != null -> {
                    val service = apiClientFactory.create(LlmApiService::class.java, baseUrl, apiKey)
                    service.chatCompletion(ChatCompletionRequest(cleanup, listOf(ChatMessage("user", "Reply with OK."))))
                }

                else -> return ConnectionResult.Failed(context.getString(R.string.error_models_not_listed))
            }
            ConnectionResult.Connected(emptyList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ConnectionResult.Failed(describeConnectionError(e, context.resources))
        }
    }

    private fun Provider.toEntity() = ProviderEntity(
        id = id,
        name = name.trim().ifEmpty { "Server" },
        baseUrl = baseUrl.trim(),
        speechModel = speechModel?.trim()?.ifEmpty { null },
        cleanupModel = cleanupModel?.trim()?.ifEmpty { null }
    )

    private fun ProviderEntity.toDomain() = Provider(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = secureStore.getProviderApiKey(id),
        speechModel = speechModel,
        cleanupModel = cleanupModel
    )

    private companion object {
        val MODELS_UNSUPPORTED = setOf(404, 405, 501)
        val WAV = "audio/wav".toMediaType()
        val TEXT = "text/plain".toMediaType()

        /** silentWav is a tenth of a second of 16 kHz mono silence: the smallest upload servers accept. */
        fun silentWav(): ByteArray {
            val sampleRate = 16_000
            val dataSize = sampleRate / 10 * 2
            return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(36 + dataSize)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(sampleRate)
                putInt(sampleRate * 2)
                putShort(2)
                putShort(16)
                put("data".toByteArray())
                putInt(dataSize)
            }.array()
        }
    }
}
