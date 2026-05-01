package com.sasayaki.data.db.entity

import androidx.compose.runtime.Immutable

@Immutable
data class DictationSummary(
    val id: Long,
    val text: String,
    val wordCount: Int,
    val timestamp: Long,
    val sourceApp: String?,
    val durationMs: Long,
    val status: String,
    val errorMessage: String?,
    val profileId: Long?,
    val audioPath: String?
)
