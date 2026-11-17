package com.sasayaki.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sasayaki.data.audio.RetainedAudioStore
import com.sasayaki.data.db.SasayakiDatabase
import com.sasayaki.data.db.entity.Dictation
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.DictationStatus
import com.sasayaki.domain.model.Profile
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class HistoryRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val now = Instant.parse("2026-10-24T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val preferences = MutableStateFlow(UserPreferences())
    private val profile = Profile(id = 1, name = "Chat")
    private lateinit var database: SasayakiDatabase
    private lateinit var audioDirectory: File
    private lateinit var repository: HistoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SasayakiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        audioDirectory = File(temp.root, "retained_audio")
        repository = HistoryRepository(
            dictationDao = database.dictationDao(),
            lifetimeStatsDao = database.lifetimeStatsDao(),
            audioStore = RetainedAudioStore(audioDirectory),
            preferences = preferences,
            clock = clock
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `history shows every entry the retention setting keeps`() = runBlocking {
        preferences.update { it.copy(historyRetentionLimit = 1000) }
        repeat(600) { insertVisible(timestamp = now.toEpochMilli() - it) }

        assertEquals(600, repository.history.first().size)
    }

    @Test
    fun `history count ignores entries removed from history`() = runBlocking {
        repeat(3) { insertVisible(timestamp = now.toEpochMilli() - it) }
        repository.removeFromHistory(repository.history.first().first().id)

        assertEquals(2, repository.historyCount.first())
    }

    @Test
    fun `entries beyond the retention limit are deleted with their audio`() = runBlocking {
        preferences.update { it.copy(historyRetentionLimit = 2) }
        repeat(3) { repository.recordSuccess(record(), text = "entry $it", rawText = "entry $it") }

        assertEquals(listOf("entry 2", "entry 1"), repository.history.first().map { it.text })
        assertEquals(2, audioDirectory.listFiles()!!.size)
    }

    @Test
    fun `lowering the retention limit prunes at once`() = runBlocking {
        repeat(5) { repository.recordSuccess(record(), text = "entry $it", rawText = "") }

        preferences.update { it.copy(historyRetentionLimit = 1) }
        repository.prune()

        assertEquals(1, repository.history.first().size)
        assertEquals(1, audioDirectory.listFiles()!!.size)
    }

    @Test
    fun `audio without a row is swept`() = runBlocking {
        repository.recordSuccess(record(), text = "kept", rawText = "kept")
        val orphan = File(audioDirectory, "dictation_orphan.wav").apply { writeText("orphan") }

        repository.prune()

        assertFalse(orphan.exists())
        assertEquals(1, audioDirectory.listFiles()!!.size)
    }

    @Test
    fun `a failure with history on can be retried`() = runBlocking {
        val id = repository.recordFailure(record(), "timeout")

        assertNotNull(id)
        assertNotNull(database.dictationDao().getAudioPath(id!!))
    }

    @Test
    fun `nothing is stored or counted when history and word counting are off`() = runBlocking {
        preferences.update { it.copy(historyEnabled = false, keepStatsWithoutHistory = false) }

        repository.recordSuccess(record(), text = "secret words", rawText = "secret words")
        val failureId = repository.recordFailure(record(), "timeout")

        assertNull(failureId)
        assertNull(database.dictationDao().getById(1))
        assertNull(database.lifetimeStatsDao().observe().first())
        assertFalse(audioDirectory.exists() && audioDirectory.listFiles()!!.isNotEmpty())
    }

    @Test
    fun `word counting without history keeps totals but no text`() = runBlocking {
        preferences.update { it.copy(historyEnabled = false, keepStatsWithoutHistory = true) }

        repository.recordSuccess(record(), text = "three secret words", rawText = "three secret words")

        val stored = database.dictationDao().getById(1)!!
        assertEquals("", stored.text)
        assertEquals("", stored.rawText)
        assertNull(stored.audioPath)
        assertNull(stored.sourceAppPackage)
        assertEquals(3, database.lifetimeStatsDao().observe().first()!!.wordCount)
        assertTrue(repository.history.first().isEmpty())
    }

    @Test
    fun `hidden rows are dropped once they no longer count towards today`() = runBlocking {
        val startOfToday = Instant.parse("2026-10-24T00:00:00Z").toEpochMilli()
        insertHidden(timestamp = startOfToday - 1)
        insertHidden(timestamp = startOfToday + 1)

        repository.prune()

        val stats = database.dictationDao().getTodayStats(startOfToday).first()
        assertEquals(1, stats.count)
        assertNull(database.dictationDao().getById(1))
    }

    @Test
    fun `a successful retry replaces the failure and its audio`() = runBlocking {
        val id = repository.recordFailure(record(), "timeout")!!
        val failedAudio = File(database.dictationDao().getAudioPath(id)!!)

        repository.recordSuccess(record(audio = failedAudio, retryEntryId = id), text = "done", rawText = "done")

        val entry = database.dictationDao().getById(id)!!
        assertEquals(DictationStatus.SUCCESS.name, entry.status)
        assertEquals(failedAudio.absolutePath, entry.audioPath)
        assertEquals(1, audioDirectory.listFiles()!!.size)
    }

    @Test
    fun `an entry deleted while its retry runs stays deleted`() = runBlocking {
        val id = repository.recordFailure(record(), "timeout")!!
        val failedAudio = File(database.dictationDao().getAudioPath(id)!!)
        repository.removeFromHistory(id)

        repository.recordSuccess(record(audio = failedAudio, retryEntryId = id), text = "done", rawText = "done")

        assertTrue(repository.history.first().isEmpty())
        assertEquals("", database.dictationDao().getById(id)!!.text)
    }

    private fun record(audio: File = recording(), retryEntryId: Long? = null) = DictationRecord(
        audio = audio,
        durationMs = 1000,
        appContext = AppContext(label = "Signal", packageName = "org.thoughtcrime.securesms"),
        profile = profile,
        retryEntryId = retryEntryId
    )

    private fun recording(): File = temp.newFile().apply { writeText("audio") }

    private suspend fun insertVisible(timestamp: Long) {
        database.dictationDao().insert(Dictation(text = "entry", rawText = "entry", wordCount = 1, timestamp = timestamp))
    }

    private suspend fun insertHidden(timestamp: Long) {
        database.dictationDao().insert(
            Dictation(text = "", rawText = "", wordCount = 2, timestamp = timestamp, historyVisible = false)
        )
    }
}
