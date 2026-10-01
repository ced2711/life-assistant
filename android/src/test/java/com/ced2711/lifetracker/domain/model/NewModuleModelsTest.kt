package com.ced2711.lifetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NewModuleModelsTest {
    @Test
    fun visibleDestinationsKeepEnumOrderAndNeverBecomeEmpty() {
        assertEquals(
            listOf(TopLevelDestination.TODO, TopLevelDestination.DIARY),
            normalizeVisibleDestinations(setOf(TopLevelDestination.DIARY, TopLevelDestination.TODO)),
        )
        assertEquals(TopLevelDestination.entries, normalizeVisibleDestinations(emptySet()))
    }

    @Test
    fun hiddenRememberedDestinationFallsBackToTheFirstVisibleModule() {
        val visible = listOf(TopLevelDestination.CALENDAR, TopLevelDestination.DIARY)
        assertEquals(TopLevelDestination.CALENDAR, resolveVisibleDestination(TopLevelDestination.TODO, visible))
        assertEquals(TopLevelDestination.DIARY, resolveVisibleDestination(TopLevelDestination.DIARY, visible))
    }

    @Test
    fun appLockExpiresOnlyAfterItsTimeoutInTheBackground() {
        assertFalse(appLockExpired(null, 10_000, AppLockTimeout.IMMEDIATELY))
        assertTrue(appLockExpired(10_000, 10_000, AppLockTimeout.IMMEDIATELY))
        assertFalse(appLockExpired(10_000, 69_999, AppLockTimeout.ONE_MINUTE))
        assertTrue(appLockExpired(10_000, 70_000, AppLockTimeout.ONE_MINUTE))
        // A clock that went backwards (for example after a reboot) must not keep the app open.
        assertTrue(appLockExpired(10_000, 5_000, AppLockTimeout.FIVE_MINUTES))
    }

    @Test
    fun diaryPreviewUsesTheFirstNonBlankLine() {
        assertEquals("Second line", diaryPreview("\n  \n  Second line  \nThird"))
        assertEquals("", diaryPreview("   "))
        assertEquals("abcd…", diaryPreview("abcdefgh", maximumLength = 5))
    }

    @Test
    fun sealedConfessionsRoundTripThroughTheirCodec() {
        val entries = listOf(
            ConfessionEntry("b", 2, "第二条 🔥"),
            ConfessionEntry("a", 1, "first\nline"),
        )
        assertEquals(entries, ConfessionCodec.decode(ConfessionCodec.encode(entries)))
        assertEquals(emptyList<ConfessionEntry>(), ConfessionCodec.decode(ConfessionCodec.encode(emptyList())))
    }

    @Test
    fun confessionCodecRejectsDamagedData() {
        val encoded = ConfessionCodec.encode(listOf(ConfessionEntry("a", 1, "x")))
        assertThrows(IllegalArgumentException::class.java) { ConfessionCodec.decode(encoded + 0) }
        assertThrows(IllegalArgumentException::class.java) {
            ConfessionCodec.decode(encoded.copyOf().also { it[0] = 0 })
        }
    }

    @Test
    fun blankOrOversizedConfessionsCannotBeSealed() {
        assertThrows(IllegalArgumentException::class.java) { newConfessionEntry("  ", 1) }
        assertThrows(IllegalArgumentException::class.java) {
            newConfessionEntry("x".repeat(MAX_CONFESSION_LENGTH + 1), 1)
        }
        assertEquals("keep", newConfessionEntry("keep", 5).text)
    }
}
