package com.ced2711.lifetracker.domain.recurrence

import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import java.time.DateTimeException
import java.time.LocalDate

object RecurrenceEngine {
    data class OccurrencePage(
        val epochDays: List<Long>,
        val hasMore: Boolean,
    )

    /** Earliest legal start for a replacement rule that must not rewrite generated history. */
    fun firstUnmaterializedEpochDay(
        effectiveEpochDay: Long,
        materializedThroughEpochDay: Long,
    ): Long = maxOf(
        effectiveEpochDay,
        LocalDate.ofEpochDay(materializedThroughEpochDay).plusDays(1).toEpochDay(),
    )

    /**
     * Extends a generation horizon far enough to materialize tasks whose reminders fire before
     * [throughEpochDay]. The caller-provided cap prevents malformed/imported offsets from causing
     * an unbounded catch-up batch.
     */
    fun reminderAwareGenerationHorizon(
        throughEpochDay: Long,
        reminderOffsetsMinutes: Iterable<Long>,
        maxLookaheadDays: Long,
    ): Long {
        require(maxLookaheadDays >= 0) { "Lookahead cap must not be negative" }
        val largestPositiveOffset = reminderOffsetsMinutes.filter { it > 0 }.maxOrNull()
            ?: return throughEpochDay
        val minutesPerDay = 1_440L
        val requiredDays = (
            largestPositiveOffset / minutesPerDay +
                if (largestPositiveOffset % minutesPerDay == 0L) 0L else 1L
            ).coerceAtMost(maxLookaheadDays)
        return LocalDate.ofEpochDay(throughEpochDay).plusDays(requiredDays).toEpochDay()
    }

    /**
     * Generates occurrences from [startEpochDay] through [throughEpochDay], inclusive.
     * A rule end date, when present, is also inclusive.
     */
    fun generateOccurrences(
        startEpochDay: Long,
        rule: RecurrenceRule,
        throughEpochDay: Long,
    ): List<Long> {
        val occurrences = mutableListOf<Long>()
        var afterEpochDay: Long? = null
        do {
            val page = generateOccurrencesAfter(
                startEpochDay = startEpochDay,
                rule = rule,
                afterEpochDayExclusive = afterEpochDay,
                throughEpochDay = throughEpochDay,
                limit = COMPATIBILITY_PAGE_SIZE,
            )
            occurrences += page.epochDays
            afterEpochDay = page.epochDays.lastOrNull()
        } while (page.hasMore)
        return occurrences
    }

    /**
     * Generates at most [limit] occurrences strictly after [afterEpochDayExclusive]. The first
     * absolute occurrence index is found with exponential and binary search, so an old series is
     * resumed without enumerating its history or changing its original calendar anchor.
     */
    fun generateOccurrencesAfter(
        startEpochDay: Long,
        rule: RecurrenceRule,
        afterEpochDayExclusive: Long?,
        throughEpochDay: Long,
        limit: Int,
    ): OccurrencePage {
        require(rule.interval > 0) { "Recurrence interval must be positive" }
        require(limit > 0) { "Occurrence page limit must be positive" }

        val start = LocalDate.ofEpochDay(startEpochDay)
        val through = LocalDate.ofEpochDay(throughEpochDay)
        val end = rule.endEpochDay
            ?.let(LocalDate::ofEpochDay)
            ?.let { minOf(it, through) }
            ?: through

        if (end < start) return OccurrencePage(emptyList(), hasMore = false)

        var occurrenceIndex = firstIndexAfter(
            start = start,
            rule = rule,
            afterEpochDayExclusive = afterEpochDayExclusive,
        )
        val occurrences = ArrayList<Long>(limit + 1)
        while (occurrences.size <= limit) {
            val occurrence = occurrenceAt(start, rule, occurrenceIndex) ?: break
            if (occurrence > end) break
            occurrences += occurrence.toEpochDay()
            if (occurrenceIndex == Long.MAX_VALUE) break
            occurrenceIndex++
        }

        val hasMore = occurrences.size > limit
        if (hasMore) occurrences.removeAt(occurrences.lastIndex)
        return OccurrencePage(occurrences, hasMore)
    }

    private fun firstIndexAfter(
        start: LocalDate,
        rule: RecurrenceRule,
        afterEpochDayExclusive: Long?,
    ): Long {
        val after = afterEpochDayExclusive?.let(LocalDate::ofEpochDay) ?: return 0L
        if (after < start) return 0L

        var lower = 0L
        var upper = 1L
        while (true) {
            val candidate = occurrenceAt(start, rule, upper)
            if (candidate == null || candidate > after) break
            lower = upper
            if (upper == Long.MAX_VALUE) return Long.MAX_VALUE
            upper = if (upper > Long.MAX_VALUE / 2L) Long.MAX_VALUE else upper * 2L
        }

        while (lower + 1L < upper) {
            val middle = lower + (upper - lower) / 2L
            val candidate = occurrenceAt(start, rule, middle)
            if (candidate == null || candidate > after) {
                upper = middle
            } else {
                lower = middle
            }
        }
        return upper
    }

    private fun occurrenceAt(
        start: LocalDate,
        rule: RecurrenceRule,
        occurrenceIndex: Long,
    ): LocalDate? {
        val amount = try {
            Math.multiplyExact(occurrenceIndex, rule.interval.toLong())
        } catch (_: ArithmeticException) {
            return null
        }
        return try {
            start.plus(amount, rule.unit)
        } catch (_: DateTimeException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun LocalDate.plus(amount: Long, unit: RecurrenceUnit): LocalDate = when (unit) {
        RecurrenceUnit.DAY -> plusDays(amount)
        RecurrenceUnit.WEEK -> plusWeeks(amount)
        RecurrenceUnit.MONTH -> plusMonths(amount)
        RecurrenceUnit.YEAR -> plusYears(amount)
    }

    private const val COMPATIBILITY_PAGE_SIZE = 4_096
}
