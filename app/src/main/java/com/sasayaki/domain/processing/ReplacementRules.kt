package com.sasayaki.domain.processing

import androidx.annotation.StringRes
import com.sasayaki.R
import com.sasayaki.domain.model.TextReplacementRule

fun applyReplacementRules(text: String, rules: List<TextReplacementRule>): String =
    rules.filter { it.pattern.isNotBlank() }.fold(text) { current, rule -> rule.applyTo(current) }

/** applyTo leaves the text untouched for an invalid regex rather than failing the whole dictation. */
fun TextReplacementRule.applyTo(text: String): String = if (isRegex) {
    runCatching { Regex(pattern).replace(text, replacement) }.getOrElse { text }
} else {
    text.replace(pattern, replacement)
}

@StringRes
fun TextReplacementRule.patternError(): Int? {
    if (!isRegex || pattern.isBlank()) return null
    return runCatching { Regex(pattern) }.exceptionOrNull()?.let { R.string.error_invalid_pattern }
}
