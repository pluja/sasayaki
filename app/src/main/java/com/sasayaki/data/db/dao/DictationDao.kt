package com.sasayaki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sasayaki.data.db.entity.Dictation
import com.sasayaki.data.db.entity.DictationStats
import com.sasayaki.data.db.entity.DictationSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface DictationDao {
    @Insert
    suspend fun insert(dictation: Dictation): Long

    @Update
    suspend fun update(dictation: Dictation)

    @Query("SELECT id, text, wordCount, timestamp, sourceApp, durationMs, status, errorMessage, profileId, audioPath FROM dictations WHERE historyVisible = 1 ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 500): Flow<List<DictationSummary>>

    @Query("SELECT * FROM dictations WHERE id = :id")
    suspend fun getById(id: Long): Dictation?

    @Query("SELECT id FROM dictations WHERE status = 'FAILURE' AND historyVisible = 1 AND audioPath IS NOT NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRetriableFailureId(): Long?

    @Query("SELECT rawText FROM dictations WHERE id = :id")
    suspend fun getRawText(id: Long): String?

    @Query("SELECT audioPath FROM dictations WHERE id = :id")
    suspend fun getAudioPath(id: Long): String?

    @Query("SELECT audioPath FROM dictations WHERE historyVisible = 1 AND audioPath IS NOT NULL AND id NOT IN (SELECT id FROM dictations WHERE historyVisible = 1 ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun getPrunableAudioPaths(keep: Int): List<String>

    @Query("UPDATE dictations SET text = '', rawText = '', sourceApp = NULL, errorMessage = NULL, audioPath = NULL, historyVisible = 0 WHERE id = :id")
    suspend fun removeFromHistory(id: Long)

    @Query("SELECT audioPath FROM dictations WHERE historyVisible = 1 AND audioPath IS NOT NULL")
    suspend fun getAllVisibleAudioPaths(): List<String>

    @Query("UPDATE dictations SET text = '', rawText = '', sourceApp = NULL, errorMessage = NULL, audioPath = NULL, historyVisible = 0 WHERE historyVisible = 1")
    suspend fun removeAllFromHistory()

    @Query("DELETE FROM dictations WHERE historyVisible = 1 AND id NOT IN (SELECT id FROM dictations WHERE historyVisible = 1 ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun pruneOldEntries(keep: Int)

    @Query("""
        UPDATE dictations
        SET text = :text,
            rawText = :rawText,
            wordCount = :wordCount,
            timestamp = :timestamp,
            status = :status,
            errorMessage = :errorMessage,
            profileId = :profileId,
            audioPath = :audioPath,
            historyVisible = :historyVisible
        WHERE id = :id
    """)
    suspend fun updateRetryResult(
        id: Long,
        text: String,
        rawText: String,
        wordCount: Int,
        timestamp: Long,
        status: String,
        errorMessage: String?,
        profileId: Long?,
        audioPath: String?,
        historyVisible: Boolean
    )

    @Query("""
        SELECT COUNT(*) as count,
               COALESCE(SUM(wordCount), 0) as wordCount,
               COALESCE(SUM(durationMs), 0) as durationMs
        FROM dictations WHERE timestamp >= :startOfDay
    """)
    fun getTodayStats(startOfDay: Long): Flow<DictationStats>

    @Query("""
        SELECT COUNT(*) as count,
               COALESCE(SUM(wordCount), 0) as wordCount,
               COALESCE(SUM(durationMs), 0) as durationMs
        FROM dictations
    """)
    fun getTotalStats(): Flow<DictationStats>
}
