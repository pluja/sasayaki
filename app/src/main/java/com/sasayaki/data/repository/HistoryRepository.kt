package com.sasayaki.data.repository

import android.util.Log
import com.sasayaki.data.audio.RetainedAudioStore
import com.sasayaki.data.db.dao.DictationDao
import com.sasayaki.data.db.dao.LifetimeStatsDao
import com.sasayaki.data.db.entity.Dictation
import com.sasayaki.data.db.entity.DictationSummary
import com.sasayaki.data.preferences.PreferencesDataStore
import com.sasayaki.data.preferences.UserPreferences
import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.DictationStatus
import com.sasayaki.domain.model.Profile
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * HistoryRepository is the only writer of dictation rows and retained audio, so the
 * history settings (what is stored, kept and counted) are applied in one place.
 */
@Singleton
class HistoryRepository internal constructor(
    private val dictationDao: DictationDao,
    private val lifetimeStatsDao: LifetimeStatsDao,
    private val audioStore: RetainedAudioStore,
    private val preferences: Flow<UserPreferences>,
    private val clock: Clock
) {
    @Inject constructor(
        dictationDao: DictationDao,
        lifetimeStatsDao: LifetimeStatsDao,
        audioStore: RetainedAudioStore,
        preferencesDataStore: PreferencesDataStore
    ) : this(dictationDao, lifetimeStatsDao, audioStore, preferencesDataStore.preferences, Clock.systemDefaultZone())

    // Serialises row and file writes, so the orphan sweep in prune() never sees audio
    // that has been retained but whose row is not written yet.
    private val writes = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: Flow<List<DictationSummary>> = preferences
        .map { it.historyRetentionLimit }
        .distinctUntilChanged()
        .flatMapLatest(dictationDao::getRecent)

    val historyCount: Flow<Int> = dictationDao.countVisible()

    fun recent(limit: Int): Flow<List<DictationSummary>> = dictationDao.getRecent(limit)

    suspend fun entry(id: Long): Dictation? = dictationDao.getById(id)

    suspend fun getRawText(id: Long): String? = dictationDao.getRawText(id)

    suspend fun recordSuccess(dictation: DictationRecord, text: String, rawText: String) = writes.withLock {
        if (dictation.incognito) return@withLock
        val prefs = preferences.first()
        val wordCount = countWords(text)
        if (prefs.historyEnabled || prefs.keepStatsWithoutHistory) {
            recordLifetimeStats(wordCount, dictation.durationMs)
        }
        val keepRow = prefs.historyEnabled || prefs.keepStatsWithoutHistory || dictation.retryEntryId != null
        if (!keepRow) return@withLock
        save(
            dictation = dictation,
            visible = prefs.historyEnabled,
            status = DictationStatus.SUCCESS,
            text = text,
            rawText = rawText,
            wordCount = wordCount,
            errorMessage = null
        )
        pruneLocked(prefs.historyRetentionLimit)
    }

    /** recordFailure returns the entry the failure can be retried from, if one was kept. */
    suspend fun recordFailure(dictation: DictationRecord, message: String): Long? = writes.withLock {
        if (dictation.incognito) return@withLock null
        val prefs = preferences.first()
        if (!prefs.historyEnabled && dictation.retryEntryId == null) return@withLock null
        val saved = save(
            dictation = dictation,
            visible = prefs.historyEnabled,
            status = DictationStatus.FAILURE,
            text = "",
            rawText = "",
            wordCount = 0,
            errorMessage = message
        )
        pruneLocked(prefs.historyRetentionLimit)
        saved.id.takeIf { saved.audioPath != null }
    }

    suspend fun removeFromHistory(id: Long) = writes.withLock {
        dictationDao.getAudioPath(id)?.let { audioStore.delete(it) }
        dictationDao.removeFromHistory(id)
    }

    suspend fun removeAllFromHistory() = writes.withLock {
        dictationDao.getAllVisibleAudioPaths().forEach { audioStore.delete(it) }
        dictationDao.removeAllFromHistory()
    }

    /**
     * prune enforces the retention limit, drops hidden rows once they no longer feed
     * today's totals, and deletes retained audio no row points to.
     */
    suspend fun prune() = writes.withLock {
        pruneLocked(preferences.first().historyRetentionLimit)
    }

    private suspend fun pruneLocked(limit: Int) {
        dictationDao.getPrunableAudioPaths(limit).forEach { audioStore.delete(it) }
        dictationDao.pruneOldEntries(limit)
        val startOfToday = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        dictationDao.pruneHiddenEntries(before = startOfToday)
        audioStore.deleteAllExcept(dictationDao.getAllAudioPaths().toSet())
    }

    private suspend fun save(
        dictation: DictationRecord,
        visible: Boolean,
        status: DictationStatus,
        text: String,
        rawText: String,
        wordCount: Int,
        errorMessage: String?
    ): SavedEntry {
        val retryEntryId = dictation.retryEntryId
        val retried = retryEntryId?.let { dictationDao.getById(it) }
        // An entry deleted while its retry ran stays deleted.
        if (retryEntryId != null && retried?.historyVisible != true) return SavedEntry(retryEntryId, audioPath = null)
        val previousAudioPath = retried?.audioPath
        // A hidden row only feeds today's totals, so it keeps no content at all.
        val appContext = dictation.appContext.takeIf { visible }
        val storedText = text.takeIf { visible }.orEmpty()
        val storedRawText = rawText.takeIf { visible }.orEmpty()
        // Retaining and writing run together, so a cancel cannot leave a copy without a row.
        var audioPath: String? = null
        val id = try {
            withContext(NonCancellable) {
                audioPath = if (visible) audioStore.retain(dictation.audio) else null
                if (retryEntryId == null) {
                    dictationDao.insert(
                        Dictation(
                            text = storedText,
                            rawText = storedRawText,
                            wordCount = wordCount,
                            timestamp = clock.millis(),
                            sourceApp = appContext?.label,
                            sourceAppPackage = appContext?.packageName,
                            durationMs = dictation.durationMs,
                            historyVisible = visible,
                            status = status.name,
                            errorMessage = errorMessage.takeIf { visible },
                            profileId = dictation.profile.id.takeIf { it != 0L },
                            audioPath = audioPath
                        )
                    )
                } else {
                    dictationDao.updateRetryResult(
                        id = retryEntryId,
                        text = storedText,
                        rawText = storedRawText,
                        wordCount = wordCount,
                        timestamp = clock.millis(),
                        status = status.name,
                        errorMessage = errorMessage.takeIf { visible },
                        sourceApp = appContext?.label,
                        sourceAppPackage = appContext?.packageName,
                        profileId = dictation.profile.id.takeIf { it != 0L },
                        audioPath = audioPath,
                        historyVisible = visible
                    )
                    retryEntryId
                }
            }
        } catch (e: Exception) {
            audioPath?.takeIf { it != previousAudioPath }?.let { audioStore.delete(it) }
            throw e
        }
        if (previousAudioPath != null && previousAudioPath != audioPath) audioStore.delete(previousAudioPath)
        return SavedEntry(id, audioPath)
    }

    private class SavedEntry(val id: Long, val audioPath: String?)

    /**
     * Lifetime counters are content-free and live outside the dictation rows, so deleting
     * or pruning history never walks them backwards. A counter failure must never fail
     * the dictation itself.
     */
    private suspend fun recordLifetimeStats(wordCount: Int, durationMs: Long) {
        if (wordCount == 0) return
        try {
            lifetimeStatsDao.record(wordCount, durationMs, clock.millis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record lifetime stats", e)
        }
    }

    private fun countWords(text: String): Int = text.split(WHITESPACE).count { it.isNotBlank() }

    private companion object {
        const val TAG = "HistoryRepository"
        val WHITESPACE = "\\s+".toRegex()
    }
}

/** DictationRecord describes one finished recording; [audio] may live anywhere until it is retained. */
data class DictationRecord(
    val audio: File,
    val durationMs: Long,
    val appContext: AppContext?,
    val profile: Profile,
    val retryEntryId: Long? = null,
    // incognito is set for fields that ask not to be learned from, such as private browser tabs.
    val incognito: Boolean = false
)
