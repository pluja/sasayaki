package com.sasayaki.ui.history

import com.sasayaki.data.db.entity.DictationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryFocusTest {
    private fun dictation(id: Long) = DictationSummary(
        id = id,
        text = "Dictation $id",
        wordCount = 2,
        timestamp = 0,
        sourceApp = null,
        sourceAppPackage = null,
        durationMs = 1000,
        status = "SUCCESS",
        errorMessage = null,
        profileId = null,
        audioPath = null
    )

    private val groups = listOf(
        DayGroup("today", DayLabel.Today, 4, listOf(dictation(1), dictation(2))),
        DayGroup("yesterday", DayLabel.Yesterday, 2, listOf(dictation(3)))
    )

    @Test
    fun `index skips the search field and each day header`() {
        val state = HistoryUiState(loaded = true, historyEnabled = true, hasHistory = true, groups = groups)

        assertEquals(2, state.listIndexOf(1))
        assertEquals(5, state.listIndexOf(3))
    }

    @Test
    fun `index counts the banner shown while history is off`() {
        val state = HistoryUiState(loaded = true, historyEnabled = false, hasHistory = true, groups = groups)

        assertEquals(3, state.listIndexOf(1))
    }

    @Test
    fun `a dictation that is not listed has no index`() {
        assertNull(HistoryUiState(loaded = true, hasHistory = true, groups = groups).listIndexOf(99))
    }
}
