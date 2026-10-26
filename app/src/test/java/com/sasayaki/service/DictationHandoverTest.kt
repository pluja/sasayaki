package com.sasayaki.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DictationHandoverTest {
    @After
    fun reset() {
        DictationHandover.onCarryEnded = null
        DictationHandover.release = null
    }

    @Test
    fun `a hidden panel hears once that the bubble finished its dictation`() {
        var handedBack = 0
        DictationHandover.onCarryEnded = { handedBack++ }

        DictationHandover.carryEnded()
        DictationHandover.carryEnded()

        assertEquals(1, handedBack)
    }

    @Test
    fun `taking the dictation back stops the hidden panel handing back`() {
        var handedBack = 0
        DictationHandover.onCarryEnded = { handedBack++ }
        DictationHandover.release = {
            DictationHandover.carryEnded()
            null
        }

        DictationHandover.reclaim()

        assertEquals(0, handedBack)
    }
}
