package com.sasayaki.service

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BubbleChoiceWriterTest {
    @Test
    fun `rapid choices persist in request order even while the first write is suspended`() = runTest {
        val firstWrite = CompletableDeferred<Unit>()
        val saved = mutableListOf<Boolean>()
        var writesStarted = 0
        val writer = BubbleChoiceWriter(backgroundScope) { enabled ->
            if (writesStarted++ == 0) firstWrite.await()
            saved += enabled
        }

        writer.remember(true)
        runCurrent()
        writer.remember(false)
        writer.remember(true)
        writer.remember(false)
        runCurrent()
        assertEquals(emptyList<Boolean>(), saved)

        firstWrite.complete(Unit)
        runCurrent()
        assertEquals(listOf(true, false, true, false), saved)
    }

    @Test
    fun `a failed write does not prevent remembering the next choice`() = runTest {
        val saved = mutableListOf<Boolean>()
        val writer = BubbleChoiceWriter(backgroundScope) { enabled ->
            if (enabled) throw IOException("Storage unavailable")
            saved += enabled
        }

        writer.remember(true)
        writer.remember(false)
        runCurrent()

        assertEquals(listOf(false), saved)
    }
}
