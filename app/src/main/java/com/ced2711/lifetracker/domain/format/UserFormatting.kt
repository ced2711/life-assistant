package com.ced2711.lifetracker.domain.format

import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.WeekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Shared formatting rules for values controlled by the app's regional settings. */
object UserFormatting {
    fun dateFormatter(
        option: DateFormatOption,
        locale: Locale = Locale.getDefault(),
    ): DateTimeFormatter = when (option) {
        DateFormatOption.SYSTEM -> DateTimeFormatter
            .ofLocalizedDate(FormatStyle.SHORT)
            .withLocale(locale)
        DateFormatOption.MONTH_DAY_YEAR -> DateTimeFormatter.ofPattern("MM/dd/yyyy", locale)
        DateFormatOption.DAY_MONTH_YEAR -> DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
        DateFormatOption.YEAR_MONTH_DAY -> DateTimeFormatter.ofPattern("yyyy-MM-dd", locale)
    }

    fun formatDate(
        date: LocalDate,
        option: DateFormatOption,
        locale: Locale = Locale.getDefault(),
    ): String = date.format(dateFormatter(option, locale))

    fun uses24HourClock(
        option: TimeFormatOption,
        systemUses24Hour: Boolean,
    ): Boolean = when (option) {
        TimeFormatOption.SYSTEM -> systemUses24Hour
        TimeFormatOption.HOUR_12 -> false
        TimeFormatOption.HOUR_24 -> true
    }

    fun timeFormatter(
        option: TimeFormatOption,
        systemUses24Hour: Boolean,
        locale: Locale = Locale.getDefault(),
    ): DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (uses24HourClock(option, systemUses24Hour)) "HH:mm" else "h:mm a",
        locale,
    )

    fun formatTime(
        time: LocalTime,
        option: TimeFormatOption,
        systemUses24Hour: Boolean,
        locale: Locale = Locale.getDefault(),
    ): String = time.format(timeFormatter(option, systemUses24Hour, locale))

    fun formatMinuteOfDay(
        minuteOfDay: Int,
        option: TimeFormatOption,
        systemUses24Hour: Boolean,
        locale: Locale = Locale.getDefault(),
    ): String = formatTime(
        time = LocalTime.ofSecondOfDay(minuteOfDay.coerceIn(0, 1_439) * 60L),
        option = option,
        systemUses24Hour = systemUses24Hour,
        locale = locale,
    )

    fun firstDayOfWeek(
        option: WeekStart,
        locale: Locale = Locale.getDefault(),
    ): DayOfWeek = when (option) {
        WeekStart.SYSTEM -> WeekFields.of(locale).firstDayOfWeek
        WeekStart.SUNDAY -> DayOfWeek.SUNDAY
        WeekStart.MONDAY -> DayOfWeek.MONDAY
    }

    fun orderedDaysOfWeek(
        option: WeekStart,
        locale: Locale = Locale.getDefault(),
    ): List<DayOfWeek> {
        val firstDay = firstDayOfWeek(option, locale)
        return List(DayOfWeek.entries.size) { index ->
            DayOfWeek.of((firstDay.value - 1 + index) % DayOfWeek.entries.size + 1)
        }
    }

    fun formatWeekday(
        dayOfWeek: DayOfWeek,
        locale: Locale = Locale.getDefault(),
        textStyle: TextStyle = TextStyle.SHORT,
    ): String = dayOfWeek.getDisplayName(textStyle, locale)
}
