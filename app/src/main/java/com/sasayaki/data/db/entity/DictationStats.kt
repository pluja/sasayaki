package com.sasayaki.data.db.entity

import androidx.compose.runtime.Immutable

@Immutable
data class DictationStats(
    val count: Int,
    val wordCount: Int,
    val durationMs: Long
)
