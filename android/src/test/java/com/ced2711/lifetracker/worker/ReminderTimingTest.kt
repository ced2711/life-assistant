package com.ced2711.lifetracker.worker

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimingTest {
    @Test
    fun `positive offset means minutes before deadline`() {
        val window = requireNotNull(
            calculateReminderWindow(
                deadlineEpochDay = LocalDate.of(2026, 8, 20).toEpochDay(),
                deadlineMinute = 12 * 60,
                defaultAllDayMinute = 0,
                offsetMinutes = 60,
                zone = ZoneId.of("UTC"),
            ),
        )

        assertEquals(Instant.parse("2026-08-20T11:00:00Z").toEpochMilli(), window.triggerAtMillis)
        assertEquals(Instant.parse("2026-08-20T12:00:00Z").toEpochMilli(), window.expiresAtMillis)
    }

    @Test
    fun `negative before offset is rejected`() {
        assertNull(
            calculateReminderWindow(
                deadlineEpochDay = LocalDate.of(2026, 8, 20).toEpochDay(),
                deadlineMinute = 12 * 60,
                defaultAllDayMinute = 0,
                offsetMinutes = -60,
                zone = ZoneId.of("UTC"),
            ),
        )
    }

    @Test
    fun `timed due reminder remains useful through the local due date`() {
        val window = requireNotNull(
            calculateReminderWindow(
                deadlineEpochDay = LocalDate.of(2026, 8, 20).toEpochDay(),
                deadlineMinute = 12 * 60,
                defaultAllDayMinute = 0,
                offsetMinutes = 0,
                zone = ZoneId.of("UTC"),
            ),
        )

        assertEquals(ReminderWindowState.EARLY, window.stateAt(window.triggerAtMillis - 1))
        assertEquals(ReminderWindowState.DUE, window.stateAt(window.triggerAtMillis))
        assertEquals(Instant.parse("2026-08-21T00:00:00Z").toEpochMilli(), window.expiresAtMillis)
        assertEquals(ReminderWindowState.DUE, window.stateAt(window.expiresAtMillis - 1))
        assertEquals(ReminderWindowState.EXPIRED, window.stateAt(window.expiresAtMillis))
    }

    @Test
    fun `all-day due reminder remains useful through the local due date`() {
        val zone = ZoneId.of("America/New_York")
        val window = requireNotNull(
            calculateReminderWindow(
                deadlineEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
                deadlineMinute = null,
                defaultAllDayMinute = 8 * 60,
                offsetMinutes = 0,
                zone = zone,
            ),
        )

        assertEquals(Instant.parse("2026-11-01T13:00:00Z").toEpochMilli(), window.triggerAtMillis)
        assertEquals(Instant.parse("2026-11-02T05:00:00Z").toEpochMilli(), window.expiresAtMillis)
    }

    @Test
    fun `deadline minute remains local wall time across daylight-saving gap`() {
        val window = requireNotNull(
            calculateReminderWindow(
                deadlineEpochDay = LocalDate.of(2026, 3, 8).toEpochDay(),
                deadlineMinute = 10 * 60,
                defaultAllDayMinute = 0,
                offsetMinutes = 0,
                zone = ZoneId.of("America/New_York"),
            ),
        )

        assertEquals(Instant.parse("2026-03-08T14:00:00Z").toEpochMilli(), window.triggerAtMillis)
        assertTrue(window.expiresAtMillis > window.triggerAtMillis)
    }
}
