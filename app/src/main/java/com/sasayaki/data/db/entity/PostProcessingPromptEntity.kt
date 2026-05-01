package com.sasayaki.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sasayaki.domain.model.PostProcessingPrompt

@Entity(tableName = "post_processing_prompts")
data class PostProcessingPromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val prompt: String,
    val builtIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): PostProcessingPrompt = PostProcessingPrompt(
        id = id,
        title = title,
        prompt = prompt,
        builtIn = builtIn,
        createdAt = createdAt
    )
}

fun PostProcessingPrompt.toEntity(): PostProcessingPromptEntity = PostProcessingPromptEntity(
    id = id,
    title = title.trim().ifBlank { "Prompt" },
    prompt = prompt.trim(),
    builtIn = builtIn,
    createdAt = createdAt
)

