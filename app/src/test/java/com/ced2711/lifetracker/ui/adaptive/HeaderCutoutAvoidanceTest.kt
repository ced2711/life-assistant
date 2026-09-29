package com.ced2711.lifetracker.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderCutoutAvoidanceTest {
    // A 1080 px wide header, 147 px tall, at the top of the window.
    private fun padding(island: CutoutIsland?) = headerCutoutPadding(
        barLeft = 0, barTop = 0, barRight = 1080, barBottom = 147,
        titleStart = 42, trailingWidth = 137, minimumTitleWidth = 126, gap = 21,
        island = island,
    )

    @Test
    fun centredPunchHoleOnlyShortensTheTitle() {
        assertEquals(HeaderCutoutPadding(titleEnd = 943 - (500 - 21)), padding(CutoutIsland(500, 20, 580, 90)))
    }

    @Test
    fun cornerCameraOverTheTitleMovesTheWholeRow() {
        assertEquals(HeaderCutoutPadding(start = 150 + 21), padding(CutoutIsland(60, 20, 150, 90)))
    }

    @Test
    fun cameraOverTheSettingsButtonMovesItLeft() {
        assertEquals(HeaderCutoutPadding(end = 1080 - (960 - 21)), padding(CutoutIsland(960, 20, 1040, 90)))
    }

    @Test
    fun cameraOutsideTheHeaderChangesNothing() {
        assertEquals(HeaderCutoutPadding(), padding(null))
        assertEquals(HeaderCutoutPadding(), padding(CutoutIsland(500, 200, 580, 260)))
    }

    @Test
    fun onlySmallCutoutsCountAsIslands() {
        assertTrue(isTopCutoutIsland(cutoutWidthPx = 90, windowWidthPx = 1080))
        assertFalse(isTopCutoutIsland(cutoutWidthPx = 600, windowWidthPx = 1080))
        assertFalse(isTopCutoutIsland(cutoutWidthPx = 0, windowWidthPx = 1080))
    }
}
