package com.sasayaki.data.backup

// The classes here mirror the settings file. Every field is nullable because Gson fills in
// whatever a file holds, including hand-edited ones; SettingsBackupCodec validates them.
// API keys have no field on purpose, so they can never be written to a file.

internal class BackupFile(
    val format: String? = null,
    val version: Int? = null,
    val providers: List<BackupProvider?>? = null,
    val rules: List<BackupRule?>? = null,
    val instructions: List<BackupInstruction?>? = null,
    val profiles: List<BackupProfile?>? = null,
    val vocabulary: List<String?>? = null,
    val preferences: BackupPreferences? = null
)

internal class BackupProvider(
    val ref: Long? = null,
    val name: String? = null,
    val baseUrl: String? = null,
    val speechModel: String? = null,
    val cleanupModel: String? = null
)

internal class BackupRule(
    val ref: Long? = null,
    val name: String? = null,
    val pattern: String? = null,
    val replacement: String? = null,
    val isRegex: Boolean? = null
)

internal class BackupInstruction(val ref: Long? = null, val title: String? = null, val prompt: String? = null)

internal class BackupProfile(
    val ref: Long? = null,
    val name: String? = null,
    val active: Boolean? = null,
    val language: String? = null,
    val speechProviderRef: Long? = null,
    val asrModel: String? = null,
    val llmEnabled: Boolean? = null,
    val cleanupProviderRef: Long? = null,
    val llmModel: String? = null,
    val profilePrompt: String? = null,
    val outputStyle: String? = null,
    val rewriteMode: String? = null,
    val summarizeMode: String? = null,
    val emojiAllowed: Boolean? = null,
    val ruleRefs: List<Long?>? = null,
    val instructionRefs: List<Long?>? = null,
    val apps: List<String?>? = null
)

internal class BackupPreferences(
    val autoClipboard: Boolean? = null,
    val vibrateOnRecord: Boolean? = null,
    val pauseOtherAudio: Boolean? = null,
    val keepScreenOnWhileRecording: Boolean? = null,
    val silenceThresholdMs: Long? = null,
    val historyEnabled: Boolean? = null,
    val keepStatsWithoutHistory: Boolean? = null,
    val historyRetentionLimit: Int? = null,
    val startOnBoot: Boolean? = null,
    val themeMode: String? = null,
    val wallpaperColors: Boolean? = null,
    val bubbleSize: String? = null,
    val controlsSize: String? = null,
    val bubbleTranslucent: Boolean? = null,
    val bubbleMonochrome: Boolean? = null,
    val rememberProfilePerApp: Boolean? = null,
    val skipCleanupInAiChats: Boolean? = null
)
