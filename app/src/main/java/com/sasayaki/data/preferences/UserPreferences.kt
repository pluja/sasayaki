package com.sasayaki.data.preferences

data class UserPreferences(
    val autoClipboard: Boolean = true,
    val vibrateOnRecord: Boolean = true,
    val pauseOtherAudio: Boolean = false,
    // showBubble remembers whether the person wants the bubble, so a boot or an update brings it
    // back only when it was on.
    val showBubble: Boolean = true,
    val silenceThresholdMs: Long = 2000,
    val preferredLanguages: List<String> = emptyList(),
    val activeLanguage: String? = null,
    val historyEnabled: Boolean = true,
    val keepStatsWithoutHistory: Boolean = false,
    val historyRetentionLimit: Int = 500,
    val startOnBoot: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val wallpaperColors: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val bubbleSize: BubbleSize = BubbleSize.STANDARD,
    val controlsSize: BubbleSize = BubbleSize.STANDARD,
    val bubbleTranslucent: Boolean = false,
    val bubbleMonochrome: Boolean = false,
    val rememberProfilePerApp: Boolean = true,
    val skipCleanupInAiChats: Boolean = true,
    val appProfiles: Map<String, Long> = emptyMap(), // package name to profile id
    val vocabulary: List<String> = emptyList()
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class BubbleSize(val scale: Float) {
    TINY(0.65f),
    SMALL(0.85f),
    STANDARD(1f),
    LARGE(1.15f),
    LARGER(1.3f)
}

internal fun bubbleSizeNamed(name: String?): BubbleSize? = BubbleSize.entries.firstOrNull { it.name == name }

/**
 * controlsSizeFrom falls back to the bubble size, because one setting sized both before they
 * were split; the first bubble size change pins the controls. Tiny and Small are only offered
 * for the bubble, so they never carry over to the controls.
 */
internal fun controlsSizeFrom(controls: String?, bubble: String?): BubbleSize =
    (bubbleSizeNamed(controls) ?: bubbleSizeNamed(bubble))?.takeIf { it in CONTROLS_SIZES } ?: BubbleSize.STANDARD

val BUBBLE_SIZES = listOf(BubbleSize.TINY, BubbleSize.SMALL, BubbleSize.STANDARD, BubbleSize.LARGE)
val CONTROLS_SIZES = listOf(BubbleSize.STANDARD, BubbleSize.LARGE, BubbleSize.LARGER)

// bubbleSizeFrom keeps a bubble that was set to Huge before that option moved to the controls only.
internal fun bubbleSizeFrom(bubble: String?): BubbleSize = bubbleSizeNamed(bubble)?.coerceAtMost(BubbleSize.LARGE) ?: BubbleSize.STANDARD

private const val APP_PROFILE_SEPARATOR = '='

internal fun encodeAppProfiles(profiles: Map<String, Long>): Set<String> =
    profiles.map { (packageName, id) -> "$packageName$APP_PROFILE_SEPARATOR$id" }.toSet()

internal fun decodeAppProfiles(entries: Set<String>): Map<String, Long> = entries.mapNotNull { entry ->
    val packageName = entry.substringBeforeLast(APP_PROFILE_SEPARATOR, missingDelimiterValue = "")
    val id = entry.substringAfterLast(APP_PROFILE_SEPARATOR).toLongOrNull()
    if (packageName.isEmpty() || id == null) null else packageName to id
}.toMap()

fun UserPreferences.rememberedProfileFor(packageName: String?): Long? = packageName?.takeIf { rememberProfilePerApp }?.let(appProfiles::get)
