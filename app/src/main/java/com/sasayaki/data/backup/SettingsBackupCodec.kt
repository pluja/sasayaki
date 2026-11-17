package com.sasayaki.data.backup

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.sasayaki.data.api.EndpointPolicy
import com.sasayaki.data.preferences.BUBBLE_SIZES
import com.sasayaki.data.preferences.BubbleSize
import com.sasayaki.data.preferences.CONTROLS_SIZES
import com.sasayaki.data.preferences.ThemeMode
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.domain.model.OutputStyle
import com.sasayaki.domain.model.PostProcessingPrompt
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.SummarizeMode
import com.sasayaki.domain.model.TextReplacementRule
import com.sasayaki.domain.processing.MAX_VOCABULARY_TERM_LENGTH
import com.sasayaki.domain.processing.patternError
import com.sasayaki.domain.processing.sortedVocabulary
import com.sasayaki.domain.provider.Provider
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class BackupProblem { NOT_SETTINGS_FILE, NEWER_VERSION, DAMAGED }

class BackupException(val problem: BackupProblem, detail: String = problem.name) : Exception(detail)

/**
 * SettingsSnapshot is everything a settings file carries. Ids are the refs the file links by:
 * profiles point at providers, rules and instructions through them, and
 * preferences.appProfiles maps packages to profile ids.
 */
data class SettingsSnapshot(
    val providers: List<Provider>,
    val rules: List<TextReplacementRule>,
    val instructions: List<PostProcessingPrompt>,
    val profiles: List<Profile>,
    val preferences: UserPreferences
)

object SettingsBackupCodec {
    const val FORMAT = "sasayaki-settings"
    const val VERSION = 1

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun encode(snapshot: SettingsSnapshot): String {
        val prefs = snapshot.preferences
        val instructions = snapshot.instructions.filterNot(PostProcessingPrompt::builtIn)
        val ruleRefs = snapshot.rules.map(TextReplacementRule::id).toSet()
        val instructionRefs = instructions.map(PostProcessingPrompt::id).toSet()
        val file = BackupFile(
            format = FORMAT,
            version = VERSION,
            providers = snapshot.providers.map { BackupProvider(it.id, it.name, it.baseUrl, it.speechModel, it.cleanupModel) },
            rules = snapshot.rules.map { BackupRule(it.id, it.name, it.pattern, it.replacement, it.isRegex) },
            instructions = instructions.map { BackupInstruction(it.id, it.title, it.prompt) },
            profiles = snapshot.profiles.map { profile ->
                BackupProfile(
                    ref = profile.id,
                    name = profile.name,
                    active = profile.isActive,
                    language = profile.language,
                    speechProviderRef = profile.speechProviderId,
                    asrModel = profile.asrModel,
                    llmEnabled = profile.llmEnabled,
                    cleanupProviderRef = profile.cleanupProviderId,
                    llmModel = profile.llmModel,
                    profilePrompt = profile.profilePrompt,
                    outputStyle = profile.outputStyle.name,
                    rewriteMode = profile.rewriteMode.name,
                    summarizeMode = profile.summarizeMode.name,
                    emojiAllowed = profile.emojiAllowed,
                    ruleRefs = profile.selectedRuleIds.filter { it in ruleRefs }.sorted(),
                    instructionRefs = profile.selectedPromptIds.filter { it in instructionRefs }.sorted(),
                    apps = prefs.appProfiles.filterValues { it == profile.id }.keys.sorted()
                )
            },
            vocabulary = prefs.vocabulary,
            preferences = BackupPreferences(
                autoClipboard = prefs.autoClipboard,
                vibrateOnRecord = prefs.vibrateOnRecord,
                pauseOtherAudio = prefs.pauseOtherAudio,
                keepScreenOnWhileRecording = prefs.keepScreenOnWhileRecording,
                silenceThresholdMs = prefs.silenceThresholdMs,
                historyEnabled = prefs.historyEnabled,
                keepStatsWithoutHistory = prefs.keepStatsWithoutHistory,
                historyRetentionLimit = prefs.historyRetentionLimit,
                startOnBoot = prefs.startOnBoot,
                themeMode = prefs.themeMode.name,
                wallpaperColors = prefs.wallpaperColors,
                bubbleSize = prefs.bubbleSize.name,
                controlsSize = prefs.controlsSize.name,
                bubbleTranslucent = prefs.bubbleTranslucent,
                bubbleMonochrome = prefs.bubbleMonochrome,
                rememberProfilePerApp = prefs.rememberProfilePerApp,
                skipCleanupInAiChats = prefs.skipCleanupInAiChats
            )
        )
        return gson.toJson(file)
    }

    /**
     * decode reads a settings file into a snapshot whose refs all resolve. Settings the file
     * leaves out keep their [current] values; links to things the file does not hold are dropped.
     */
    fun decode(json: String, current: UserPreferences): SettingsSnapshot {
        val file = parse(json)
        val providers = file.providers.required("providers").map { it.toProvider() }.uniqueRefs("provider", Provider::id)
        val rules = file.rules.required("rules").map { it.toRule() }.uniqueRefs("rule", TextReplacementRule::id)
        val instructions = file.instructions.required("instructions").map { it.toInstruction() }
            .uniqueRefs("instruction", PostProcessingPrompt::id)
        val backupProfiles = file.profiles.required("profiles").map { it ?: damaged("a profile is empty") }
        if (backupProfiles.isEmpty()) damaged("there are no profiles")
        val links = Links(
            providers = providers.map(Provider::id).toSet(),
            rules = rules.map(TextReplacementRule::id).toSet(),
            instructions = instructions.map(PostProcessingPrompt::id).toSet()
        )
        val profiles = withOneActive(backupProfiles.map { it.toProfile(links) }.uniqueRefs("profile", Profile::id))
        val appProfiles = backupProfiles.zip(profiles)
            .flatMap { (backup, profile) ->
                backup.apps.orEmpty().mapNotNull { app -> app?.trim()?.takeIf(String::isNotEmpty) }.map {
                    it to
                        profile.id
                }
            }
            .distinctBy { it.first }
            .toMap()
        val vocabulary = file.vocabulary?.let(::cleanVocabulary) ?: current.vocabulary
        val preferences = (file.preferences?.applyTo(current) ?: current).copy(appProfiles = appProfiles, vocabulary = vocabulary)
        return SettingsSnapshot(providers, rules, instructions, profiles, preferences)
    }

    private fun parse(json: String): BackupFile {
        val root = try {
            JsonParser.parseString(json)
        } catch (_: JsonParseException) {
            throw BackupException(BackupProblem.NOT_SETTINGS_FILE)
        }
        if (!root.isJsonObject || root.asJsonObject.get("format").asStringOrNull() != FORMAT) {
            throw BackupException(BackupProblem.NOT_SETTINGS_FILE)
        }
        val version = root.asJsonObject.get("version").asIntOrNull() ?: damaged("the version is missing")
        if (version > VERSION) throw BackupException(BackupProblem.NEWER_VERSION)
        if (version < 1) damaged("version $version does not exist")
        return try {
            gson.fromJson(root, BackupFile::class.java)
        } catch (e: JsonParseException) {
            damaged(e.message ?: "a value has the wrong type")
        }
    }

    private class Links(val providers: Set<Long>, val rules: Set<Long>, val instructions: Set<Long>)

    private fun BackupProvider?.toProvider(): Provider {
        val provider = this ?: damaged("a provider is empty")
        return Provider(
            id = provider.ref ?: damaged("a provider has no ref"),
            name = provider.name?.trim().orEmpty(),
            baseUrl = provider.baseUrl?.trim()?.takeIf(String::isNotEmpty)
                ?.also { url -> if (runCatching { EndpointPolicy.normalizeBaseUrl(url) }.isFailure) damaged("a provider has an invalid server address") }
                ?: damaged("a provider has no server address"),
            speechModel = provider.speechModel?.trim()?.takeIf(String::isNotEmpty),
            cleanupModel = provider.cleanupModel?.trim()?.takeIf(String::isNotEmpty)
        )
    }

    private fun BackupRule?.toRule(): TextReplacementRule {
        val rule = this ?: damaged("a rule is empty")
        val pattern = rule.pattern?.takeIf(String::isNotEmpty) ?: damaged("a rule has no pattern")
        if (pattern.length > MAX_RULE_PATTERN_LENGTH) damaged("a rule pattern is too long")
        return TextReplacementRule(
            id = rule.ref ?: damaged("a rule has no ref"),
            name = rule.name.orEmpty(),
            pattern = pattern,
            replacement = rule.replacement.orEmpty(),
            isRegex = rule.isRegex ?: false
        ).also { if (it.patternError() != null) damaged("a rule has an invalid pattern") }
    }

    private fun BackupInstruction?.toInstruction(): PostProcessingPrompt {
        val instruction = this ?: damaged("an instruction is empty")
        return PostProcessingPrompt(
            id = instruction.ref ?: damaged("an instruction has no ref"),
            title = instruction.title?.trim().orEmpty(),
            prompt = instruction.prompt?.trim()?.takeIf(String::isNotEmpty) ?: damaged("an instruction has no text"),
            builtIn = false
        )
    }

    private fun BackupProfile.toProfile(links: Links): Profile {
        val speechProvider = speechProviderRef?.takeIf { it in links.providers }
        val cleanupProvider = cleanupProviderRef?.takeIf { it in links.providers }
        return Profile(
            id = ref ?: damaged("a profile has no ref"),
            name = name?.trim().orEmpty(),
            isActive = active ?: false,
            speechProviderId = speechProvider,
            asrModel = if (speechProvider == null) "" else asrModel?.trim().orEmpty(),
            language = language?.trim()?.lowercase()?.takeIf(String::isNotEmpty),
            llmEnabled = llmEnabled ?: false,
            cleanupProviderId = cleanupProvider,
            llmModel = if (cleanupProvider == null) "" else llmModel?.trim().orEmpty(),
            profilePrompt = profilePrompt?.trim().orEmpty(),
            outputStyle = enumOrDefault(outputStyle, OutputStyle.STANDARD, "outputStyle"),
            rewriteMode = enumOrDefault(rewriteMode, RewriteMode.FIX, "rewriteMode"),
            summarizeMode = enumOrDefault(summarizeMode, SummarizeMode.NONE, "summarizeMode"),
            emojiAllowed = emojiAllowed ?: false,
            selectedRuleIds = ruleRefs.refsIn(links.rules, "ruleRefs"),
            selectedPromptIds = instructionRefs.refsIn(links.instructions, "instructionRefs")
        )
    }

    private fun BackupPreferences.applyTo(current: UserPreferences): UserPreferences = current.copy(
        autoClipboard = autoClipboard ?: current.autoClipboard,
        vibrateOnRecord = vibrateOnRecord ?: current.vibrateOnRecord,
        pauseOtherAudio = pauseOtherAudio ?: current.pauseOtherAudio,
        keepScreenOnWhileRecording = keepScreenOnWhileRecording ?: current.keepScreenOnWhileRecording,
        silenceThresholdMs = silenceThresholdMs?.coerceIn(MIN_SILENCE_MS, MAX_SILENCE_MS) ?: current.silenceThresholdMs,
        historyEnabled = historyEnabled ?: current.historyEnabled,
        keepStatsWithoutHistory = keepStatsWithoutHistory ?: current.keepStatsWithoutHistory,
        historyRetentionLimit = historyRetentionLimit?.coerceIn(1, MAX_RETENTION) ?: current.historyRetentionLimit,
        startOnBoot = startOnBoot ?: current.startOnBoot,
        themeMode = enumOrDefault(themeMode, current.themeMode, "themeMode"),
        wallpaperColors = wallpaperColors ?: current.wallpaperColors,
        bubbleSize = enumOrDefault(bubbleSize, current.bubbleSize, "bubbleSize").also {
            if (it !in BUBBLE_SIZES) damaged("bubbleSize $it is not offered")
        },
        controlsSize = enumOrDefault<BubbleSize>(controlsSize, current.controlsSize, "controlsSize").also {
            if (it !in CONTROLS_SIZES) damaged("controlsSize $it is not offered")
        },
        bubbleTranslucent = bubbleTranslucent ?: current.bubbleTranslucent,
        bubbleMonochrome = bubbleMonochrome ?: current.bubbleMonochrome,
        rememberProfilePerApp = rememberProfilePerApp ?: current.rememberProfilePerApp,
        skipCleanupInAiChats = skipCleanupInAiChats ?: current.skipCleanupInAiChats
    )

    private fun withOneActive(profiles: List<Profile>): List<Profile> {
        val activeId = profiles.firstOrNull(Profile::isActive)?.id ?: profiles.first().id
        return profiles.map { it.copy(isActive = it.id == activeId) }
    }

    private fun cleanVocabulary(terms: List<String?>): List<String> = sortedVocabulary(
        terms.mapNotNull { it?.trim()?.takeIf { term -> term.isNotEmpty() && term.length <= MAX_VOCABULARY_TERM_LENGTH } }
            .distinctBy(String::lowercase)
    )

    private fun List<Long?>?.refsIn(known: Set<Long>, field: String): Set<Long> =
        orEmpty().map { it ?: damaged("$field holds an empty ref") }.filter { it in known }.toSet()

    private fun <T> List<T>?.required(field: String): List<T> = this ?: damaged("$field are missing")

    private fun <T> List<T>.uniqueRefs(kind: String, ref: (T) -> Long): List<T> {
        if (map(ref).toSet().size != size) damaged("two of the ${kind}s share a ref")
        return this
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T, field: String): T {
        if (name == null) return default
        return enumValues<T>().firstOrNull { it.name == name } ?: damaged("$field $name is unknown")
    }

    private fun JsonElement?.asStringOrNull(): String? = this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonElement?.asIntOrNull(): Int? =
        this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asString?.toIntOrNull()

    private fun damaged(detail: String): Nothing = throw BackupException(BackupProblem.DAMAGED, detail)

    private const val MIN_SILENCE_MS = 500L
    private const val MAX_SILENCE_MS = 5000L
    private const val MAX_RETENTION = 5000
    private const val MAX_RULE_PATTERN_LENGTH = 500
}

/** profilesForInsert returns the snapshot's profiles linked to the ids their refs were saved under. */
fun SettingsSnapshot.profilesForInsert(
    providerIds: Map<Long, Long>,
    ruleIds: Map<Long, Long>,
    instructionIds: Map<Long, Long>
): List<Profile> = profiles.map { profile ->
    profile.copy(
        id = 0,
        speechProviderId = profile.speechProviderId?.let(providerIds::getValue),
        cleanupProviderId = profile.cleanupProviderId?.let(providerIds::getValue),
        selectedRuleIds = profile.selectedRuleIds.mapTo(mutableSetOf(), ruleIds::getValue),
        selectedPromptIds = profile.selectedPromptIds.mapTo(mutableSetOf(), instructionIds::getValue)
    )
}

fun SettingsSnapshot.appProfilesFor(profileIds: Map<Long, Long>): Map<String, Long> =
    preferences.appProfiles.mapValues { (_, ref) -> profileIds.getValue(ref) }

/**
 * carriedApiKeys keeps the key of each [previous] provider for the restored provider on the same
 * server, since settings files never hold keys. Two accounts on one server are told apart by name;
 * a server match alone only counts when one previous provider there had a key. [restored] maps
 * new provider ids to the providers saved under them.
 */
fun carriedApiKeys(previous: List<Provider>, restored: Map<Long, Provider>): Map<Long, String> {
    val withKeys = previous.filter { it.apiKey.isNotBlank() }
    val byServerAndName = withKeys.associateBy { serverKey(it.baseUrl) to it.name.trim() }
    val byServer = withKeys.groupBy { serverKey(it.baseUrl) }
    return restored.mapNotNull { (id, provider) ->
        val server = serverKey(provider.baseUrl)
        val match = byServerAndName[server to provider.name.trim()] ?: byServer[server]?.singleOrNull()
        match?.let { id to it.apiKey }
    }.toMap()
}

private fun serverKey(url: String): String =
    runCatching { EndpointPolicy.normalizeBaseUrl(url) }.getOrElse { url.trim().trimEnd('/').lowercase() }

/**
 * retentionAfterImport applies the file's history limit unless it would erase dictations this
 * phone already keeps; an import replaces settings, never history.
 */
fun retentionAfterImport(imported: Int, current: Int, historyCount: Int): Int = if (imported < historyCount) current else imported

fun backupFileName(date: LocalDate): String = "sasayaki-settings-${date.format(DateTimeFormatter.BASIC_ISO_DATE)}.json"
