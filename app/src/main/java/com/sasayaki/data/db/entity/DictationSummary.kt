package com.sasayaki.data.db.entity

import androidx.compose.runtime.Immutable

@Immutable
data class DictationSummary(
    val id: Long,
    val text: String,
    val wordCount: Int,
    val timestamp: Long,
    val sourceApp: String?,
    val durationMs: Long
)
