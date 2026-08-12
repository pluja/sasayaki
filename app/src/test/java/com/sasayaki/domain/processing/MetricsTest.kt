package com.sasayaki.domain.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the benchmark's own instruments.
 *
 * A broken metric is worse than a missing one: [Metrics.emojiCount] once counted per Char
 * and so reported zero for output that visibly contained emoji, which made a working
 * control look dead on every model. The eval is only trustworthy if what it measures with
 * is itself measured.
 */
class MetricsTest {

    @Test
    fun `counts emoji above the BMP`() {
        // Surrogate pairs in UTF-16 - the case that previously counted as zero.
        assertEquals(1.0, Metrics.emojiCount("Congrats! 🎉"), 0.0)
        assertEquals(2.0, Metrics.emojiCount("nice 🎉 work 🚀"), 0.0)
        assertEquals(1.0, Metrics.emojiCount("so happy 😀"), 0.0)
    }

    @Test
    fun `counts emoji inside the BMP`() {
        assertEquals(1.0, Metrics.emojiCount("love it ❤"), 0.0)
        assertEquals(1.0, Metrics.emojiCount("sunny ☀"), 0.0)
    }

    @Test
    fun `plain text has no emoji`() {
        assertEquals(0.0, Metrics.emojiCount("Congrats on the new job. That is great news."), 0.0)
        assertEquals(0.0, Metrics.emojiCount("acentos: cafè, señor, français"), 0.0)
    }

    @Test
    fun `word count ignores surrounding whitespace`() {
        assertEquals(3.0, Metrics.wordCount("  one two   three "), 0.0)
    }

    @Test
    fun `punctuation density is per word, not absolute`() {
        val short = Metrics.punctuationDensity("hi, there.")
        val long = Metrics.punctuationDensity("hi, there. " + "word ".repeat(20))
        assertTrue("Density must fall as unpunctuated words are added", long < short)
    }

    @Test
    fun `uppercase ratio distinguishes lowercase from sentence case`() {
        assertEquals(0.0, Metrics.uppercaseRatio("all lowercase here"), 0.0)
        assertTrue(Metrics.uppercaseRatio("All Lowercase Here") > 0.0)
    }

    @Test
    fun `edit distance grows as text is changed further`() {
        val raw = "yeah so me and him was gonna go over the numbers"
        val untouched = Metrics.editDistanceFromRaw(raw, raw)
        val light = Metrics.editDistanceFromRaw(raw, "Yeah, so me and him were gonna go over the numbers.")
        val heavy = Metrics.editDistanceFromRaw(raw, "He and I intended to review the figures together.")
        assertEquals(0.0, untouched, 0.0)
        assertTrue("A light edit must score above an identical string", light > untouched)
        assertTrue("A rewrite must score above a light edit", heavy > light)
    }

    @Test
    fun `median handles even and odd sample counts`() {
        assertEquals(2.0, Metrics.median(listOf(1.0, 2.0, 3.0)), 0.0)
        assertEquals(2.5, Metrics.median(listOf(1.0, 2.0, 3.0, 4.0)), 0.0)
        assertEquals(0.0, Metrics.median(emptyList()), 0.0)
    }
}
