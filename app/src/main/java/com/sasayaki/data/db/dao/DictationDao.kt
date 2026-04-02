package com.sasayaki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sasayaki.data.db.entity.DictationStats
import com.sasayaki.data.db.entity.DictationSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface DictationDao {
    @Insert
    suspend fun insert(dictation: com.sasayaki.data.db.entity.Dictation): Long

    @Query("SELECT id, text, wordCount, timestamp, sourceApp, durationMs FROM dictations WHERE historyVisible = 1 ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 500): Flow<List<DictationSummary>>

    @Query("SELECT rawText FROM dictations WHERE id = :id")
    suspend fun getRawText(id: Long): String?

    @Query("UPDATE dictations SET text = '', rawText = '', sourceApp = NULL, historyVisible = 0 WHERE id = :id")
    suspend fun removeFromHistory(id: Long)

    @Query("UPDATE dictations SET text = '', rawText = '', sourceApp = NULL, historyVisible = 0 WHERE historyVisible = 1")
    suspend fun removeAllFromHistory()

    @Query("DELETE FROM dictations WHERE id NOT IN (SELECT id FROM dictations ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun pruneOldEntries(keep: Int)

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
