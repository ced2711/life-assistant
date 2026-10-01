package com.ced2711.lifetracker.domain.date

import java.time.DateTimeException
import java.time.LocalDate

/** Parses the compact US-style deadline inputs supported by the todo editor. */
object SmartDateParser {
    const val MIN_SUPPORTED_EPOCH_DAY = -719_162L // 0001-01-01
    const val MAX_SUPPORTED_EPOCH_DAY = 2_932_896L // 9999-12-31

    private val dayOnly = Regex("^(\\d{1,2})$")
    private val monthAndDay = Regex("^(\\d{1,2})/(\\d{1,2})$")
    private val fullDate = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$")

    fun parse(input: String, todayEpochDay: Long): Long? =
        parse(input, LocalDate.ofEpochDay(todayEpochDay))?.toEpochDay()

    fun parse(input: String, today: LocalDate): LocalDate? {
        val value = input.trim()

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

        monthAndDay.matchEntire(value)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val thisYear = dateOrNull(today.year, month, day) ?: return null
            return if (thisYear < today) {
                dateOrNull(today.year + 1, month, day)
            } else {
                thisYear
            }
        }

        fullDate.matchEntire(value)?.let { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt()
            if (year == 0) return null
            return dateOrNull(year, month, day)
        }

        return null
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
