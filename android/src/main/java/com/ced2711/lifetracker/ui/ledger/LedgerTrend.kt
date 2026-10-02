package com.ced2711.lifetracker.ui.ledger

import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.LedgerType
import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * The numbers behind the trend chart: how a period is cut into bars, how the axis is scaled and
 * labelled, and which bar a tap lands on. Plain logic, covered by unit tests.
 */


internal const val MAX_LEDGER_TREND_POINTS = 120

internal data class TrendPoint(
    val incomeCents: Long,
    val expenseCents: Long,
    val axisLabel: String,
)

internal data class LedgerTrendResult(
    val points: List<TrendPoint>,
    val notice: String? = null,
)

internal data class TrendSelectionSummary(
    val label: String,
    val incomeText: String,
    val expensesText: String,
) {
    val accessibilityDescription: String
        get() = "$label. $incomeText. $expensesText."
}

internal fun trendPoints(
    entries: List<LedgerEntryEntity>,
    range: Pair<LocalDate, LocalDate>,
    dateFormat: DateFormatOption,
    locale: Locale,
): LedgerTrendResult {
    val dayCount = ChronoUnit.DAYS.between(range.first, range.second) + 1
    if (dayCount <= 46) {
        val dayFormatter = statisticsDayAxisFormatter(dateFormat, locale)
        val grouped = entries.groupBy(LedgerEntryEntity::epochDay)
        return LedgerTrendResult(
            points = List(dayCount.toInt()) { offset ->
                val date = range.first.plusDays(offset.toLong())
                trendPoint(grouped[date.toEpochDay()].orEmpty(), date.format(dayFormatter))
            },
        )
    }

    val firstMonth = range.first.withDayOfMonth(1)
    val lastMonth = range.second.withDayOfMonth(1)
    val monthCount = ChronoUnit.MONTHS.between(firstMonth, lastMonth) + 1
    val spansMultipleYears = range.first.year != range.second.year
    val monthFormatter = statisticsMonthAxisFormatter(dateFormat, locale, spansMultipleYears)
    if (monthCount <= MAX_LEDGER_TREND_POINTS) {
        val grouped = entries.groupBy { entry ->
            val date = LocalDate.ofEpochDay(entry.epochDay)
            date.year * 12L + date.monthValue - 1L
        }
        return LedgerTrendResult(
            points = List(monthCount.toInt()) { offset ->
                val date = firstMonth.plusMonths(offset.toLong())
                val key = date.year * 12L + date.monthValue - 1L
                trendPoint(grouped[key].orEmpty(), date.format(monthFormatter))
            },
        )
    }

    val firstYear = range.first.year
    val yearCount = range.second.year - firstYear + 1
    val yearsPerBucket = (yearCount + MAX_LEDGER_TREND_POINTS - 1) / MAX_LEDGER_TREND_POINTS
    val bucketCount = (yearCount + yearsPerBucket - 1) / yearsPerBucket
    val grouped = entries.groupBy { entry ->
        (LocalDate.ofEpochDay(entry.epochDay).year - firstYear) / yearsPerBucket
    }
    return LedgerTrendResult(
        points = List(bucketCount) { index ->
            val startYear = firstYear + index * yearsPerBucket
            val endYear = minOf(startYear + yearsPerBucket - 1, range.second.year)
            val label = if (startYear == endYear) "$startYear" else "$startYear–$endYear"
            trendPoint(grouped[index].orEmpty(), label)
        },
        notice = if (yearsPerBucket == 1) {
            "Long range summarized by year to keep the chart readable."
        } else {
            "Long range summarized in $yearsPerBucket-year groups to keep the chart readable."
        }
    )
}

private fun statisticsDayAxisFormatter(
    dateFormat: DateFormatOption,
    locale: Locale,
): DateTimeFormatter {
    val fieldOrder = dateFieldOrder(dateFormat, locale)
    val dayBeforeMonth = fieldOrder.indexOf('d') < fieldOrder.indexOf('M')
    val separator = if (dateFormat == DateFormatOption.YEAR_MONTH_DAY) "-" else "/"
    return DateTimeFormatter.ofPattern(
        if (dayBeforeMonth) "d${separator}M" else "M${separator}d",
        locale,
    )
}

private fun statisticsMonthAxisFormatter(
    dateFormat: DateFormatOption,
    locale: Locale,
    includesYear: Boolean,
): DateTimeFormatter {
    if (!includesYear) return DateTimeFormatter.ofPattern("MMM", locale)
    val fieldOrder = dateFieldOrder(dateFormat, locale)
    val yearBeforeMonth = fieldOrder.indexOf('y') < fieldOrder.indexOf('M')
    return DateTimeFormatter.ofPattern(if (yearBeforeMonth) "yy MMM" else "MMM yy", locale)
}

private fun dateFieldOrder(dateFormat: DateFormatOption, locale: Locale): List<Char> = when (dateFormat) {
    DateFormatOption.MONTH_DAY_YEAR -> listOf('M', 'd', 'y')
    DateFormatOption.DAY_MONTH_YEAR -> listOf('d', 'M', 'y')
    DateFormatOption.YEAR_MONTH_DAY -> listOf('y', 'M', 'd')
    DateFormatOption.SYSTEM -> localizedDatePattern(locale).dateFieldOrder()
}

private fun localizedDatePattern(locale: Locale): String =
    DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        FormatStyle.SHORT,
        null,
        IsoChronology.INSTANCE,
        locale,
    )

private fun String.dateFieldOrder(): List<Char> {
    val fields = mutableListOf<Char>()
    var quoted = false
    forEach { character ->
        when {
            character == '\'' -> quoted = !quoted
            !quoted && character in "yYuUr" && 'y' !in fields -> fields += 'y'
            !quoted && character in "ML" && 'M' !in fields -> fields += 'M'
            !quoted && character == 'd' && 'd' !in fields -> fields += 'd'
        }
    }
    return fields
}

private fun trendPoint(entries: List<LedgerEntryEntity>, label: String): TrendPoint = TrendPoint(
    incomeCents = entries.filter { it.type == LedgerType.INCOME }.sumOf { it.amountCents },
    expenseCents = entries.filter { it.type == LedgerType.EXPENSE }.sumOf { it.amountCents },
    axisLabel = label,
)

internal fun chartAxisMaximum(maximumCents: Long): Long {
    if (maximumCents <= 0L) return 100L
    val padding = maximumCents / 10L + if (maximumCents % 10L == 0L) 0L else 1L
    return if (maximumCents > Long.MAX_VALUE - padding) {
        Long.MAX_VALUE
    } else {
        maximumCents + padding
    }
}

internal fun trendPointIndexAtX(
    x: Float,
    chartStartX: Float,
    chartWidth: Float,
    pointCount: Int,
): Int? {
    if (
        pointCount <= 0 ||
        !x.isFinite() ||
        !chartStartX.isFinite() ||
        !chartWidth.isFinite() ||
        chartWidth <= 0f ||
        x < chartStartX ||
        x > chartStartX + chartWidth
    ) {
        return null
    }
    if (pointCount == 1) return 0
    val fraction = ((x - chartStartX) / chartWidth).coerceIn(0f, 1f)
    return (fraction * pointCount).toInt().coerceAtMost(pointCount - 1)
}

internal fun trendSelectionSummary(
    points: List<TrendPoint>,
    index: Int,
): TrendSelectionSummary? = points.getOrNull(index)?.let { point ->
    TrendSelectionSummary(
        label = point.axisLabel,
        incomeText = "Income ${formatMoney(point.incomeCents)}",
        expensesText = "Expenses ${formatMoney(point.expenseCents)}",
    )
}

internal fun formatCompactAxisMoney(cents: Long): String = when {
    cents >= 100_000_000_000L -> String.format(Locale.US, "$%.1fB", cents / 100_000_000_000.0)
    cents >= 100_000_000L -> String.format(Locale.US, "$%.1fM", cents / 100_000_000.0)
    cents >= 100_000L -> String.format(Locale.US, "$%.1fK", cents / 100_000.0)
    cents % 100L == 0L -> "$${cents / 100L}"
    else -> String.format(Locale.US, "$%.2f", cents / 100.0)
}
