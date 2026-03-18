package com.sasayaki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sasayaki.data.db.entity.DictationSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface DictationDao {
    @Insert
    suspend fun insert(dictation: com.sasayaki.data.db.entity.Dictation): Long

    @Query("SELECT id, text, wordCount, timestamp, sourceApp, durationMs FROM dictations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 500): Flow<List<DictationSummary>>

    @Query("SELECT rawText FROM dictations WHERE id = :id")
    suspend fun getRawText(id: Long): String?

    @Query("DELETE FROM dictations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM dictations WHERE id NOT IN (SELECT id FROM dictations ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun pruneOldEntries(keep: Int)

    @Query("SELECT COUNT(*) FROM dictations WHERE timestamp >= :startOfDay")
    fun getTodayCount(startOfDay: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM dictations WHERE timestamp >= :startOfDay")
    fun getTodayWordCount(startOfDay: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM dictations WHERE timestamp >= :startOfDay")
    fun getTodayDurationMs(startOfDay: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM dictations")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM dictations")
    fun getTotalWordCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM dictations")
    fun getTotalDurationMs(): Flow<Long>
}
