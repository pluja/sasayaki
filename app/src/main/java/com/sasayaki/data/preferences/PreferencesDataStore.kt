package com.sasayaki.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferencesStore: SecurePreferencesStore
) {
    private object Keys {
        val ASR_BASE_URL = stringPreferencesKey("asr_base_url")
        val ASR_API_KEY = stringPreferencesKey("asr_api_key")
        val ASR_MODEL = stringPreferencesKey("asr_model")
        val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val LLM_MODEL = stringPreferencesKey("llm_model")
        val LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        val AUTO_CLIPBOARD = booleanPreferencesKey("auto_clipboard")
        val VIBRATE_ON_RECORD = booleanPreferencesKey("vibrate_on_record")
        val SILENCE_THRESHOLD_MS = longPreferencesKey("silence_threshold_ms")
        val LANGUAGE = stringPreferencesKey("language")
        val PREFERRED_LANGUAGES = stringPreferencesKey("preferred_languages")
        val ACTIVE_LANGUAGE = stringPreferencesKey("active_language")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e("PreferencesDataStore", "Error reading preferences", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
        migrateLegacySecretsIfNeeded(prefs)

        UserPreferences(
            asrBaseUrl = prefs[Keys.ASR_BASE_URL] ?: "",
            asrApiKey = securePreferencesStore.getAsrApiKey(),
            asrModel = prefs[Keys.ASR_MODEL] ?: "whisper-1",
            llmBaseUrl = prefs[Keys.LLM_BASE_URL] ?: "",
            llmApiKey = securePreferencesStore.getLlmApiKey(),
            llmModel = prefs[Keys.LLM_MODEL] ?: "gpt-4o-mini",
            llmEnabled = prefs[Keys.LLM_ENABLED] ?: false,
            autoClipboard = prefs[Keys.AUTO_CLIPBOARD] ?: true,
            vibrateOnRecord = prefs[Keys.VIBRATE_ON_RECORD] ?: true,
            silenceThresholdMs = prefs[Keys.SILENCE_THRESHOLD_MS] ?: 2000L,
            preferredLanguages = parsePreferredLanguages(prefs),
            activeLanguage = resolveActiveLanguage(prefs),
            historyEnabled = prefs[Keys.HISTORY_ENABLED] ?: true
        )
    }

    suspend fun updateAsrConfig(baseUrl: String, apiKey: String, model: String) {
        securePreferencesStore.updateAsrApiKey(apiKey.trim())
        context.dataStore.edit { prefs ->
            prefs[Keys.ASR_BASE_URL] = baseUrl.trim()
            prefs.remove(Keys.ASR_API_KEY)
            prefs[Keys.ASR_MODEL] = model.trim()
        }
    }

    suspend fun updateLlmConfig(baseUrl: String, apiKey: String, model: String, enabled: Boolean) {
        securePreferencesStore.updateLlmApiKey(apiKey.trim())
        context.dataStore.edit { prefs ->
            prefs[Keys.LLM_BASE_URL] = baseUrl.trim()
            prefs.remove(Keys.LLM_API_KEY)
            prefs[Keys.LLM_MODEL] = model.trim()
            prefs[Keys.LLM_ENABLED] = enabled
        }
    }

    suspend fun updateGeneralSettings(
        autoClipboard: Boolean,
        vibrateOnRecord: Boolean,
        silenceThresholdMs: Long,
        historyEnabled: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_CLIPBOARD] = autoClipboard
            prefs[Keys.VIBRATE_ON_RECORD] = vibrateOnRecord
            prefs[Keys.SILENCE_THRESHOLD_MS] = silenceThresholdMs
            prefs[Keys.HISTORY_ENABLED] = historyEnabled
        }
    }

    suspend fun updateActiveLanguage(language: String?) {
        context.dataStore.edit { prefs ->
            if (language == null) {
                prefs.remove(Keys.ACTIVE_LANGUAGE)
            } else {
                prefs[Keys.ACTIVE_LANGUAGE] = language
            }
        }
    }

    suspend fun toggleLlmEnabled() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LLM_ENABLED] = !(prefs[Keys.LLM_ENABLED] ?: false)
        }
    }

    suspend fun toggleHistoryEnabled() {
        context.dataStore.edit { prefs ->
            prefs[Keys.HISTORY_ENABLED] = !(prefs[Keys.HISTORY_ENABLED] ?: true)
        }
    }

    suspend fun updatePreferredLanguages(languages: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PREFERRED_LANGUAGES] = languages.joinToString(",")
            prefs.remove(Keys.LANGUAGE)
        }
    }

    private fun resolveActiveLanguage(prefs: Preferences): String? {
        val preferred = parsePreferredLanguages(prefs)
        val active = prefs[Keys.ACTIVE_LANGUAGE]
        if (active != null) return if (active in preferred) active else null
        return if (preferred.size == 1) preferred.first() else null
    }

    private fun parsePreferredLanguages(prefs: Preferences): List<String> {
        val stored = prefs[Keys.PREFERRED_LANGUAGES].orEmpty()
        if (stored.isNotBlank()) {
            return stored.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        }
        val legacy = prefs[Keys.LANGUAGE].orEmpty().trim().lowercase()
        if (legacy.isNotBlank()) return listOf(legacy)
        return emptyList()
    }

    private suspend fun migrateLegacySecretsIfNeeded(prefs: Preferences) {
        val legacyAsrApiKey = prefs[Keys.ASR_API_KEY].orEmpty()
        val legacyLlmApiKey = prefs[Keys.LLM_API_KEY].orEmpty()

        if (legacyAsrApiKey.isBlank() && legacyLlmApiKey.isBlank()) return

        if (legacyAsrApiKey.isNotBlank() && securePreferencesStore.getAsrApiKey().isBlank()) {
            securePreferencesStore.updateAsrApiKey(legacyAsrApiKey)
        }

        if (legacyLlmApiKey.isNotBlank() && securePreferencesStore.getLlmApiKey().isBlank()) {
            securePreferencesStore.updateLlmApiKey(legacyLlmApiKey)
        }

        context.dataStore.edit { mutablePrefs ->
            mutablePrefs.remove(Keys.ASR_API_KEY)
            mutablePrefs.remove(Keys.LLM_API_KEY)
        }
    }

}
