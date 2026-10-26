package com.sasayaki.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sasayaki.domain.processing.sortedVocabulary
import com.sasayaki.domain.provider.LegacyEndpoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

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
        val PAUSE_OTHER_AUDIO = booleanPreferencesKey("pause_other_audio")
        val KEEP_LISTENING_WHEN_KEYBOARD_HIDES = booleanPreferencesKey("keep_listening_when_keyboard_hides")
        val SHOW_BUBBLE = booleanPreferencesKey("show_bubble")
        val CARRY_KEYBOARD_DICTATION = booleanPreferencesKey("carry_keyboard_dictation")
        val SILENCE_THRESHOLD_MS = longPreferencesKey("silence_threshold_ms")
        val LANGUAGE = stringPreferencesKey("language")
        val PREFERRED_LANGUAGES = stringPreferencesKey("preferred_languages")
        val ACTIVE_LANGUAGE = stringPreferencesKey("active_language")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val KEEP_STATS_WITHOUT_HISTORY = booleanPreferencesKey("keep_stats_without_history")
        val HISTORY_RETENTION_LIMIT = intPreferencesKey("history_retention_limit")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WALLPAPER_COLORS = booleanPreferencesKey("wallpaper_colors")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val BUBBLE_SIZE = stringPreferencesKey("bubble_size")
        val CONTROLS_SIZE = stringPreferencesKey("controls_size")
        val BUBBLE_TRANSLUCENT = booleanPreferencesKey("bubble_translucent")
        val BUBBLE_MONOCHROME = booleanPreferencesKey("bubble_monochrome")
        val REMEMBER_PROFILE_PER_APP = booleanPreferencesKey("remember_profile_per_app")
        val SKIP_CLEANUP_IN_AI_CHATS = booleanPreferencesKey("skip_cleanup_in_ai_chats")
        val APP_PROFILES = stringSetPreferencesKey("app_profiles")
        val VOCABULARY = stringSetPreferencesKey("vocabulary")
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
            UserPreferences(
                autoClipboard = prefs[Keys.AUTO_CLIPBOARD] ?: true,
                vibrateOnRecord = prefs[Keys.VIBRATE_ON_RECORD] ?: true,
                pauseOtherAudio = prefs[Keys.PAUSE_OTHER_AUDIO] ?: false,
                keepListeningWhenKeyboardHides = prefs[Keys.KEEP_LISTENING_WHEN_KEYBOARD_HIDES] ?: true,
                showBubble = prefs[Keys.SHOW_BUBBLE] ?: true,
                carryKeyboardDictation = prefs[Keys.CARRY_KEYBOARD_DICTATION] ?: true,
                silenceThresholdMs = prefs[Keys.SILENCE_THRESHOLD_MS] ?: 2000L,
                preferredLanguages = parsePreferredLanguages(prefs),
                activeLanguage = resolveActiveLanguage(prefs),
                historyEnabled = prefs[Keys.HISTORY_ENABLED] ?: true,
                keepStatsWithoutHistory = prefs[Keys.KEEP_STATS_WITHOUT_HISTORY] ?: false,
                historyRetentionLimit = (prefs[Keys.HISTORY_RETENTION_LIMIT] ?: 500).coerceIn(1, 5000),
                startOnBoot = prefs[Keys.START_ON_BOOT] ?: true,
                themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME_MODE] } ?: ThemeMode.SYSTEM,
                wallpaperColors = prefs[Keys.WALLPAPER_COLORS] ?: false,
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
                bubbleSize = bubbleSizeFrom(prefs[Keys.BUBBLE_SIZE]),
                controlsSize = controlsSizeFrom(prefs[Keys.CONTROLS_SIZE], prefs[Keys.BUBBLE_SIZE]),
                bubbleTranslucent = prefs[Keys.BUBBLE_TRANSLUCENT] ?: false,
                bubbleMonochrome = prefs[Keys.BUBBLE_MONOCHROME] ?: false,
                rememberProfilePerApp = prefs[Keys.REMEMBER_PROFILE_PER_APP] ?: true,
                skipCleanupInAiChats = prefs[Keys.SKIP_CLEANUP_IN_AI_CHATS] ?: true,
                appProfiles = decodeAppProfiles(prefs[Keys.APP_PROFILES].orEmpty()),
                vocabulary = sortedVocabulary(prefs[Keys.VOCABULARY].orEmpty())
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /**
     * legacyEndpoints returns the single speech and cleanup endpoints from before
     * providers existed, or null once they have been moved into the providers table.
     */
    suspend fun legacyEndpoints(): Pair<LegacyEndpoint?, LegacyEndpoint?>? {
        val prefs = context.dataStore.data.first()
        val speechUrl = prefs[Keys.ASR_BASE_URL].orEmpty()
        val cleanupUrl = prefs[Keys.LLM_BASE_URL].orEmpty()
        if (speechUrl.isBlank() && cleanupUrl.isBlank()) return null
        val speech = LegacyEndpoint(speechUrl, securePreferencesStore.getAsrApiKey(), prefs[Keys.ASR_MODEL] ?: "whisper-1")
        val cleanup = LegacyEndpoint(cleanupUrl, securePreferencesStore.getLlmApiKey(), prefs[Keys.LLM_MODEL] ?: "gpt-4o-mini")
        return speech to cleanup
    }

    fun clearLegacyApiKeysIfPresent() {
        if (securePreferencesStore.hasLegacyApiKeys()) securePreferencesStore.clearLegacyApiKeys()
    }

    suspend fun clearLegacyEndpoints() {
        securePreferencesStore.clearLegacyApiKeys()
        context.dataStore.edit { prefs ->
            listOf(Keys.ASR_BASE_URL, Keys.ASR_MODEL, Keys.LLM_BASE_URL, Keys.LLM_MODEL).forEach(prefs::remove)
            prefs.remove(Keys.LLM_ENABLED)
        }
    }

    suspend fun setAutoClipboard(enabled: Boolean) = set(Keys.AUTO_CLIPBOARD, enabled)

    suspend fun setVibrateOnRecord(enabled: Boolean) = set(Keys.VIBRATE_ON_RECORD, enabled)

    suspend fun setPauseOtherAudio(enabled: Boolean) = set(Keys.PAUSE_OTHER_AUDIO, enabled)

    suspend fun setKeepListeningWhenKeyboardHides(enabled: Boolean) = set(Keys.KEEP_LISTENING_WHEN_KEYBOARD_HIDES, enabled)

    suspend fun setShowBubble(enabled: Boolean) = set(Keys.SHOW_BUBBLE, enabled)

    suspend fun setCarryKeyboardDictation(enabled: Boolean) = set(Keys.CARRY_KEYBOARD_DICTATION, enabled)

    suspend fun setSilenceThresholdMs(value: Long) = set(Keys.SILENCE_THRESHOLD_MS, value)

    suspend fun setKeepStatsWithoutHistory(enabled: Boolean) = set(Keys.KEEP_STATS_WITHOUT_HISTORY, enabled)

    suspend fun setHistoryRetentionLimit(limit: Int) = set(Keys.HISTORY_RETENTION_LIMIT, limit.coerceIn(1, 5000))

    suspend fun setStartOnBoot(enabled: Boolean) = set(Keys.START_ON_BOOT, enabled)

    suspend fun setHistoryEnabled(enabled: Boolean) = set(Keys.HISTORY_ENABLED, enabled)

    suspend fun setThemeMode(mode: ThemeMode) = set(Keys.THEME_MODE, mode.name)

    suspend fun setWallpaperColors(enabled: Boolean) = set(Keys.WALLPAPER_COLORS, enabled)

    suspend fun setOnboardingCompleted() = set(Keys.ONBOARDING_COMPLETED, true)

    suspend fun setBubbleSize(size: BubbleSize) {
        context.dataStore.edit { prefs ->
            // Pin the controls to the size they had, so they stop following the bubble size.
            if (prefs[Keys.CONTROLS_SIZE] == null) {
                prefs[Keys.CONTROLS_SIZE] = controlsSizeFrom(null, prefs[Keys.BUBBLE_SIZE]).name
            }
            prefs[Keys.BUBBLE_SIZE] = size.name
        }
    }

    suspend fun setControlsSize(size: BubbleSize) = set(Keys.CONTROLS_SIZE, size.name)

    suspend fun setBubbleTranslucent(enabled: Boolean) = set(Keys.BUBBLE_TRANSLUCENT, enabled)

    suspend fun setBubbleMonochrome(enabled: Boolean) = set(Keys.BUBBLE_MONOCHROME, enabled)

    suspend fun setRememberProfilePerApp(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMEMBER_PROFILE_PER_APP] = enabled
            if (!enabled) prefs.remove(Keys.APP_PROFILES)
        }
    }

    suspend fun setSkipCleanupInAiChats(enabled: Boolean) = set(Keys.SKIP_CLEANUP_IN_AI_CHATS, enabled)

    suspend fun rememberAppProfile(packageName: String, profileId: Long) {
        context.dataStore.edit { prefs ->
            val profiles = decodeAppProfiles(prefs[Keys.APP_PROFILES].orEmpty())
            if (profiles[packageName] != profileId) {
                prefs[Keys.APP_PROFILES] = encodeAppProfiles(profiles + (packageName to profileId))
            }
        }
    }

    suspend fun addVocabulary(terms: List<String>) {
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.VOCABULARY].orEmpty()
            val known = existing.map(String::lowercase).toSet()
            prefs[Keys.VOCABULARY] = existing + terms.filter { it.lowercase() !in known }
        }
    }

    suspend fun removeVocabulary(term: String) {
        context.dataStore.edit { prefs -> prefs[Keys.VOCABULARY] = prefs[Keys.VOCABULARY].orEmpty() - term }
    }

    suspend fun forgetAppProfiles() {
        context.dataStore.edit { prefs -> prefs.remove(Keys.APP_PROFILES) }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    suspend fun runStartupMigrations() {
        migrateLegacySecretsIfNeeded(context.dataStore.data.first())
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
