package com.ced2711.lifetracker.ui.adaptive

import android.app.DatePickerDialog
import android.view.Gravity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptivePlatformDialogPostureTest {
    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var picker: DatePickerDialog

    @Test
    fun openDatePickerTracksLivePaneChangesWithoutLosingSelection() {
        lateinit var layoutState: MutableState<SafePaneLayout>
        var density = 1f

        composeRule.setContent {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                layoutState = remember(maxWidth, maxHeight) {
                    mutableStateOf(SafePaneLayout.singlePane(maxWidth, maxHeight))
                }
                density = LocalDensity.current.density
                val context = LocalContext.current
                CompositionLocalProvider(LocalSafePaneLayout provides layoutState.value) {
                    val launchDialog = rememberHingeSafePlatformDialogLauncher()
                    Button(
                        onClick = {
                            picker = DatePickerDialog(context, null, 2030, 0, 15)
                            launchDialog(picker)
                        },
                    ) {
                        Text("Open test date picker")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Open test date picker").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            this::picker.isInitialized &&
                picker.isShowing &&
                (picker.window?.decorView?.width ?: 0) > 0
        }

        val shownPicker = picker
        val baseline = composeRule.runOnIdle {
            picker.datePicker.updateDate(2032, 4, 21)
            picker.window!!.attributes.let {
                WindowPlacement(it.gravity, it.x, it.y, it.width, it.height)
            }
        }

        val verticalLayout = splitVertically(layoutState.value)
        composeRule.runOnIdle { layoutState.value = verticalLayout }
        waitForPlacementInside(verticalLayout.primaryPane, density)

        val horizontalLayout = splitHorizontally(layoutState.value)
        composeRule.runOnIdle { layoutState.value = horizontalLayout }
        waitForPlacementInside(horizontalLayout.primaryPane, density)

        composeRule.runOnIdle {
            assertSame(shownPicker, picker)
            assertTrue(picker.isShowing)
            assertEquals(2032, picker.datePicker.year)
            assertEquals(4, picker.datePicker.month)
            assertEquals(21, picker.datePicker.dayOfMonth)
            layoutState.value = SafePaneLayout.singlePane(
                horizontalLayout.windowWidth,
                horizontalLayout.windowHeight,
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            picker.window?.attributes?.let {
                it.gravity == baseline.gravity &&
                    it.x == baseline.x &&
                    it.y == baseline.y &&
                    it.width == baseline.width &&
                    it.height == baseline.height
            } == true
        }
    }

    private fun waitForPlacementInside(pane: SafePaneBounds, density: Float) {
        val paneLeft = pane.left.toPixels(density)
        val paneTop = pane.top.toPixels(density)
        val paneRight = pane.right.toPixels(density)
        val paneBottom = pane.bottom.toPixels(density)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            picker.window?.attributes?.let { attributes ->
                attributes.gravity == (Gravity.TOP or Gravity.LEFT) &&
                    attributes.x >= paneLeft &&
                    attributes.y >= paneTop &&
                    attributes.x + attributes.width <= paneRight &&
                    attributes.y + attributes.height <= paneBottom
            } == true
        }
    }

    private fun splitVertically(layout: SafePaneLayout): SafePaneLayout {
        val middle = layout.windowWidth / 2f
        return SafePaneLayout(
            windowWidth = layout.windowWidth,
            windowHeight = layout.windowHeight,
            primaryPane = SafePaneBounds(middle + 6.dp, 0.dp, layout.windowWidth, layout.windowHeight),
            secondaryPane = SafePaneBounds(0.dp, 0.dp, middle - 6.dp, layout.windowHeight),
            separatingFeatureBounds = SafePaneBounds(
                middle - 6.dp,
                0.dp,
                middle + 6.dp,
                layout.windowHeight,
            ),
            splitAxis = SafePaneAxis.VERTICAL,
        )
    }

    private fun splitHorizontally(layout: SafePaneLayout): SafePaneLayout {
        val middle = layout.windowHeight / 2f
        return SafePaneLayout(
            windowWidth = layout.windowWidth,
            windowHeight = layout.windowHeight,
            primaryPane = SafePaneBounds(0.dp, middle + 6.dp, layout.windowWidth, layout.windowHeight),
            secondaryPane = SafePaneBounds(0.dp, 0.dp, layout.windowWidth, middle - 6.dp),
            separatingFeatureBounds = SafePaneBounds(
                0.dp,
                middle - 6.dp,
                layout.windowWidth,
                middle + 6.dp,
            ),
            splitAxis = SafePaneAxis.HORIZONTAL,
        )
    }

    private data class WindowPlacement(
        val gravity: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )
}

private fun Dp.toPixels(density: Float): Int = (value * density).roundToInt()
