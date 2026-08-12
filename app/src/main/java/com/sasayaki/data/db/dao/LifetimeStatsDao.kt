package com.sasayaki.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.sasayaki.data.db.entity.LifetimeStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LifetimeStatsDao {
    @Query("SELECT * FROM lifetime_stats WHERE id = ${LifetimeStatsEntity.SINGLETON_ID}")
    fun observe(): Flow<LifetimeStatsEntity?>

    /**
     * Fresh installs create the table empty, so the counter row is materialised
     * lazily rather than relying on a migration having run.
     */
    @Query(
        """
        INSERT OR IGNORE INTO lifetime_stats
            (id, dictationCount, wordCount, durationMs, firstDictationAt)
        VALUES (${LifetimeStatsEntity.SINGLETON_ID}, 0, 0, 0, NULL)
    """
    )
    suspend fun ensureRow()

    @Query(
        """
        UPDATE lifetime_stats
        SET dictationCount = dictationCount + 1,
            wordCount = wordCount + :wordCount,
            durationMs = durationMs + :durationMs,
            firstDictationAt = COALESCE(firstDictationAt, :timestamp)
        WHERE id = ${LifetimeStatsEntity.SINGLETON_ID}
    """
    )
    suspend fun increment(wordCount: Int, durationMs: Long, timestamp: Long)

    @Transaction
    suspend fun record(wordCount: Int, durationMs: Long, timestamp: Long) {
        ensureRow()
        increment(wordCount, durationMs, timestamp)
    }
}
