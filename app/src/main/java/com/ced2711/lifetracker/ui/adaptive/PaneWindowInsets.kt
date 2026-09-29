package com.ced2711.lifetracker.ui.adaptive

import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
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

/** A top cutout narrower than this share of the window is a camera island, not a full notch. */
private const val CUTOUT_ISLAND_MAX_WIDTH_FRACTION = 0.4f

internal fun isTopCutoutIsland(cutoutWidthPx: Int, windowWidthPx: Int): Boolean =
    cutoutWidthPx > 0 && cutoutWidthPx <= windowWidthPx * CUTOUT_ISLAND_MAX_WIDTH_FRACTION

/** The top display-cutout rectangle in window pixels, when it is a small camera island. */
@Composable
internal fun topCutoutIsland(): Rect? {
    // Reading the inset subscribes this composable to cutout changes such as rotation.
    val topCutout = WindowInsets.displayCutout.getTop(LocalDensity.current)
    val view = LocalView.current
    if (topCutout == 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val rect = view.rootWindowInsets?.displayCutout?.boundingRectTop ?: return null
    return rect.takeIf { !it.isEmpty && isTopCutoutIsland(it.width(), view.rootView.width) }
}

/**
 * Like [appSafeDrawingInsets], but a small top camera island does not reserve a full-width strip:
 * with the status bar hidden, the header paints behind it and keeps only its own content clear.
 */
@Composable
private fun appSafeDrawingInsetsAllowingTopIsland(): WindowInsets {
    if (topCutoutIsland() == null) return appSafeDrawingInsets()
    return WindowInsets.systemBars
        .union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        .union(appImeInsets())
}

@Composable
internal fun paneSafeDrawingInsets(
    pane: SafePaneBounds,
    windowWidth: Dp,
    windowHeight: Dp,
    allowTopCutoutIsland: Boolean = false,
): WindowInsets {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // safeDrawing includes the visible system bars, display cutout and software keyboard.
    val windowInsets = if (allowTopCutoutIsland) appSafeDrawingInsetsAllowingTopIsland() else appSafeDrawingInsets()
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
    allowTopCutoutIsland: Boolean = false,
): Modifier = windowInsetsPadding(paneSafeDrawingInsets(pane, windowWidth, windowHeight, allowTopCutoutIsland))
    // Children must not apply the original window-edge insets again inside an offset pane.
    .consumeWindowInsets(appSafeDrawingInsets())
