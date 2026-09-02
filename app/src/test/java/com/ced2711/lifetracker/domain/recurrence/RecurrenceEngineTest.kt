package com.ced2711.lifetracker.domain.recurrence

import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceEngineTest {
    @Test
    fun `daily recurrence includes start and through date`() {
        val result = occurrences(
            start = "2026-08-18",
            through = "2026-08-21",
            rule = RecurrenceRule(RecurrenceUnit.DAY),
        )

        assertEquals(dates("2026-08-18", "2026-08-19", "2026-08-20", "2026-08-21"), result)
    }

    @Test
    fun `interval applies to day and week recurrences`() {
        assertEquals(
            dates("2026-08-18", "2026-08-21", "2026-08-24"),
            occurrences(
                start = "2026-08-18",
                through = "2026-08-25",
                rule = RecurrenceRule(RecurrenceUnit.DAY, interval = 3),
            ),
        )
        assertEquals(
            dates("2026-08-18", "2026-09-01", "2026-09-15"),
            occurrences(
                start = "2026-08-18",
                through = "2026-09-15",
                rule = RecurrenceRule(RecurrenceUnit.WEEK, interval = 2),
            ),
        )
    }

    @Test
    fun `monthly recurrence falls back to month end but stays anchored`() {
        val result = occurrences(
            start = "2024-01-31",
            through = "2024-05-31",
            rule = RecurrenceRule(RecurrenceUnit.MONTH),
        )

        assertEquals(
            dates("2024-01-31", "2024-02-29", "2024-03-31", "2024-04-30", "2024-05-31"),
            result,
        )
    }

    @Test
    fun `monthly interval is calculated from original start`() {
        val result = occurrences(
            start = "2026-01-31",
            through = "2026-07-31",
            rule = RecurrenceRule(RecurrenceUnit.MONTH, interval = 2),
        )

        assertEquals(dates("2026-01-31", "2026-03-31", "2026-05-31", "2026-07-31"), result)
    }

    @Test
    fun `yearly leap day recurrence returns to leap day`() {
        val result = occurrences(
            start = "2024-02-29",
            through = "2028-02-29",
            rule = RecurrenceRule(RecurrenceUnit.YEAR),
        )

        assertEquals(
            dates("2024-02-29", "2025-02-28", "2026-02-28", "2027-02-28", "2028-02-29"),
            result,
        )
    }

    @Test
    fun `paged monthly recurrence keeps its original month end anchor`() {
        val page = RecurrenceEngine.generateOccurrencesAfter(
            startEpochDay = LocalDate.parse("2024-01-31").toEpochDay(),
            rule = RecurrenceRule(RecurrenceUnit.MONTH),
            afterEpochDayExclusive = LocalDate.parse("2024-02-29").toEpochDay(),
            throughEpochDay = LocalDate.parse("2024-04-30").toEpochDay(),
            limit = 10,
        )

        assertEquals(
            dates("2024-03-31", "2024-04-30"),
            page.epochDays.map(LocalDate::ofEpochDay),
        )
        assertFalse(page.hasMore)
    }

    @Test
    fun `paged yearly recurrence returns to leap day`() {
        val page = RecurrenceEngine.generateOccurrencesAfter(
            startEpochDay = LocalDate.parse("2024-02-29").toEpochDay(),
            rule = RecurrenceRule(RecurrenceUnit.YEAR),
            afterEpochDayExclusive = LocalDate.parse("2027-02-28").toEpochDay(),
            throughEpochDay = LocalDate.parse("2028-02-29").toEpochDay(),
            limit = 10,
        )

        assertEquals(
            dates("2028-02-29"),
            page.epochDays.map(LocalDate::ofEpochDay),
        )
    }

    @Test
    fun `page watermark is exclusive and limit reports more work`() {
        val start = LocalDate.parse("2026-08-18")
        val page = RecurrenceEngine.generateOccurrencesAfter(
            startEpochDay = start.toEpochDay(),
            rule = RecurrenceRule(RecurrenceUnit.DAY),
            afterEpochDayExclusive = start.plusDays(2).toEpochDay(),
            throughEpochDay = start.plusDays(10).toEpochDay(),
            limit = 3,
        )

        assertEquals(
            listOf(start.plusDays(3), start.plusDays(4), start.plusDays(5)),
            page.epochDays.map(LocalDate::ofEpochDay),
        )
        assertTrue(page.hasMore)
    }

    @Test
    fun `ancient daily series jumps directly to the next absolute occurrence`() {
        val start = LocalDate.of(1, 1, 1)
        val watermark = start.plusDays(739_999)
        val page = RecurrenceEngine.generateOccurrencesAfter(
            startEpochDay = start.toEpochDay(),
            rule = RecurrenceRule(RecurrenceUnit.DAY),
            afterEpochDayExclusive = watermark.toEpochDay(),
            throughEpochDay = watermark.plusDays(3).toEpochDay(),
            limit = 2,
        )

        assertEquals(
            listOf(watermark.plusDays(1), watermark.plusDays(2)),
            page.epochDays.map(LocalDate::ofEpochDay),
        )
        assertTrue(page.hasMore)
    }

    @Test
    fun `paging stops cleanly at the representable date range`() {
        val page = RecurrenceEngine.generateOccurrencesAfter(
            startEpochDay = LocalDate.MAX.toEpochDay(),
            rule = RecurrenceRule(RecurrenceUnit.YEAR),
            afterEpochDayExclusive = LocalDate.MAX.toEpochDay(),
            throughEpochDay = LocalDate.MAX.toEpochDay(),
            limit = 1,
        )

        assertTrue(page.epochDays.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun `rule end and requested through dates are both inclusive bounds`() {
        val start = LocalDate.parse("2026-08-18")
        val ruleEndingOnOccurrence = RecurrenceRule(
            unit = RecurrenceUnit.DAY,
            interval = 2,
            endEpochDay = LocalDate.parse("2026-08-22").toEpochDay(),
        )

        assertEquals(
            dates("2026-08-18", "2026-08-20", "2026-08-22"),
            RecurrenceEngine.generateOccurrences(
                start.toEpochDay(),
                ruleEndingOnOccurrence,
                LocalDate.parse("2026-08-30").toEpochDay(),
            ).map(LocalDate::ofEpochDay),
        )
        assertEquals(
            dates("2026-08-18", "2026-08-20"),
            RecurrenceEngine.generateOccurrences(
                start.toEpochDay(),
                ruleEndingOnOccurrence.copy(endEpochDay = null),
                LocalDate.parse("2026-08-21").toEpochDay(),
            ).map(LocalDate::ofEpochDay),
        )
    }

    @Test
    fun `bound before start produces no occurrences`() {
        val result = occurrences(
            start = "2026-08-18",
            through = "2026-08-17",
            rule = RecurrenceRule(RecurrenceUnit.DAY),
        )

        assertTrue(result.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero interval is rejected`() {
        occurrences(
            start = "2026-08-18",
            through = "2026-08-20",
            rule = RecurrenceRule(RecurrenceUnit.DAY, interval = 0),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative interval is rejected`() {
        occurrences(
            start = "2026-08-18",
            through = "2026-08-20",
            rule = RecurrenceRule(RecurrenceUnit.DAY, interval = -1),
        )
    }

    @Test
    fun `rule end before start produces no occurrences`() {
        val result = occurrences(
            start = "2026-08-18",
            through = "2026-08-30",
            rule = RecurrenceRule(
                RecurrenceUnit.DAY,
                endEpochDay = LocalDate.parse("2026-08-17").toEpochDay(),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `reminder horizon rounds positive offsets up to whole days`() {
        val today = LocalDate.parse("2026-08-18")

        assertEquals(
            today.plusDays(1),
            reminderHorizon(today, listOf(0, 1, 1_440)),
        )
        assertEquals(
            today.plusDays(2),
            reminderHorizon(today, listOf(1_441)),
        )
    }

    @Test
    fun `reminder horizon ignores non-positive offsets`() {
        val today = LocalDate.parse("2026-08-18")

        assertEquals(today, reminderHorizon(today, listOf(-1_440, -1, 0)))
    }

    @Test
    fun `reminder horizon obeys materialization cap`() {
        val today = LocalDate.parse("2026-08-18")

        assertEquals(
            today.plusDays(366),
            reminderHorizon(today, listOf(Long.MAX_VALUE), maxLookaheadDays = 366),
        )
    }

    @Test
    fun `replacement rule starts after generated history and respects a later effective date`() {
        val today = LocalDate.parse("2026-08-18")

        assertEquals(
            today.plusDays(1).toEpochDay(),
            RecurrenceEngine.firstUnmaterializedEpochDay(
                effectiveEpochDay = today.minusDays(3).toEpochDay(),
                materializedThroughEpochDay = today.toEpochDay(),
            ),
        )
        assertEquals(
            today.plusDays(5).toEpochDay(),
            RecurrenceEngine.firstUnmaterializedEpochDay(
                effectiveEpochDay = today.plusDays(5).toEpochDay(),
                materializedThroughEpochDay = today.toEpochDay(),
            ),
        )
    }

    private fun occurrences(
        start: String,
        through: String,
        rule: RecurrenceRule,
    ): List<LocalDate> = RecurrenceEngine.generateOccurrences(
        startEpochDay = LocalDate.parse(start).toEpochDay(),
        rule = rule,
        throughEpochDay = LocalDate.parse(through).toEpochDay(),
    ).map(LocalDate::ofEpochDay)

    private fun dates(vararg values: String): List<LocalDate> = values.map(LocalDate::parse)

    private fun reminderHorizon(
        through: LocalDate,
        offsets: List<Long>,
        maxLookaheadDays: Long = 366,
    ): LocalDate = LocalDate.ofEpochDay(
        RecurrenceEngine.reminderAwareGenerationHorizon(
            throughEpochDay = through.toEpochDay(),
            reminderOffsetsMinutes = offsets,
            maxLookaheadDays = maxLookaheadDays,
        ),
    )
}
