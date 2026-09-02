package com.ced2711.lifetracker.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptivePlatformDialogTest {
    @Test
    fun missingPaneContextKeepsPlatformDefaultPlacement() {
        assertNull(
            calculatePlatformDialogWindowBounds(
                safePaneLayout = null,
                panePreference = SafePanePreference.PRIMARY,
                density = 1f,
                dialogWidthPx = 320,
                dialogHeightPx = 240,
            ),
        )
    }

    @Test
    fun ordinaryWindowKeepsPlatformDefaultPlacement() {
        val layout = SafePaneLayout.singlePane(width = 600.dp, height = 400.dp)

        assertNull(
            calculatePlatformDialogWindowBounds(
                safePaneLayout = layout,
                panePreference = SafePanePreference.PRIMARY,
                density = 1f,
                dialogWidthPx = 320,
                dialogHeightPx = 240,
            ),
        )
    }

    @Test
    fun verticalFoldCentersDialogInsidePrimaryPane() {
        val layout = splitLayout(
            windowWidth = 1_000.dp,
            windowHeight = 700.dp,
            primaryPane = SafePaneBounds(520.dp, 0.dp, 1_000.dp, 700.dp),
            secondaryPane = SafePaneBounds(0.dp, 0.dp, 500.dp, 700.dp),
            separator = SafePaneBounds(500.dp, 0.dp, 520.dp, 700.dp),
            axis = SafePaneAxis.VERTICAL,
        )

        assertEquals(
            PlatformDialogWindowBounds(x = 600, y = 230, width = 320, height = 240),
            calculatePlatformDialogWindowBounds(
                safePaneLayout = layout,
                panePreference = SafePanePreference.PRIMARY,
                density = 1f,
                dialogWidthPx = 320,
                dialogHeightPx = 240,
            ),
        )
    }

    @Test
    fun horizontalFoldCentersDialogInsidePrimaryPane() {
        val layout = splitLayout(
            windowWidth = 800.dp,
            windowHeight = 1_000.dp,
            primaryPane = SafePaneBounds(0.dp, 520.dp, 800.dp, 1_000.dp),
            secondaryPane = SafePaneBounds(0.dp, 0.dp, 800.dp, 500.dp),
            separator = SafePaneBounds(0.dp, 500.dp, 800.dp, 520.dp),
            axis = SafePaneAxis.HORIZONTAL,
        )

        assertEquals(
            PlatformDialogWindowBounds(x = 200, y = 610, width = 400, height = 300),
            calculatePlatformDialogWindowBounds(
                safePaneLayout = layout,
                panePreference = SafePanePreference.PRIMARY,
                density = 1f,
                dialogWidthPx = 400,
                dialogHeightPx = 300,
            ),
        )
    }

    @Test
    fun paneAndDialogAreClampedToWindow() {
        val layout = splitLayout(
            windowWidth = 600.dp,
            windowHeight = 400.dp,
            primaryPane = SafePaneBounds((-20).dp, (-10).dp, 700.dp, 500.dp),
            secondaryPane = SafePaneBounds(0.dp, 0.dp, 1.dp, 1.dp),
            separator = SafePaneBounds(300.dp, 0.dp, 300.dp, 400.dp),
            axis = SafePaneAxis.VERTICAL,
        )

        assertEquals(
            PlatformDialogWindowBounds(x = 0, y = 0, width = 600, height = 400),
            calculatePlatformDialogWindowBounds(
                safePaneLayout = layout,
                panePreference = SafePanePreference.PRIMARY,
                density = 1f,
                dialogWidthPx = 800,
                dialogHeightPx = 600,
            ),
        )
    }

    @Test
    fun emptyRequestedPaneKeepsPlatformDefaultPlacement() {
        val layout = splitLayout(
            windowWidth = 600.dp,
            windowHeight = 400.dp,
            primaryPane = SafePaneBounds(300.dp, 0.dp, 300.dp, 400.dp),
            secondaryPane = SafePaneBounds(0.dp, 0.dp, 280.dp, 400.dp),
            separator = SafePaneBounds(280.dp, 0.dp, 300.dp, 400.dp),
            axis = SafePaneAxis.VERTICAL,
        )

        assertNull(
            calculatePlatformDialogWindowBounds(
                safePaneLayout = layout,
                panePreference = SafePanePreference.PRIMARY,
                density = 1f,
                dialogWidthPx = 300,
                dialogHeightPx = 200,
            ),
        )
    }

    @Test
    fun densityConversionPreservesPaneCoordinates() {
        val layout = splitLayout(
            windowWidth = 600.dp,
            windowHeight = 400.dp,
            primaryPane = SafePaneBounds(310.dp, 0.dp, 600.dp, 400.dp),
            secondaryPane = SafePaneBounds(0.dp, 0.dp, 300.dp, 400.dp),
            separator = SafePaneBounds(300.dp, 0.dp, 310.dp, 400.dp),
            axis = SafePaneAxis.VERTICAL,
        )

        assertEquals(
            PlatformDialogWindowBounds(x = 810, y = 320, width = 200, height = 160),
            calculatePlatformDialogWindowBounds(
                safePaneLayout = layout,
                panePreference = SafePanePreference.PRIMARY,
                density = 2f,
                dialogWidthPx = 200,
                dialogHeightPx = 160,
            ),
        )
    }

    @Test
    fun secondaryPreferenceUsesSecondaryPane() {
        val layout = splitLayout(
            windowWidth = 1_000.dp,
            windowHeight = 700.dp,
            primaryPane = SafePaneBounds(520.dp, 0.dp, 1_000.dp, 700.dp),
            secondaryPane = SafePaneBounds(0.dp, 0.dp, 500.dp, 700.dp),
            separator = SafePaneBounds(500.dp, 0.dp, 520.dp, 700.dp),
            axis = SafePaneAxis.VERTICAL,
        )

        assertEquals(
            PlatformDialogWindowBounds(x = 90, y = 230, width = 320, height = 240),
            calculatePlatformDialogWindowBounds(
                safePaneLayout = layout,
                panePreference = SafePanePreference.SECONDARY,
                density = 1f,
                dialogWidthPx = 320,
                dialogHeightPx = 240,
            ),
        )
    }

    @Test
    fun livePostureChangesRecalculateFromTheDialogsPreferredSize() {
        val verticalLayout = splitLayout(
            windowWidth = 1_000.dp,
            windowHeight = 800.dp,
            primaryPane = SafePaneBounds(520.dp, 0.dp, 1_000.dp, 800.dp),
            secondaryPane = SafePaneBounds(0.dp, 0.dp, 500.dp, 800.dp),
            separator = SafePaneBounds(500.dp, 0.dp, 520.dp, 800.dp),
            axis = SafePaneAxis.VERTICAL,
        )
        val horizontalLayout = splitLayout(
            windowWidth = 1_000.dp,
            windowHeight = 800.dp,
            primaryPane = SafePaneBounds(0.dp, 420.dp, 1_000.dp, 800.dp),
            secondaryPane = SafePaneBounds(0.dp, 0.dp, 1_000.dp, 400.dp),
            separator = SafePaneBounds(0.dp, 400.dp, 1_000.dp, 420.dp),
            axis = SafePaneAxis.HORIZONTAL,
        )

        assertEquals(
            PlatformDialogWindowBounds(x = 600, y = 250, width = 320, height = 300),
            calculatePlatformDialogWindowBounds(
                safePaneLayout = verticalLayout,
                panePreference = SafePanePreference.PRIMARY,
                density = 1f,
                dialogWidthPx = 320,
                dialogHeightPx = 300,
            ),
        )
        assertEquals(
            PlatformDialogWindowBounds(x = 340, y = 460, width = 320, height = 300),
            calculatePlatformDialogWindowBounds(
                safePaneLayout = horizontalLayout,
                panePreference = SafePanePreference.PRIMARY,
                density = 1f,
                dialogWidthPx = 320,
                dialogHeightPx = 300,
            ),
        )
        assertNull(
            calculatePlatformDialogWindowBounds(
                safePaneLayout = SafePaneLayout.singlePane(1_000.dp, 800.dp),
                panePreference = SafePanePreference.PRIMARY,
                density = 1f,
                dialogWidthPx = 320,
                dialogHeightPx = 300,
            ),
        )
    }

    private fun splitLayout(
        windowWidth: androidx.compose.ui.unit.Dp,
        windowHeight: androidx.compose.ui.unit.Dp,
        primaryPane: SafePaneBounds,
        secondaryPane: SafePaneBounds,
        separator: SafePaneBounds,
        axis: SafePaneAxis,
    ) = SafePaneLayout(
        windowWidth = windowWidth,
        windowHeight = windowHeight,
        primaryPane = primaryPane,
        secondaryPane = secondaryPane,
        separatingFeatureBounds = separator,
        splitAxis = axis,
    )
}
