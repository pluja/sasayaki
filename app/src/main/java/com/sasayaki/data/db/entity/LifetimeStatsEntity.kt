package com.sasayaki.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Running totals that survive history deletion and retention pruning.
 *
 * Dictation rows are pruned once they fall outside the retention limit, so totals
 * derived from them shrink over time. These counters are incremented once per
 * dictation and never decremented, so they hold content-free lifetime figures.
 */
@Entity(tableName = "lifetime_stats")
data class LifetimeStatsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val dictationCount: Int = 0,
    val wordCount: Int = 0,
    val durationMs: Long = 0,
    val firstDictationAt: Long? = null
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
