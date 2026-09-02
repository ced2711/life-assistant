package com.ced2711.lifetracker.ui.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The direction in which a separating fold divides the app window. */
enum class SafePaneAxis {
    VERTICAL,
    HORIZONTAL,
}

/** Bounds of an unobstructed part of the app window, in root-composition coordinates. */
@Immutable
data class SafePaneBounds(
    val left: Dp,
    val top: Dp,
    val right: Dp,
    val bottom: Dp,
) {
    val width: Dp get() = (right - left).coerceAtLeast(0.dp)
    val height: Dp get() = (bottom - top).coerceAtLeast(0.dp)
    val isEmpty: Boolean get() = width <= 0.dp || height <= 0.dp
}

/**
 * Window-relative safe regions calculated from the active separating [androidx.window.layout.FoldingFeature].
 *
 * [primaryPane] is the larger region and is the default home for feature content and dialogs.
 * [secondaryPane] remains available so adaptive screens can use both sides rather than treating a
 * foldable as a smaller conventional phone. On equally sized panes, the right or lower pane is
 * primary; this leaves the conventional left/top region available for navigation chrome.
 */
@Immutable
data class SafePaneLayout(
    val windowWidth: Dp,
    val windowHeight: Dp,
    val primaryPane: SafePaneBounds,
    val secondaryPane: SafePaneBounds? = null,
    val separatingFeatureBounds: SafePaneBounds? = null,
    val splitAxis: SafePaneAxis? = null,
) {
    val hasSeparatingFeature: Boolean
        get() = secondaryPane != null && separatingFeatureBounds != null && splitAxis != null

    companion object {
        fun singlePane(width: Dp, height: Dp): SafePaneLayout = SafePaneLayout(
            windowWidth = width,
            windowHeight = height,
            primaryPane = SafePaneBounds(
                left = 0.dp,
                top = 0.dp,
                right = width,
                bottom = height,
            ),
        )
    }
}

/**
 * Uses the pane that actually hosts feature content, even if an ancestor accidentally reports the
 * complete unfolded window. This prevents phone-width panes from selecting two-column screens.
 */
internal fun usesWideFeatureLayout(
    measuredWidth: Dp,
    safePaneLayout: SafePaneLayout?,
    wideThreshold: Dp = 840.dp,
): Boolean {
    val featurePaneWidth = minOf(measuredWidth, safePaneLayout?.primaryPane?.width ?: measuredWidth)
    return featurePaneWidth >= wideThreshold
}

internal fun usablePaneExtent(extent: Dp, safeDrawingInsets: Dp): Dp =
    (extent - safeDrawingInsets).coerceAtLeast(0.dp)

/**
 * The safe-pane geometry for the current app window.
 *
 * Dialogs inherit this local through their composition. A null value means the caller is outside
 * [AdaptiveTaskLedgerScaffold] and should use its own complete bounds.
 */
val LocalSafePaneLayout = staticCompositionLocalOf<SafePaneLayout?> { null }

/** Integer-only rectangle used by the pure safe-region calculator and its local unit tests. */
internal data class PixelPaneBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height.toLong()
    val isEmpty: Boolean get() = width == 0 || height == 0
}

internal data class SafeRegionCalculation(
    val window: PixelPaneBounds,
    val primary: PixelPaneBounds,
    val secondary: PixelPaneBounds? = null,
    val separator: PixelPaneBounds? = null,
    val splitAxis: SafePaneAxis? = null,
)

/**
 * Splits [window] around a separating display feature without depending on Android runtime types.
 * Invalid or non-intersecting feature data safely falls back to a single region.
 */
internal fun calculateSafeRegions(
    window: PixelPaneBounds,
    separatingFeature: PixelPaneBounds?,
    splitAxis: SafePaneAxis?,
): SafeRegionCalculation {
    if (window.isEmpty || separatingFeature == null || splitAxis == null) {
        return SafeRegionCalculation(window = window, primary = window)
    }

    val crossesWindow = when (splitAxis) {
        SafePaneAxis.VERTICAL ->
            separatingFeature.bottom > window.top && separatingFeature.top < window.bottom

        SafePaneAxis.HORIZONTAL ->
            separatingFeature.right > window.left && separatingFeature.left < window.right
    }
    if (!crossesWindow) return SafeRegionCalculation(window = window, primary = window)

    val separator = PixelPaneBounds(
        left = separatingFeature.left.coerceIn(window.left, window.right),
        top = separatingFeature.top.coerceIn(window.top, window.bottom),
        right = separatingFeature.right.coerceIn(window.left, window.right),
        bottom = separatingFeature.bottom.coerceIn(window.top, window.bottom),
    ).normalized()

    val candidates = when (splitAxis) {
        SafePaneAxis.VERTICAL -> listOf(
            PixelPaneBounds(window.left, window.top, separator.left, window.bottom),
            PixelPaneBounds(separator.right, window.top, window.right, window.bottom),
        )

        SafePaneAxis.HORIZONTAL -> listOf(
            PixelPaneBounds(window.left, window.top, window.right, separator.top),
            PixelPaneBounds(window.left, separator.bottom, window.right, window.bottom),
        )
    }.filterNot(PixelPaneBounds::isEmpty)

    // A malformed feature that consumes the complete window provides no meaningful safe split.
    if (candidates.isEmpty()) return SafeRegionCalculation(window = window, primary = window)
    if (candidates.size == 1) {
        return SafeRegionCalculation(
            window = window,
            primary = candidates.single(),
            separator = separator,
            splitAxis = splitAxis,
        )
    }

    val first = candidates[0]
    val second = candidates[1]
    val primary = if (second.area >= first.area) second else first
    val secondary = if (primary === second) first else second
    return SafeRegionCalculation(
        window = window,
        primary = primary,
        secondary = secondary,
        separator = separator,
        splitAxis = splitAxis,
    )
}

private fun PixelPaneBounds.normalized(): PixelPaneBounds = PixelPaneBounds(
    left = minOf(left, right),
    top = minOf(top, bottom),
    right = maxOf(left, right),
    bottom = maxOf(top, bottom),
)
