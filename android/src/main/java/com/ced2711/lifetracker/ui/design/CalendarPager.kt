package com.ced2711.lifetracker.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * Calendar pages (months, weeks or days) side by side. A swipe moves them with the finger, settles
 * on the next page when let go far or fast enough and springs back otherwise; a change from the
 * arrows, the keyboard or Today slides over from the side it comes from. [page] numbers the shown
 * page from any fixed start (a month, week or day index); [onPageChange] reports the page a swipe
 * settled on, and [content] draws a page by its number.
 */
@Composable
fun CalendarPager(
    page: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int) -> Unit,
) {
    // Pager indexes around the page shown first, far enough either way for any real use.
    val start = remember { page }
    val state = rememberPagerState(initialPage = CALENDAR_PAGER_MIDDLE) { CALENDAR_PAGER_COUNT }
    val shown by rememberUpdatedState(page)
    val report by rememberUpdatedState(onPageChange)

    LaunchedEffect(page) {
        val target = CALENDAR_PAGER_MIDDLE + (page - start)
        if (state.currentPage == target || target !in 0 until CALENDAR_PAGER_COUNT) return@LaunchedEffect
        // Neighbours slide; a jump across many pages (such as Today from far away) arrives at once.
        if (abs(target - state.currentPage) > 2) {
            state.scrollToPage(target)
        } else {
            state.animateScrollToPage(target, animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.collect { settled ->
            val settledOn = start + (settled - CALENDAR_PAGER_MIDDLE)
            if (settledOn != shown) report(settledOn)
        }
    }
    HorizontalPager(
        state = state,
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        key = { it },
    ) { index ->
        content(start + (index - CALENDAR_PAGER_MIDDLE))
    }
}

/** The month of [date] as a [CalendarPager] page; neighbouring months are neighbouring pages. */
fun monthPage(date: LocalDate): Int = date.year * 12 + date.monthValue - 1

fun monthOfPage(page: Int): YearMonth = YearMonth.of(Math.floorDiv(page, 12), Math.floorMod(page, 12) + 1)

/** The week starting on [weekStart] as a [CalendarPager] page. */
fun weekPage(weekStart: LocalDate): Int = Math.floorDiv(weekStart.toEpochDay(), 7L).toInt()

/** The first day of the week numbered [page], for weeks starting on [firstDayOfWeek]. */
fun weekStartOfPage(page: Int, firstDayOfWeek: DayOfWeek): LocalDate =
    LocalDate.ofEpochDay(page * 7L).with(TemporalAdjusters.nextOrSame(firstDayOfWeek))

/** The day [date] as a [CalendarPager] page. */
fun dayPage(date: LocalDate): Int = date.toEpochDay().toInt()

private const val CALENDAR_PAGER_COUNT = 200_000
private const val CALENDAR_PAGER_MIDDLE = CALENDAR_PAGER_COUNT / 2
