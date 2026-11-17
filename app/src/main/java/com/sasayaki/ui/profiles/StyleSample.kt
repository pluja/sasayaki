package com.sasayaki.ui.profiles

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import com.sasayaki.R
import com.sasayaki.domain.model.OutputStyle
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.SummarizeMode
import com.sasayaki.domain.processing.applyPunctuationStyle
import com.sasayaki.ui.theme.Spacing

private const val SAMPLE_EMOJI = " 🍜"

// sampleTexts are written in full punctuation; the lighter punctuation styles are derived from them.
private val sampleTexts: Map<Pair<RewriteMode, SummarizeMode>, Int> = mapOf(
    (RewriteMode.NONE to SummarizeMode.NONE) to R.string.profile_style_sample_keep_off,
    (RewriteMode.NONE to SummarizeMode.LIGHT) to R.string.profile_style_sample_keep_light,
    (RewriteMode.NONE to SummarizeMode.HARD) to R.string.profile_style_sample_keep_strong,
    (RewriteMode.FIX to SummarizeMode.NONE) to R.string.profile_style_sample_fix_off,
    (RewriteMode.FIX to SummarizeMode.LIGHT) to R.string.profile_style_sample_fix_light,
    (RewriteMode.FIX to SummarizeMode.HARD) to R.string.profile_style_sample_fix_strong,
    (RewriteMode.POLISH to SummarizeMode.NONE) to R.string.profile_style_sample_polish_off,
    (RewriteMode.POLISH to SummarizeMode.LIGHT) to R.string.profile_style_sample_polish_light,
    (RewriteMode.POLISH to SummarizeMode.HARD) to R.string.profile_style_sample_polish_strong
)

@StringRes
fun styleSampleRes(rewrite: RewriteMode, summarize: SummarizeMode): Int = sampleTexts.getValue(rewrite to summarize)

fun styleSample(text: String, style: OutputStyle, rewrite: RewriteMode, emoji: Boolean): String {
    val punctuated = applyPunctuationStyle(text, style)
    return if (emoji && rewrite != RewriteMode.POLISH) punctuated + SAMPLE_EMOJI else punctuated
}

@Composable
fun StylePreview(
    shapes: ListItemShapes,
    style: OutputStyle,
    rewrite: RewriteMode,
    summarize: SummarizeMode,
    emoji: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Surface(shape = shapes.shape, color = colors.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)
        ) {
            Text(
                stringResource(R.string.profile_style_you_say),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSecondaryContainer
            )
            Text(
                stringResource(R.string.profile_style_sample_dictation),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = colors.onSecondaryContainer.copy(alpha = 0.72f)
            )
            Text(
                stringResource(R.string.profile_style_you_get),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSecondaryContainer,
                modifier = Modifier.padding(top = Spacing.s)
            )
            AnimatedContent(
                targetState = styleSample(stringResource(styleSampleRes(rewrite, summarize)), style, rewrite, emoji),
                transitionSpec = { fadeIn(fadeInSpec) togetherWith fadeOut(fadeOutSpec) },
                label = "style sample"
            ) { sample ->
                Text(sample, style = MaterialTheme.typography.bodyLarge, color = colors.onSecondaryContainer)
            }
        }
    }
}
