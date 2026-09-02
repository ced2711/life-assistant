package com.ced2711.lifetracker.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafePaneLayoutTest {
    @Test
    fun unfoldedWindowUsesActualFeaturePaneForWideLayoutBreakpoint() {
        val splitLayout = SafePaneLayout(
            windowWidth = 900.dp,
            windowHeight = 700.dp,
            primaryPane = SafePaneBounds(450.dp, 0.dp, 900.dp, 700.dp),
            secondaryPane = SafePaneBounds(0.dp, 0.dp, 450.dp, 700.dp),
            separatingFeatureBounds = SafePaneBounds(450.dp, 0.dp, 450.dp, 700.dp),
            splitAxis = SafePaneAxis.VERTICAL,
        )

        assertFalse(usesWideFeatureLayout(measuredWidth = 900.dp, safePaneLayout = splitLayout))
        assertTrue(usesWideFeatureLayout(measuredWidth = 900.dp, safePaneLayout = null))
    }

    @Test
    fun chromeThresholdUsesSpaceRemainingAfterSafeDrawingInsets() {
        assertEquals(40.dp, usablePaneExtent(extent = 64.dp, safeDrawingInsets = 24.dp))
        assertEquals(0.dp, usablePaneExtent(extent = 16.dp, safeDrawingInsets = 24.dp))
    }

    private val window = PixelPaneBounds(left = 0, top = 0, right = 2_000, bottom = 1_200)

    @Test
    fun noSeparatingFeatureUsesCompleteWindow() {
        val result = calculateSafeRegions(
            window = window,
            separatingFeature = null,
            splitAxis = null,
        )

        assertEquals(window, result.primary)
        assertNull(result.secondary)
        assertNull(result.separator)
        assertNull(result.splitAxis)
    }

    @Test
    fun verticalHingeExcludesItsWidthAndKeepsBothPanes() {
        val result = calculateSafeRegions(
            window = window,
            separatingFeature = PixelPaneBounds(980, 0, 1_020, 1_200),
            splitAxis = SafePaneAxis.VERTICAL,
        )

        // Equal regions intentionally prefer the trailing pane for feature content.
        assertEquals(PixelPaneBounds(1_020, 0, 2_000, 1_200), result.primary)
        assertEquals(PixelPaneBounds(0, 0, 980, 1_200), result.secondary)
        assertEquals(PixelPaneBounds(980, 0, 1_020, 1_200), result.separator)
        assertEquals(SafePaneAxis.VERTICAL, result.splitAxis)
    }

    @Test
    fun horizontalHingeKeepsLargerLowerPane() {
        val result = calculateSafeRegions(
            window = window,
            separatingFeature = PixelPaneBounds(0, 500, 2_000, 540),
            splitAxis = SafePaneAxis.HORIZONTAL,
        )

        assertEquals(PixelPaneBounds(0, 540, 2_000, 1_200), result.primary)
        assertEquals(PixelPaneBounds(0, 0, 2_000, 500), result.secondary)
        assertEquals(PixelPaneBounds(0, 500, 2_000, 540), result.separator)
    }

    @Test
    fun zeroWidthFoldStillDividesTheWindow() {
        val result = calculateSafeRegions(
            window = window,
            separatingFeature = PixelPaneBounds(1_000, 0, 1_000, 1_200),
            splitAxis = SafePaneAxis.VERTICAL,
        )

        assertEquals(PixelPaneBounds(1_000, 0, 2_000, 1_200), result.primary)
        assertEquals(PixelPaneBounds(0, 0, 1_000, 1_200), result.secondary)
    }

    @Test
    fun offCenterFoldChoosesTheLargerRegion() {
        val result = calculateSafeRegions(
            window = window,
            separatingFeature = PixelPaneBounds(1_600, 0, 1_620, 1_200),
            splitAxis = SafePaneAxis.VERTICAL,
        )

        assertEquals(PixelPaneBounds(0, 0, 1_600, 1_200), result.primary)
        assertEquals(PixelPaneBounds(1_620, 0, 2_000, 1_200), result.secondary)
    }

    @Test
    fun featureAtWindowEdgeLeavesTheRemainingSafePane() {
        val result = calculateSafeRegions(
            window = window,
            separatingFeature = PixelPaneBounds(-20, 0, 30, 1_200),
            splitAxis = SafePaneAxis.VERTICAL,
        )

        assertEquals(PixelPaneBounds(30, 0, 2_000, 1_200), result.primary)
        assertNull(result.secondary)
        assertEquals(PixelPaneBounds(0, 0, 30, 1_200), result.separator)
    }

    @Test
    fun featureThatDoesNotCrossWindowIsIgnored() {
        val result = calculateSafeRegions(
            window = window,
            separatingFeature = PixelPaneBounds(900, 1_300, 1_100, 1_400),
            splitAxis = SafePaneAxis.VERTICAL,
        )

        assertEquals(window, result.primary)
        assertNull(result.secondary)
        assertNull(result.separator)
    }
}
