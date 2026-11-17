package com.sasayaki.ui.history

import com.sasayaki.data.db.entity.DictationSummary
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryGroupingTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 24)

    private fun dictation(id: Long, day: LocalDate, hour: Int, text: String = "entry $id", app: String? = null, words: Int = 3) =
        DictationSummary(
            id = id,
            text = text,
            wordCount = words,
            timestamp = day.atTime(hour, 0).toInstant(zone).toEpochMilli(),
            sourceApp = app,
            sourceAppPackage = null,
            durationMs = 1000,
            status = "SUCCESS",
            errorMessage = null,
            profileId = null,
            audioPath = null
        )

    @Test
    fun `groups newest day first with relative labels and word totals`() {
        val groups = groupByDay(
            listOf(
                dictation(1, today.minusDays(3), 9),
                dictation(2, today, 8, words = 4),
                dictation(3, today.minusDays(1), 22),
                dictation(4, today, 18, words = 6)
            ),
            zone,
            today
        )

        assertEquals(listOf(DayLabel.Today, DayLabel.Yesterday), groups.take(2).map { it.label })
        assertEquals(listOf(4L, 2L), groups.first().dictations.map { it.id })
        assertEquals(10, groups.first().totalWords)
        assertEquals(3, groups.size)
    }

    @Test
    fun `query matches text and app case-insensitively`() {
        val all = listOf(
            dictation(1, today, 9, text = "Dinner at nine"),
            dictation(2, today, 10, text = "Status update", app = "Slack"),
            dictation(3, today, 11, text = "Something else")
        )

        assertEquals(listOf(1L), groupByDay(all, zone, today, query = "DINNER").single().dictations.map { it.id })
        assertEquals(listOf(2L), groupByDay(all, zone, today, query = "slack").single().dictations.map { it.id })
        assertEquals(emptyList<DayGroup>(), groupByDay(all, zone, today, query = "absent"))
    }

    @Test
    fun `hidden entries are left out and empty days disappear`() {
        val groups = groupByDay(
            listOf(dictation(1, today, 9), dictation(2, today.minusDays(1), 9)),
            zone,
            today,
            hiddenIds = setOf(2L)
        )

        assertEquals(listOf(DayLabel.Today), groups.map { it.label })
    }
}
