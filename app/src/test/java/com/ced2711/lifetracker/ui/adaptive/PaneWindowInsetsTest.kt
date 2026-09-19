package com.ced2711.lifetracker.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class PaneWindowInsetsTest {
    private val portraitWindow = PixelPaneBounds(
        left = 0,
        top = 0,
        right = 1_080,
        bottom = 2_520,
    )

    @Test
    fun portraitFoldProjectsCutoutAndImeOnlyIntoThePaneEdgesTheyOverlap() {
        val topPane = PixelPaneBounds(0, 0, 1_080, 1_260)
        val bottomPane = PixelPaneBounds(0, 1_260, 1_080, 2_520)
        val portraitInsets = PaneEdgeInsets(
            left = 0,
            top = 100,
            right = 0,
            bottom = 1_300,
        )

        assertEquals(
            PaneEdgeInsets(left = 0, top = 100, right = 0, bottom = 40),
            projectWindowInsetsIntoPane(topPane, 1_080, 2_520, portraitInsets),
        )
        assertEquals(
            PaneEdgeInsets(left = 0, top = 0, right = 0, bottom = 1_260),
            projectWindowInsetsIntoPane(bottomPane, 1_080, 2_520, portraitInsets),
        )

        // Navigation-bar-only insets do not turn into an IME-sized inset.
        assertEquals(
            PaneEdgeInsets(left = 0, top = 0, right = 0, bottom = 60),
            projectWindowInsetsIntoPane(
                bottomPane,
                1_080,
                2_520,
                PaneEdgeInsets(left = 0, top = 0, right = 0, bottom = 60),
            ),
        )
    }

    @Test
    fun landscapeCutoutAndNavigationInsetsAreProjectedToOppositeVerticalPanes() {
        val windowWidth = 2_520
        val windowHeight = 1_080
        val leftPane = PixelPaneBounds(0, 0, 1_260, 1_080)
        val rightPane = PixelPaneBounds(1_260, 0, 2_520, 1_080)
        val landscapeInsets = PaneEdgeInsets(
            left = 100,
            top = 0,
            right = 60,
            bottom = 0,
        )

        assertEquals(
            PaneEdgeInsets(left = 100, top = 0, right = 0, bottom = 0),
            projectWindowInsetsIntoPane(leftPane, windowWidth, windowHeight, landscapeInsets),
        )
        assertEquals(
            PaneEdgeInsets(left = 0, top = 0, right = 60, bottom = 0),
            projectWindowInsetsIntoPane(rightPane, windowWidth, windowHeight, landscapeInsets),
        )
    }

    @Test
    fun panesAwayFromWindowEdgesDoNotReceiveUnrelatedWindowInsets() {
        val inset = PaneEdgeInsets(left = 80, top = 100, right = 70, bottom = 60)
        val interiorPane = PixelPaneBounds(100, 100, 900, 2_420)

        assertEquals(
            PaneEdgeInsets(left = 0, top = 0, right = 0, bottom = 0),
            projectWindowInsetsIntoPane(
                interiorPane,
                portraitWindow.right,
                portraitWindow.bottom,
                inset,
            ),
        )
    }

    @Test
    fun missingAndNegativeInsetsClampToZeroAndOversizedInsetsToPaneSize() {
        val pane = PixelPaneBounds(100, 200, 900, 1_000)

        assertEquals(
            PaneEdgeInsets(left = 0, top = 0, right = 0, bottom = 0),
            projectWindowInsetsIntoPane(
                pane,
                windowWidth = 1_000,
                windowHeight = 1_200,
                insets = PaneEdgeInsets(left = -10, top = -20, right = -30, bottom = -40),
            ),
        )
        assertEquals(
            PaneEdgeInsets(left = 800, top = 800, right = 800, bottom = 800),
            projectWindowInsetsIntoPane(
                pane,
                windowWidth = 1_000,
                windowHeight = 1_200,
                insets = PaneEdgeInsets(left = 2_000, top = 2_000, right = 2_000, bottom = 2_000),
            ),
        )
    }
}
