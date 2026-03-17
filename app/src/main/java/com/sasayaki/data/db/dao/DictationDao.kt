package com.sasayaki.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictationDao {
    @Insert
    suspend fun insert(dictation: com.sasayaki.data.db.entity.Dictation): Long

    @Query("SELECT * FROM dictations ORDER BY timestamp DESC")
    fun getAll(): Flow<List<com.sasayaki.data.db.entity.Dictation>>

    @Query("SELECT * FROM dictations WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getByDateRange(startTime: Long, endTime: Long): Flow<List<com.sasayaki.data.db.entity.Dictation>>

    @Query("SELECT date(timestamp / 1000, 'unixepoch', 'localtime') as day, SUM(wordCount) as totalWords FROM dictations GROUP BY day ORDER BY day DESC")
    fun getDailyWordCounts(): Flow<List<DailyWordCount>>

    @Query("DELETE FROM dictations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM dictations WHERE timestamp >= :startOfDay")
    fun getTodayCount(startOfDay: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM dictations WHERE timestamp >= :startOfDay")
    fun getTodayWordCount(startOfDay: Long): Flow<Int>
}

data class DailyWordCount(
    val day: String,
    val totalWords: Int
)
