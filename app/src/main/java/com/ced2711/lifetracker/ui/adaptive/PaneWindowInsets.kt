package com.ced2711.lifetracker.ui.adaptive

import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Android 8–10 fullscreen windows do not receive reliable IME resize/insets. */
@Composable
internal fun appImeInsets(): WindowInsets {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return WindowInsets.ime
    val view = LocalView.current
    val minimumKeyboardHeight = with(LocalDensity.current) { 100.dp.roundToPx() }
    var legacyKeyboardBottom by remember(view) { mutableIntStateOf(0) }
    DisposableEffect(view, minimumKeyboardHeight) {
        val root = view.rootView
        val visible = Rect()
        val location = IntArray(2)
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            root.getWindowVisibleDisplayFrame(visible)
            root.getLocationOnScreen(location)
            val obscured = (location[1] + root.height - visible.bottom).coerceIn(0, root.height)
            // Exclude a persistent navigation bar; only the keyboard-sized obstruction applies.
            legacyKeyboardBottom = if (obscured > minimumKeyboardHeight) obscured else 0
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose {
            if (root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }
    }
    return WindowInsets.ime.union(WindowInsets(bottom = legacyKeyboardBottom))
}

@Composable
private fun appSafeDrawingInsets(): WindowInsets = WindowInsets.safeDrawing.union(appImeInsets())

@Composable
internal fun paneSafeDrawingInsets(
    pane: SafePaneBounds,
    windowWidth: Dp,
    windowHeight: Dp,
): WindowInsets {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // safeDrawing includes the visible system bars, display cutout and software keyboard.
    val windowInsets = appSafeDrawingInsets()
    val projected = with(density) {
        projectWindowInsetsIntoPane(
            pane = PixelPaneBounds(
                pane.left.roundToPx(), pane.top.roundToPx(),
                pane.right.roundToPx(), pane.bottom.roundToPx(),
            ),
            windowWidth = windowWidth.roundToPx(),
            windowHeight = windowHeight.roundToPx(),
            insets = PaneEdgeInsets(
                windowInsets.getLeft(density, direction), windowInsets.getTop(density),
                windowInsets.getRight(density, direction), windowInsets.getBottom(density),
            ),
        )
    }
    return WindowInsets(projected.left, projected.top, projected.right, projected.bottom)
}

@Composable
internal fun Modifier.paneSafeDrawingPadding(
    pane: SafePaneBounds,
    windowWidth: Dp,
    windowHeight: Dp,
): Modifier = windowInsetsPadding(paneSafeDrawingInsets(pane, windowWidth, windowHeight))
    // Children must not apply the original window-edge insets again inside an offset pane.
    .consumeWindowInsets(appSafeDrawingInsets())
