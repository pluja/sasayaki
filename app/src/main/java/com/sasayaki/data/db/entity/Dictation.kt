package com.sasayaki.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sasayaki.domain.model.DictationStatus

@Immutable
@Entity(tableName = "dictations", indices = [Index("timestamp")])
data class Dictation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val rawText: String,
    val wordCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceApp: String? = null,
    val durationMs: Long = 0,
    val historyVisible: Boolean = true,
    val status: String = DictationStatus.SUCCESS.name,
    val errorMessage: String? = null,
    val profileId: Long? = null,
    val audioPath: String? = null
)
