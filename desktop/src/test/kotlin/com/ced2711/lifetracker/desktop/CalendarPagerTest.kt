package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.ui.design.CalendarPager
import com.ced2711.lifetracker.ui.design.monthOfPage
import com.ced2711.lifetracker.ui.design.monthPage
import com.ced2711.lifetracker.ui.design.weekPage
import com.ced2711.lifetracker.ui.design.weekStartOfPage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The calendar's pages: they follow a swipe, settle or spring back, and slide to a page chosen elsewhere. */
@OptIn(ExperimentalTestApi::class)
class CalendarPagerTest {
    @Test
    fun pageNumbersMatchMonthsWeeksAndDays() {
        val october = LocalDate.of(2026, 10, 12)
        assertEquals(YearMonth.of(2026, 10), monthOfPage(monthPage(october)))
        assertEquals(monthPage(october) + 1, monthPage(october.plusMonths(1)))
        assertEquals(YearMonth.of(2027, 1), monthOfPage(monthPage(LocalDate.of(2026, 12, 31)) + 1))
        listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY).forEach { first ->
            val start = october.with(java.time.temporal.TemporalAdjusters.previousOrSame(first))
            assertEquals(start, weekStartOfPage(weekPage(start), first))
            assertEquals(start.plusWeeks(1), weekStartOfPage(weekPage(start) + 1, first))
            assertEquals(weekPage(start) + 1, weekPage(start.plusWeeks(1)))
        }
    }

    @Test
    fun aSwipeTurnsThePageAndASmallOneSpringsBack() = runDesktopComposeUiTest(400, 300) {
        var page by mutableIntStateOf(10)
        val reported = mutableStateListOf<Int>()
        setContent {
            CalendarPager(page = page, onPageChange = { reported += it; page = it }, modifier = Modifier.fillMaxSize().testTag("pager")) { shown ->
                Box(Modifier.fillMaxSize()) { Text("Page $shown") }
            }
        }
        onNodeWithText("Page 10").assertExists()

        // A little drag: back to where it was, nothing reported.
        onNodeWithTag("pager").performTouchInput { swipe(Offset(200f, 150f), Offset(170f, 150f), durationMillis = 400) }
        waitForIdle()
        assertTrue(reported.isEmpty())
        assertEquals(10, page)

        // A clear swipe to the left: the next page.
        onNodeWithTag("pager").performTouchInput { swipe(Offset(350f, 150f), Offset(30f, 150f), durationMillis = 200) }
        waitForIdle()
        assertEquals(listOf(11), reported.toList())
        onNodeWithText("Page 11").assertExists()

        // And to the right: back.
        onNodeWithTag("pager").performTouchInput { swipe(Offset(30f, 150f), Offset(350f, 150f), durationMillis = 200) }
        waitForIdle()
        assertEquals(listOf(11, 10), reported.toList())
    }

    @Test
    fun aPageChosenElsewhereSlidesInAndAFarOneArrivesAtOnce() = runDesktopComposeUiTest(400, 300) {
        var page by mutableIntStateOf(10)
        val reported = mutableStateListOf<Int>()
        setContent {
            CalendarPager(page = page, onPageChange = { reported += it; page = it }, modifier = Modifier.fillMaxSize()) { shown ->
                Box(Modifier.fillMaxSize()) { Text("Page $shown") }
            }
        }
        page = 11
        waitForIdle()
        onNodeWithText("Page 11").assertExists()
        page = 9
        waitForIdle()
        onNodeWithText("Page 9").assertExists()
        page = 500
        waitForIdle()
        onNodeWithText("Page 500").assertExists()
        assertTrue("pages chosen elsewhere are not reported back", reported.isEmpty())
    }

    @Test
    fun theMouseWheelDoesNotTurnThePage() = runDesktopComposeUiTest(400, 300) {
        var page by mutableIntStateOf(10)
        setContent {
            CalendarPager(page = page, onPageChange = { page = it }, modifier = Modifier.size(400.dp, 300.dp).testTag("pager")) { shown ->
                Box(Modifier.fillMaxSize()) { Text("Page $shown") }
            }
        }
        onNodeWithTag("pager").performMouseInput { scroll(3f) }
        waitForIdle()
        assertEquals(10, page)
    }
}
