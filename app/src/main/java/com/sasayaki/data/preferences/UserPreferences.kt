package com.sasayaki.data.preferences

data class UserPreferences(
    val asrBaseUrl: String = "",
    val asrApiKey: String = "",
    val asrModel: String = "whisper-1",
    val llmBaseUrl: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "gpt-4o-mini",
    val llmEnabled: Boolean = false,
    val autoClipboard: Boolean = true,
    val vibrateOnRecord: Boolean = true,
    val silenceThresholdMs: Long = 2000,
    val preferredLanguages: List<String> = emptyList(),
    val activeLanguage: String? = null,
    val historyEnabled: Boolean = true,
    val keepStatsWithoutHistory: Boolean = false
)
