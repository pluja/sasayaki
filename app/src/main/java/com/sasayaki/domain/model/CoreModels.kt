package com.sasayaki.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Profile(
    val id: Long = 0,
    val name: String,
    val isActive: Boolean = false,
    val asrModel: String = "whisper-1",
    val language: String? = null,
    val llmEnabled: Boolean = false,
    val llmModel: String = "gpt-4o-mini",
    val profilePrompt: String = "",
    val selectedRuleIds: Set<Long> = emptySet(),
    val selectedPromptIds: Set<Long> = emptySet()
)

@Immutable
data class TextReplacementRule(
    val id: Long = 0,
    val name: String,
    val pattern: String,
    val replacement: String,
    val isRegex: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
data class PostProcessingPrompt(
    val id: Long = 0,
    val title: String,
    val prompt: String,
    val builtIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class DictationStatus {
    SUCCESS,
    FAILURE
}

