package com.ced2711.lifetracker.ui.ledger

import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * The span of time the Ledger summarises, and the texts that need numbers, dates or names in
 * them. Everything here is plain logic so it can be tested without a screen.
 */

/** The pages of the Ledger. */
internal enum class LedgerTab(val label: String) { ENTRIES("Entries"), STATISTICS("Statistics"), RECURRING("Recurring") }

/** The span of time the Ledger summarises. Custom is offered on the Statistics page only. */
internal enum class LedgerPeriod(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
    ALL("All"),
    CUSTOM("Custom"),
    ;

    /** Whether previous and next make sense for this period. */
    val canStep: Boolean get() = this == WEEK || this == MONTH || this == YEAR
}

/** First and last day (both included) of a period, as epoch days. */
internal data class LedgerRange(val start: Long, val end: Long) {
    val days: Long get() = end - start + 1
    operator fun contains(epochDay: Long): Boolean = epochDay in start..end
}

internal fun LedgerPeriod.range(
    anchor: LocalDate,
    firstDayOfWeek: DayOfWeek,
    entries: List<LedgerEntryEntity>,
    custom: LedgerRange?,
): LedgerRange {
    fun month() = YearMonth.from(anchor).let { LedgerRange(it.atDay(1).toEpochDay(), it.atEndOfMonth().toEpochDay()) }
    return when (this) {
        LedgerPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            .let { LedgerRange(it.toEpochDay(), it.plusDays(6).toEpochDay()) }
        LedgerPeriod.MONTH -> month()
        LedgerPeriod.YEAR -> LedgerRange(
            anchor.withDayOfYear(1).toEpochDay(),
            anchor.withDayOfYear(anchor.lengthOfYear()).toEpochDay(),
        )
        // From the first entry to today (or the last entry when it lies in the future).
        LedgerPeriod.ALL -> LedgerRange(
            entries.minOfOrNull { it.epochDay } ?: anchor.toEpochDay(),
            maxOf(entries.maxOfOrNull { it.epochDay } ?: anchor.toEpochDay(), anchor.toEpochDay()),
        )
        LedgerPeriod.CUSTOM -> custom ?: month()
    }
}

/** The day to show after stepping [steps] periods forward (or back when negative). */
internal fun LedgerPeriod.shift(anchor: LocalDate, steps: Long): LocalDate = runCatching {
    when (this) {
        LedgerPeriod.WEEK -> anchor.plusWeeks(steps)
        LedgerPeriod.MONTH -> anchor.plusMonths(steps)
        LedgerPeriod.YEAR -> anchor.plusYears(steps)
        LedgerPeriod.ALL, LedgerPeriod.CUSTOM -> anchor
    }
}.getOrDefault(anchor)

internal fun ledgerMonthTitle(month: YearMonth, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "${month.year}年${month.monthValue}月"
    UiLanguage.ENGLISH -> month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
}

/** What the period is called above its totals: "October 2026", "2026", a span of dates, "All time". */
internal fun ledgerPeriodTitle(
    period: LedgerPeriod,
    range: LedgerRange,
    formatting: LedgerDisplayFormatting,
    language: UiLanguage,
): String = when (period) {
    LedgerPeriod.WEEK, LedgerPeriod.CUSTOM -> formatting.date(range.start) + " – " + formatting.date(range.end)
    LedgerPeriod.MONTH -> ledgerMonthTitle(YearMonth.from(LocalDate.ofEpochDay(range.start)), language)
    LedgerPeriod.YEAR -> when (language) {
        UiLanguage.SIMPLIFIED_CHINESE -> "${LocalDate.ofEpochDay(range.start).year}年"
        UiLanguage.ENGLISH -> LocalDate.ofEpochDay(range.start).year.toString()
    }
    LedgerPeriod.ALL -> translateUiText("All time", language)
}

/** The heading of one day in the list: Today, Yesterday, or the weekday and the date. */
internal fun ledgerDayHeading(
    epochDay: Long,
    today: LocalDate,
    formatting: LedgerDisplayFormatting,
    language: UiLanguage,
): String {
    val date = LocalDate.ofEpochDay(epochDay)
    return when (date) {
        today -> translateUiText("Today", language)
        today.minusDays(1) -> translateUiText("Yesterday", language)
        today.plusDays(1) -> translateUiText("Tomorrow", language)
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, formatting.locale) + " " + formatting.date(epochDay)
    }
}

/** What an entry or schedule is called in lists: where the money went, else the note, else its type. */
internal fun ledgerLabel(merchant: String, note: String, type: LedgerType, language: UiLanguage): String =
    merchant.ifBlank { note.ifBlank { translateUiText(type.displayName(), language) } }

/** "Every month from 10/01/2026 until 12/31/2026", as on the desktop. */
internal fun ledgerRepeatSummary(
    unit: RecurrenceUnit,
    interval: Int,
    start: String,
    end: String?,
    language: UiLanguage,
): String {
    val (en, zh) = when (unit) {
        RecurrenceUnit.DAY -> "day" to "天"
        RecurrenceUnit.WEEK -> "week" to "周"
        RecurrenceUnit.MONTH -> "month" to "个月"
        RecurrenceUnit.YEAR -> "year" to "年"
    }
    return when (language) {
        UiLanguage.ENGLISH ->
            (if (interval == 1) "Every $en" else "Every $interval ${en}s") + " from $start" + (end?.let { " until $it" } ?: "")
        UiLanguage.SIMPLIFIED_CHINESE ->
            "从 $start 起每${if (interval == 1) "" else " $interval "}$zh" + (end?.let { "，到 $it 为止" } ?: "")
    }
}

/** The word after the number in "Every 2 weeks". */
internal fun ledgerRepeatUnitWord(unit: RecurrenceUnit, interval: Int?, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> when (unit) {
        RecurrenceUnit.DAY -> "day"
        RecurrenceUnit.WEEK -> "week"
        RecurrenceUnit.MONTH -> "month"
        RecurrenceUnit.YEAR -> "year"
    } + if (interval == 1) "" else "s"
    UiLanguage.SIMPLIFIED_CHINESE -> when (unit) {
        RecurrenceUnit.DAY -> "天"
        RecurrenceUnit.WEEK -> "周"
        RecurrenceUnit.MONTH -> "个月"
        RecurrenceUnit.YEAR -> "年"
    }
}

internal fun ledgerRepeatLabel(unit: RecurrenceUnit): String = when (unit) {
    RecurrenceUnit.DAY -> "Daily"
    RecurrenceUnit.WEEK -> "Weekly"
    RecurrenceUnit.MONTH -> "Monthly"
    RecurrenceUnit.YEAR -> "Yearly"
}

internal fun ledgerAcrossDays(days: Long, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "Across $days day${if (days == 1L) "" else "s"}"
    UiLanguage.SIMPLIFIED_CHINESE -> "共 $days 天"
}

internal fun ledgerFileCount(count: Int, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "$count of 10 files · 25 MB each, 128 MB total"
    UiLanguage.SIMPLIFIED_CHINESE -> "$count/10 个文件 · 每个 25 MB，总计 128 MB"
}

internal fun ledgerSharePercent(percent: Int, label: String, language: UiLanguage): String =
    "$percent% " + translateUiText(label, language)

/** Whether [query] is found in an entry's merchant, note or tags ("#food" finds the tag food). */
internal fun ledgerEntryMatches(entry: LedgerEntryEntity, query: String): Boolean {
    val text = query.trim()
    if (text.isEmpty()) return true
    val tag = text.removePrefix("#")
    return entry.merchant.contains(text, ignoreCase = true) ||
        entry.note.contains(text, ignoreCase = true) ||
        (tag.isNotEmpty() && com.ced2711.lifetracker.domain.model.parseTags(entry.tagsCsv).any { it.contains(tag, ignoreCase = true) })
}
