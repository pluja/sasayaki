package com.sasayaki.data.backup

import com.sasayaki.data.preferences.BubbleSize
import com.sasayaki.data.preferences.ThemeMode
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.domain.model.OutputStyle
import com.sasayaki.domain.model.PostProcessingPrompt
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.SummarizeMode
import com.sasayaki.domain.model.TextReplacementRule
import com.sasayaki.domain.provider.Provider
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupCodecTest {
    private val providers = listOf(
        Provider(7, "Home server", "https://asr.home.lan/v1", apiKey = "sk-home-secret", speechModel = "whisper-large"),
        Provider(9, "llama-swap", "http://192.168.1.20:8080", apiKey = "", cleanupModel = "gpt-oss-120b")
    )
    private val rules =
        listOf(
            TextReplacementRule(3, "Product", "sasa yaki", "Sasayaki"),
            TextReplacementRule(4, "", "(\\d+) percent", "$1%", isRegex = true)
        )
    private val instructions = listOf(PostProcessingPrompt(21, "Digits", "Always write numbers as digits."))
    private val builtIn = PostProcessingPrompt(1, "Core", "Built-in text", builtIn = true)
    private val profiles = listOf(
        Profile(
            id = 11,
            name = "Chat",
            isActive = false,
            speechProviderId = 7,
            asrModel = "whisper-small",
            language = "en",
            llmEnabled = true,
            cleanupProviderId = 9,
            profilePrompt = "Keep it casual.",
            outputStyle = OutputStyle.RELAXED,
            rewriteMode = RewriteMode.POLISH,
            summarizeMode = SummarizeMode.LIGHT,
            emojiAllowed = true,
            selectedRuleIds = setOf(3, 4),
            selectedPromptIds = setOf(21, 1)
        ),
        Profile(id = 12, name = "Email", isActive = true, speechProviderId = 7)
    )
    private val preferences = UserPreferences(
        autoClipboard = false,
        silenceThresholdMs = 3500,
        historyRetentionLimit = 1000,
        themeMode = ThemeMode.DARK,
        bubbleSize = BubbleSize.TINY,
        controlsSize = BubbleSize.LARGER,
        appProfiles = mapOf("com.slack" to 11L, "com.google.android.gm" to 12L),
        vocabulary = listOf("Kubernetes", "Sasayaki"),
        onboardingCompleted = true
    )
    private val snapshot = SettingsSnapshot(providers, rules, instructions + builtIn, profiles, preferences)

    private fun decode(json: String, current: UserPreferences = UserPreferences()) = SettingsBackupCodec.decode(json, current)

    private fun problemOf(json: String): BackupProblem = assertThrows(BackupException::class.java) { decode(json) }.problem

    private fun minimalFile(extra: String = "", profiles: String = """[{"ref": 1, "name": "Default"}]""") =
        """{"format": "sasayaki-settings", "version": 1, "providers": [], "rules": [], "instructions": [], "profiles": $profiles $extra}"""

    @Test
    fun `a file round trips everything it carries`() {
        val decoded = decode(SettingsBackupCodec.encode(snapshot))

        assertEquals(providers.map { it.copy(apiKey = "") }, decoded.providers)
        assertEquals(rules.map { it.copy(createdAt = 0) }, decoded.rules.map { it.copy(createdAt = 0) })
        assertEquals(instructions.map { it.copy(createdAt = 0) }, decoded.instructions.map { it.copy(createdAt = 0) })
        assertEquals(profiles[0].copy(selectedPromptIds = setOf(21)), decoded.profiles[0])
        assertEquals(profiles[1], decoded.profiles[1])
        assertEquals(preferences.copy(onboardingCompleted = false), decoded.preferences)
    }

    @Test
    fun `api keys and built-in instructions are never written`() {
        val json = SettingsBackupCodec.encode(snapshot)

        assertFalse(json.contains("sk-home-secret"))
        assertFalse(json.contains("apiKey", ignoreCase = true))
        assertFalse(json.contains("Built-in text"))
    }

    @Test
    fun `settings the file leaves out keep their current values`() {
        val current = UserPreferences(themeMode = ThemeMode.LIGHT, vocabulary = listOf("Grafana"), onboardingCompleted = true)

        val decoded = decode(minimalFile(), current)

        assertEquals(current.copy(appProfiles = emptyMap()), decoded.preferences)
    }

    @Test
    fun `links to things the file does not hold are dropped`() {
        val decoded = decode(
            minimalFile(
                profiles = """[{"ref": 1, "name": "A", "speechProviderRef": 5, "asrModel": "x", "ruleRefs": [8], "instructionRefs": [9]}]"""
            )
        )

        val profile = decoded.profiles.single()
        assertEquals(null, profile.speechProviderId)
        assertEquals("", profile.asrModel)
        assertTrue(profile.selectedRuleIds.isEmpty())
        assertTrue(profile.selectedPromptIds.isEmpty())
    }

    @Test
    fun `exactly one profile ends up active`() {
        val none = decode(minimalFile(profiles = """[{"ref": 1, "name": "A"}, {"ref": 2, "name": "B"}]"""))
        val both = decode(minimalFile(profiles = """[{"ref": 1, "name": "A", "active": true}, {"ref": 2, "name": "B", "active": true}]"""))

        assertEquals(listOf(true, false), none.profiles.map(Profile::isActive))
        assertEquals(listOf(true, false), both.profiles.map(Profile::isActive))
    }

    @Test
    fun `refs are remapped to the ids the rows were saved under`() {
        val decoded = decode(SettingsBackupCodec.encode(snapshot))

        val inserted = decoded.profilesForInsert(
            providerIds = mapOf(7L to 107L, 9L to 109L),
            ruleIds = mapOf(3L to 103L, 4L to 104L),
            instructionIds = mapOf(21L to 121L)
        )
        val apps = decoded.appProfilesFor(mapOf(11L to 211L, 12L to 212L))

        assertEquals(0L, inserted[0].id)
        assertEquals(107L, inserted[0].speechProviderId)
        assertEquals(109L, inserted[0].cleanupProviderId)
        assertEquals(setOf(103L, 104L), inserted[0].selectedRuleIds)
        assertEquals(setOf(121L), inserted[0].selectedPromptIds)
        assertEquals(mapOf("com.slack" to 211L, "com.google.android.gm" to 212L), apps)
    }

    @Test
    fun `keys carry over only to providers on the same server`() {
        val carried = carriedApiKeys(providers, mapOf(50L to "https://asr.home.lan", 51L to "https://other.example"))

        assertEquals(mapOf(50L to "sk-home-secret"), carried)
    }

    @Test
    fun `an import never shrinks history below what is already kept`() {
        assertEquals(500, retentionAfterImport(imported = 50, current = 500, historyCount = 120))
        assertEquals(50, retentionAfterImport(imported = 50, current = 500, historyCount = 0))
        assertEquals(1000, retentionAfterImport(imported = 1000, current = 500, historyCount = 400))
    }

    @Test
    fun `files that are not settings files are refused`() {
        assertEquals(BackupProblem.NOT_SETTINGS_FILE, problemOf("not json at all {"))
        assertEquals(BackupProblem.NOT_SETTINGS_FILE, problemOf("""["a list"]"""))
        assertEquals(BackupProblem.NOT_SETTINGS_FILE, problemOf("""{"format": "something-else", "version": 1}"""))
        assertEquals(BackupProblem.NOT_SETTINGS_FILE, problemOf(""))
    }

    @Test
    fun `files from a newer version are refused`() {
        assertEquals(BackupProblem.NEWER_VERSION, problemOf("""{"format": "sasayaki-settings", "version": 2}"""))
    }

    @Test
    fun `hand-edited files with broken values are refused instead of crashing`() {
        listOf(
            """{"format": "sasayaki-settings"}""",
            """{"format": "sasayaki-settings", "version": "one"}""",
            minimalFile(profiles = "[]"),
            minimalFile(profiles = """[{"name": "No ref"}]"""),
            minimalFile(profiles = """[{"ref": 1, "name": "A"}, {"ref": 1, "name": "B"}]"""),
            minimalFile(profiles = """[{"ref": 1, "outputStyle": "LOUD"}]"""),
            minimalFile(profiles = """[{"ref": 1, "emojiAllowed": 3}]"""),
            minimalFile(profiles = """[null]"""),
            """{"format": "sasayaki-settings", "version": 1, "rules": [], "instructions": [], "profiles": [{"ref": 1}]}""",
            minimalFile().replace("\"providers\": []", "\"providers\": [{\"ref\": 1, \"name\": \"No address\"}]"),
            minimalFile().replace("\"rules\": []", "\"rules\": [{\"ref\": 1, \"replacement\": \"x\"}]"),
            minimalFile(""", "preferences": {"themeMode": "PURPLE"}"""),
            minimalFile(""", "preferences": {"controlsSize": "TINY"}"""),
            minimalFile(""", "preferences": {"silenceThresholdMs": "slow"}""")
        ).forEach { json ->
            assertEquals(json, BackupProblem.DAMAGED, problemOf(json))
        }
    }

    @Test
    fun `out of range numbers and messy vocabulary are cleaned up`() {
        val decoded = decode(
            minimalFile(
                """, "vocabulary": ["  vLLM ", "vllm", "", null, "${"x".repeat(
                    61
                )}"], "preferences": {"silenceThresholdMs": 99999, "historyRetentionLimit": 0}"""
            )
        )

        assertEquals(listOf("vLLM"), decoded.preferences.vocabulary)
        assertEquals(5000L, decoded.preferences.silenceThresholdMs)
        assertEquals(1, decoded.preferences.historyRetentionLimit)
    }

    @Test
    fun `the suggested file name carries the date`() {
        assertEquals("sasayaki-settings-20261117.json", backupFileName(LocalDate.of(2026, 11, 17)))
    }
}
