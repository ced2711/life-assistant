package com.ced2711.lifetracker.domain.date

import com.ced2711.lifetracker.domain.model.DateFormatOption
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * Reads the dates people type into date fields: a day ("15"), a month and day ("8/15"), a full
 * date in the user's chosen order (or ISO 2026-08-15 always), and words such as today, tomorrow,
 * fri or 周五 (the next such day, today included). A day or month/day that already passed rolls
 * forward. Field contents shown by the app (in the user's date format) read back unchanged.
 */
object SmartDateParser {
    const val MIN_SUPPORTED_EPOCH_DAY = -719_162L // 0001-01-01
    const val MAX_SUPPORTED_EPOCH_DAY = 2_932_896L // 9999-12-31

    private val dayOnly = Regex("^(\\d{1,2})$")
    private val twoParts = Regex("^(\\d{1,2})[/.\\-](\\d{1,2})$")
    private val threeParts = Regex("^(\\d{1,4})[/.\\-](\\d{1,2})[/.\\-](\\d{1,4})$")
    private val chineseDate = Regex("^(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日号]?$")

    fun parse(input: String, todayEpochDay: Long): Long? =
        parse(input, LocalDate.ofEpochDay(todayEpochDay))?.toEpochDay()

    /** US order (month/day/year) for numbers, as before the date format setting existed. */
    fun parse(input: String, today: LocalDate): LocalDate? = parse(input, today, DateFormatOption.MONTH_DAY_YEAR)

    fun parse(input: String, today: LocalDate, format: DateFormatOption, locale: Locale = Locale.getDefault()): LocalDate? {
        val value = input.trim().lowercase(Locale.ROOT)
        if (value.isEmpty()) return null
        words(value, today)?.let { return it }
        chineseDate.matchEntire(value)?.let { match ->
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            val year = match.groupValues[1].toIntOrNull()
            return if (year != null) dateOrNull(year, month, day) else rollYear(today, month, day)
        }
        val order = effectiveOrder(format, locale)

        dayOnly.matchEntire(value)?.let { match ->
            val day = match.groupValues[1].toInt()
            val thisMonth = dateOrNull(today.year, today.monthValue, day) ?: return null
            return if (thisMonth < today) {
                val nextMonth = today.plusMonths(1)
                dateOrNull(nextMonth.year, nextMonth.monthValue, day)
            } else {
                thisMonth
            }
        }

        twoParts.matchEntire(value)?.let { match ->
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].toInt()
            val (month, day) = if (order == DateFormatOption.DAY_MONTH_YEAR) second to first else first to second
            return rollYear(today, month, day)
        }

        threeParts.matchEntire(value)?.let { match ->
            val a = match.groupValues[1]
            val b = match.groupValues[2].toInt()
            val c = match.groupValues[3]
            // A four-digit first part is a year whatever the setting: 2026-08-15.
            if (a.length == 4) return dateOrNull(a.toInt(), b, c.toIntOrNull() ?: return null)
            if (c.length != 4) return null
            val year = c.toInt()
            if (year == 0) return null
            return when (order) {
                DateFormatOption.DAY_MONTH_YEAR -> dateOrNull(year, b, a.toInt())
                else -> dateOrNull(year, a.toInt(), b)
            }
        }
        return null
    }

    /** The order numbers are read in; "system" follows the locale's short date pattern. */
    fun effectiveOrder(format: DateFormatOption, locale: Locale = Locale.getDefault()): DateFormatOption = when (format) {
        DateFormatOption.SYSTEM -> when {
            locale.language == "zh" || locale.language == "ja" || locale.language == "ko" -> DateFormatOption.YEAR_MONTH_DAY
            locale.country in setOf("US", "PH", "CA", "FM") || (locale.language == "en" && locale.country.isEmpty()) -> DateFormatOption.MONTH_DAY_YEAR
            else -> DateFormatOption.DAY_MONTH_YEAR
        }
        else -> format
    }

    private fun words(value: String, today: LocalDate): LocalDate? = when (value) {
        "today", "tod", "今天", "今日" -> today
        "tomorrow", "tmr", "tom", "明天", "明日" -> today.plusDays(1)
        "yesterday", "昨天" -> today.minusDays(1)
        "后天" -> today.plusDays(2)
        "next week", "下周" -> today.plusWeeks(1)
        else -> weekday(value)?.let { day ->
            today.plusDays(((day.value - today.dayOfWeek.value + 7) % 7).toLong())
        }
    }

    private fun weekday(value: String): DayOfWeek? {
        val english = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        english.indexOfFirst { value.startsWith(it) && value.length <= 9 }.takeIf { it >= 0 }?.let { return DayOfWeek.of(it + 1) }
        val chinese = listOf("一", "二", "三", "四", "五", "六", "日")
        val stripped = value.removePrefix("周").removePrefix("星期").removePrefix("礼拜")
        if (stripped != value) {
            val index = if (stripped == "天") 6 else chinese.indexOf(stripped)
            if (index >= 0) return DayOfWeek.of(index + 1)
        }
        return null
    }

    private fun rollYear(today: LocalDate, month: Int, day: Int): LocalDate? {
        val thisYear = dateOrNull(today.year, month, day) ?: return null
        return if (thisYear < today) dateOrNull(today.year + 1, month, day) else thisYear
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day).takeIf {
                it.toEpochDay() in MIN_SUPPORTED_EPOCH_DAY..MAX_SUPPORTED_EPOCH_DAY
            }
        } catch (_: DateTimeException) {
            null
        }
}
