package com.ced2711.lifetracker.ui.calendar

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.ledger.formatMoney
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.localization.uiLocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

/*
 * What the Calendar shows and how it words things, without any drawing: the views, the range of
 * days a view covers, each day's contents, and the texts with numbers and dates in them.
 */

internal enum class CalendarView(val label: String) {
    MONTH("Month"),
    WEEK("Week"),
    DAY("Day"),
    AGENDA("Agenda"),
}

/** How many days ahead the Agenda looks. */
internal const val AGENDA_DAYS = 30L

/** On wide screens Month and Week keep the selected day beside them; Day and Agenda are one column. */
internal fun usesWideCalendarMasterDetail(view: CalendarView): Boolean =
    view == CalendarView.MONTH || view == CalendarView.WEEK

/** The last day a view can show; repeating todos are created up to it. */
internal fun calendarVisibleEndEpochDay(
    selectedDate: LocalDate,
    view: CalendarView,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate = LocalDate.now(),
): Long = when (view) {
    CalendarView.MONTH -> YearMonth.from(selectedDate).atDay(1).previousOrSame(firstDayOfWeek).plusDays(41).toEpochDay()
    CalendarView.WEEK -> selectedDate.previousOrSame(firstDayOfWeek).plusDays(6).toEpochDay()
    CalendarView.DAY -> selectedDate.toEpochDay()
    CalendarView.AGENDA -> today.plusDays(AGENDA_DAYS).toEpochDay()
}

/** The title above a view: the month, the week's first and last day, the day, or the Agenda's range. */
internal fun calendarHeaderLabel(
    date: LocalDate,
    view: CalendarView,
    firstDayOfWeek: DayOfWeek,
    dateFormatter: DateTimeFormatter,
    language: UiLanguage,
): String = when (view) {
    CalendarView.MONTH -> when (language) {
        UiLanguage.SIMPLIFIED_CHINESE -> "${date.year}年${date.monthValue}月"
        UiLanguage.ENGLISH -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
    }
    CalendarView.WEEK -> {
        val start = date.previousOrSame(firstDayOfWeek)
        dateFormatter.format(start) + " – " + dateFormatter.format(start.plusDays(6))
    }
    CalendarView.DAY -> dateFormatter.format(date)
    CalendarView.AGENDA -> translateUiText("Next 30 days", language)
}

/** The same day of the month [months] away, or that month's last day when it is shorter. */
internal fun LocalDate.moveMonth(months: Long): LocalDate {
    val target = YearMonth.from(this).plusMonths(months)
    return target.atDay(dayOfMonth.coerceAtMost(target.lengthOfMonth()))
}

internal fun LocalDate.previousOrSame(dayOfWeek: DayOfWeek): LocalDate = with(TemporalAdjusters.previousOrSame(dayOfWeek))

/** The first day shown in a month's grid and how many weeks the grid has. */
internal fun monthGridStart(month: YearMonth, firstDayOfWeek: DayOfWeek): LocalDate = month.atDay(1).previousOrSame(firstDayOfWeek)

internal fun monthGridWeeks(month: YearMonth, firstDayOfWeek: DayOfWeek): Int =
    ((month.atEndOfMonth().toEpochDay() - monthGridStart(month, firstDayOfWeek).toEpochDay()) / 7 + 1).toInt()

/** Day cells narrower than this show marks and counts; wider ones have room for todo titles. */
internal val MONTH_CELL_TITLES_MIN_WIDTH: Dp = 88.dp

/**
 * The height of a week in the month grid. It grows with the font size so the number, the amount
 * and the todo marks (or three titles in roomy cells) never clip.
 */
internal fun monthGridRowHeight(fontScale: Float, withTitles: Boolean = false): Dp {
    val scale = fontScale.coerceAtLeast(1f)
    return if (withTitles) (36f + 66f * scale).dp else (10f + 54f * scale).dp
}

/** Everything that happens on each day, gathered once per change of the data. */
internal class DayContents(
    todos: List<TodoEntity>,
    ledger: List<LedgerEntryEntity>,
    diary: List<DiaryEntryEntity>,
) {
    /** Open todos first, then by time and priority. */
    val todos: Map<Long, List<TodoEntity>> = todos
        .filter { it.deletedAt == null && it.deadlineEpochDay != null }
        .groupBy { it.deadlineEpochDay!! }
        .mapValues { (_, rows) ->
            rows.sortedWith(
                compareBy<TodoEntity> { it.completedAt != null }
                    .thenBy { it.deadlineMinute ?: Int.MAX_VALUE }
                    .thenByDescending { it.priority.ordinal }
                    .thenBy { it.title },
            )
        }
    val ledger: Map<Long, List<LedgerEntryEntity>> = ledger
        .filter { it.deletedAt == null }
        .groupBy { it.epochDay }
        .mapValues { (_, rows) -> rows.sortedWith(compareBy<LedgerEntryEntity> { it.minuteOfDay }.thenBy { it.createdAt }) }
    val diary: Map<Long, DiaryEntryEntity> = diary.associateBy { it.epochDay }

    fun net(day: Long): Long = ledger[day].orEmpty().sumOf { if (it.type == LedgerType.INCOME) it.amountCents else -it.amountCents }

    /** Open todos due before [today], oldest first. */
    fun overdue(today: Long): List<TodoEntity> =
        todos.filterKeys { it < today }.values.flatten().filter { it.completedAt == null }.sortedBy { it.deadlineEpochDay }

    /** The days from [today] on that have a todo or a ledger entry, for the Agenda. */
    fun agendaDays(today: Long): List<Long> =
        (today..today + AGENDA_DAYS).filter { !todos[it].isNullOrEmpty() || !ledger[it].isNullOrEmpty() }
}

/** An amount short enough for a day cell: 37, 1.5k, 145k, 2.4M. [cents] is positive. */
internal fun compactAmount(cents: Long): String {
    fun short(value: Double, unit: String): String =
        (if (value >= 100) "%.0f" else "%.1f").format(Locale.US, value).removeSuffix(".0") + unit
    return when {
        cents >= 99_950_000 -> short(cents / 100_000_000.0, "M")
        cents >= 100_000 -> short(cents / 100_000.0, "k")
        else -> "%.0f".format(Locale.US, cents / 100.0)
    }
}

/** A day cell's amount with its sign. */
internal fun compactSignedAmount(cents: Long): String = (if (cents < 0) "−" else "+") + compactAmount(abs(cents))

/** Money with a sign in front, as in the Ledger: +$12.00 or −$3.50. */
internal fun signedMoney(cents: Long): String = (if (cents < 0) "−" else "+") + formatMoney(abs(cents))

/** "+2 more" under the todo titles that fit in a day cell. */
internal fun moreCount(count: Int, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "+$count more"
    UiLanguage.SIMPLIFIED_CHINESE -> "还有 $count 项"
}

/** Today, Tomorrow, Yesterday, or the short weekday and the date in the user's format. */
internal fun relativeDayLabel(day: Long, today: LocalDate, settings: AppSettings, language: UiLanguage): String {
    val locale = uiLocale(language)
    val date = LocalDate.ofEpochDay(day)
    return when (date) {
        today -> translateUiText("Today", language)
        today.plusDays(1) -> translateUiText("Tomorrow", language)
        today.minusDays(1) -> translateUiText("Yesterday", language)
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + UserFormatting.formatDate(date, settings.dateFormat, locale)
    }
}

/** What a screen reader says for a day cell: the date, its todos, its money and whether it has a diary page. */
internal fun daySummary(
    date: LocalDate,
    doneTodos: Int,
    openTodos: Int,
    netCents: Long,
    hasDiary: Boolean,
    isToday: Boolean,
    settings: AppSettings,
    language: UiLanguage,
): String {
    val locale = uiLocale(language)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    val dateText = UserFormatting.formatDate(date, settings.dateFormat, locale)
    return when (language) {
        UiLanguage.ENGLISH -> buildString {
            append("$weekday, $dateText")
            if (isToday) append(", today")
            if (doneTodos + openTodos > 0) append(", $doneTodos done, $openTodos open")
            if (netCents != 0L) append(", net ${signedMoney(netCents)}")
            if (hasDiary) append(", diary")
        }
        UiLanguage.SIMPLIFIED_CHINESE -> buildString {
            append("$weekday，$dateText")
            if (isToday) append("，今天")
            if (doneTodos + openTodos > 0) append("，已完成 $doneTodos 项，未完成 $openTodos 项")
            if (netCents != 0L) append("，净额 ${signedMoney(netCents)}")
            if (hasDiary) append("，有日记")
        }
    }
}
