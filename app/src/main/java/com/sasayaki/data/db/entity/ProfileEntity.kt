package com.sasayaki.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sasayaki.domain.model.OutputStyle
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.SummarizeMode

@Entity(tableName = "profiles", indices = [Index("isActive")])
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean = false,
    val asrModel: String = "whisper-1",
    val language: String? = null,
    val llmEnabled: Boolean = false,
    val llmModel: String = "gpt-4o-mini",
    val profilePrompt: String = "",
    val outputStyle: String = OutputStyle.STANDARD.name,
    val rewriteMode: String = RewriteMode.FIX.name,
    val summarizeMode: String = SummarizeMode.NONE.name,
    val emojiAllowed: Boolean = false,
    val selectedRuleIds: String = "",
    val selectedPromptIds: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Profile = Profile(
        id = id,
        name = name,
        isActive = isActive,
        asrModel = asrModel,
        language = language,
        llmEnabled = llmEnabled,
        llmModel = llmModel,
        profilePrompt = profilePrompt,
        outputStyle = enumValueOrDefault(outputStyle, OutputStyle.STANDARD),
        rewriteMode = enumValueOrDefault(rewriteMode, RewriteMode.FIX),
        summarizeMode = enumValueOrDefault(summarizeMode, SummarizeMode.NONE),
        emojiAllowed = emojiAllowed,
        selectedRuleIds = selectedRuleIds.toIdSet(),
        selectedPromptIds = selectedPromptIds.toIdSet()
    )
}

fun Profile.toEntity(now: Long = System.currentTimeMillis()): ProfileEntity = ProfileEntity(
    id = id,
    name = name.trim().ifBlank { "Untitled profile" },
    isActive = isActive,
    asrModel = asrModel.trim().ifBlank { "whisper-1" },
    language = language?.trim()?.lowercase()?.ifBlank { null },
    llmEnabled = llmEnabled,
    llmModel = llmModel.trim().ifBlank { "gpt-4o-mini" },
    profilePrompt = profilePrompt.trim(),
    outputStyle = outputStyle.name,
    rewriteMode = rewriteMode.name,
    summarizeMode = summarizeMode.name,
    emojiAllowed = emojiAllowed,
    selectedRuleIds = selectedRuleIds.toIdCsv(),
    selectedPromptIds = selectedPromptIds.toIdCsv(),
    updatedAt = now
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
    return enumValues<T>().firstOrNull { it.name == value } ?: default
}

fun String.toIdSet(): Set<Long> = split(",")
    .mapNotNull { it.trim().toLongOrNull() }
    .toSet()

fun Set<Long>.toIdCsv(): String = sorted().joinToString(",")
