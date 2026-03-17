package com.sasayaki.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
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
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            asrBaseUrl = prefs[Keys.ASR_BASE_URL] ?: "",
            asrApiKey = prefs[Keys.ASR_API_KEY] ?: "",
            asrModel = prefs[Keys.ASR_MODEL] ?: "whisper-1",
            llmBaseUrl = prefs[Keys.LLM_BASE_URL] ?: "",
            llmApiKey = prefs[Keys.LLM_API_KEY] ?: "",
            llmModel = prefs[Keys.LLM_MODEL] ?: "gpt-4o-mini",
            llmEnabled = prefs[Keys.LLM_ENABLED] ?: false,
            autoClipboard = prefs[Keys.AUTO_CLIPBOARD] ?: true,
            vibrateOnRecord = prefs[Keys.VIBRATE_ON_RECORD] ?: true,
            silenceThresholdMs = prefs[Keys.SILENCE_THRESHOLD_MS] ?: 2000L,
            language = prefs[Keys.LANGUAGE] ?: ""
        )
    }

    suspend fun updateAsrConfig(baseUrl: String, apiKey: String, model: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ASR_BASE_URL] = baseUrl
            prefs[Keys.ASR_API_KEY] = apiKey
            prefs[Keys.ASR_MODEL] = model
        }
    }

    suspend fun updateLlmConfig(baseUrl: String, apiKey: String, model: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LLM_BASE_URL] = baseUrl
            prefs[Keys.LLM_API_KEY] = apiKey
            prefs[Keys.LLM_MODEL] = model
            prefs[Keys.LLM_ENABLED] = enabled
        }
    }

    suspend fun updateGeneralSettings(autoClipboard: Boolean, vibrateOnRecord: Boolean, silenceThresholdMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_CLIPBOARD] = autoClipboard
            prefs[Keys.VIBRATE_ON_RECORD] = vibrateOnRecord
            prefs[Keys.SILENCE_THRESHOLD_MS] = silenceThresholdMs
        }
    }

    suspend fun updateLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language
        }
    }
}
