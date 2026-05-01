package com.sasayaki.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sasayaki.domain.model.TextReplacementRule

@Entity(tableName = "text_replacement_rules")
data class TextReplacementRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pattern: String,
    val replacement: String,
    val isRegex: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): TextReplacementRule = TextReplacementRule(
        id = id,
        name = name,
        pattern = pattern,
        replacement = replacement,
        isRegex = isRegex,
        createdAt = createdAt
    )
}

fun TextReplacementRule.toEntity(): TextReplacementRuleEntity = TextReplacementRuleEntity(
    id = id,
    name = name.trim().ifBlank { pattern.trim().take(32).ifBlank { "Rule" } },
    pattern = pattern,
    replacement = replacement,
    isRegex = isRegex,
    createdAt = createdAt
)

