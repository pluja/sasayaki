package com.sasayaki.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sasayaki.data.db.SasayakiDatabase
import com.sasayaki.data.db.entity.ProviderEntity
import com.sasayaki.data.db.entity.toEntity
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.preferences.SecurePreferencesStore
import com.sasayaki.data.repository.HistoryRepository
import com.sasayaki.data.repository.ProcessingRepository
import com.sasayaki.data.repository.ProfileRepository
import com.sasayaki.data.repository.ProviderRepository
import com.sasayaki.domain.model.PostProcessingPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class SettingsBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SasayakiDatabase,
    private val providerRepository: ProviderRepository,
    private val processingRepository: ProcessingRepository,
    private val profileRepository: ProfileRepository,
    private val historyRepository: HistoryRepository,
    private val secureStore: SecurePreferencesStore,
    private val preferencesDataStore: PreferencesDataStore
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val snapshot = SettingsSnapshot(
            providers = providerRepository.getAll(),
            rules = processingRepository.rules.first(),
            instructions = processingRepository.prompts.first().filterNot(PostProcessingPrompt::builtIn),
            profiles = profileRepository.profiles.first(),
            preferences = preferencesDataStore.preferences.first()
        )
        val json = SettingsBackupCodec.encode(snapshot)
        // "wt" truncates: some storage providers otherwise keep the old file's tail after the JSON.
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write to $uri")
        output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun importFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")
        val bytes = input.use { it.readNBytesUpTo(MAX_FILE_BYTES + 1) }
        if (bytes.size > MAX_FILE_BYTES) throw BackupException(BackupProblem.NOT_SETTINGS_FILE, "the file is too large")
        val snapshot = SettingsBackupCodec.decode(bytes.toString(Charsets.UTF_8), preferencesDataStore.preferences.first())
        // Once the first table is replaced, the rest must follow even if the screen goes away.
        withContext(NonCancellable) {
            try {
                restore(snapshot)
            } catch (e: Exception) {
                throw RestoreInterruptedException(e)
            }
        }
    }

    private suspend fun restore(snapshot: SettingsSnapshot) {
        val previousProviders = providerRepository.getAll()
        val current = preferencesDataStore.preferences.first()
        val historyCount = historyRepository.historyCount.first()
        val now = System.currentTimeMillis()
        var providerIds = emptyMap<Long, Long>()
        var profileIds = emptyMap<Long, Long>()
        database.withTransaction {
            val providerDao = database.providerDao()
            val ruleDao = database.textReplacementRuleDao()
            val promptDao = database.postProcessingPromptDao()
            val profileDao = database.profileDao()
            profileDao.deleteAll()
            providerDao.deleteAll()
            ruleDao.deleteAll()
            promptDao.deleteAllCustom()

            providerIds = snapshot.providers.mapIndexed { index, provider ->
                provider.id to providerDao.insert(
                    ProviderEntity(
                        name = provider.name.ifEmpty { "Server" },
                        baseUrl = provider.baseUrl,
                        speechModel = provider.speechModel,
                        cleanupModel = provider.cleanupModel,
                        createdAt = now + index
                    )
                )
            }.toMap()
            val ruleIds = snapshot.rules.mapIndexed { index, rule ->
                rule.id to ruleDao.insert(rule.copy(id = 0, createdAt = now + index).toEntity())
            }.toMap()
            val instructionIds = snapshot.instructions.mapIndexed { index, instruction ->
                instruction.id to promptDao.insert(instruction.copy(id = 0, builtIn = false, createdAt = now + index).toEntity())
            }.toMap()
            // Profiles list newest first, so earlier ones in the file get later timestamps.
            profileIds = snapshot.profilesForInsert(providerIds, ruleIds, instructionIds)
                .zip(snapshot.profiles)
                .mapIndexed { index, (profile, original) -> original.id to profileDao.insert(profile.toEntity(now - index)) }
                .toMap()
        }

        // Provider ids are never reused (AUTOINCREMENT), so writing the carried keys before erasing
        // the old ones means a crash in between loses nothing.
        val restoredProviders = snapshot.providers.associateBy { providerIds.getValue(it.id) }
        carriedApiKeys(previousProviders, restoredProviders).forEach { (id, key) -> secureStore.setProviderApiKey(id, key) }
        previousProviders.forEach { secureStore.setProviderApiKey(it.id, "") }

        val imported = snapshot.preferences
        preferencesDataStore.restore(
            imported.copy(
                appProfiles = snapshot.appProfilesFor(profileIds),
                historyRetentionLimit = retentionAfterImport(imported.historyRetentionLimit, current.historyRetentionLimit, historyCount)
            )
        )
    }

    private companion object {
        // MAX_FILE_BYTES is far above any real settings file, and low enough that a crafted one
        // cannot exhaust memory while it is parsed.
        const val MAX_FILE_BYTES = 256 * 1024
    }
}

private fun java.io.InputStream.readNBytesUpTo(limit: Int): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (buffer.size() < limit) {
        val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
        if (read < 0) break
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
}

/** RestoreInterruptedException means an import failed after it began replacing settings. */
class RestoreInterruptedException(cause: Throwable) : Exception(cause)
