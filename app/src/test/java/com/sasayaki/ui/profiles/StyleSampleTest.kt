package com.sasayaki.ui.profiles

import android.app.Application
import com.sasayaki.domain.model.OutputStyle
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.SummarizeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StyleSampleTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun sample(style: OutputStyle, rewrite: RewriteMode, summarize: SummarizeMode, emoji: Boolean) =
        styleSample(context.getString(styleSampleRes(rewrite, summarize)), style, rewrite, emoji)

    @Test
    fun `every combination has a sample`() {
        OutputStyle.entries.forEach { style ->
            RewriteMode.entries.forEach { rewrite ->
                SummarizeMode.entries.forEach { summarize ->
                    assertTrue(sample(style, rewrite, summarize, emoji = false).isNotBlank())
                }
            }
        }
    }

    @Test
    fun `punctuation styles follow the output rules`() {
        val full = sample(OutputStyle.STANDARD, RewriteMode.FIX, SummarizeMode.LIGHT, emoji = false)
        val light = sample(OutputStyle.RELAXED, RewriteMode.FIX, SummarizeMode.LIGHT, emoji = false)
        val minimal = sample(OutputStyle.MINIMAL, RewriteMode.FIX, SummarizeMode.LIGHT, emoji = false)
        assertTrue(full.endsWith(".") && full.contains(","))
        assertFalse(light.endsWith(".") || light.contains(","))
        assertEquals(minimal.lowercase(), minimal)
        assertTrue(minimal.none { it in ".,?!" })
    }

    @Test
    fun `emoji is added only to casual rewrites`() {
        assertTrue(sample(OutputStyle.STANDARD, RewriteMode.FIX, SummarizeMode.NONE, emoji = true).endsWith("🍜"))
        assertFalse(sample(OutputStyle.STANDARD, RewriteMode.POLISH, SummarizeMode.NONE, emoji = true).endsWith("🍜"))
    }
}
