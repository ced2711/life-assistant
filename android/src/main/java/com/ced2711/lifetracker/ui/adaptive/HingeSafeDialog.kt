package com.ced2711.lifetracker.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

enum class SafePanePreference {
    PRIMARY,
    SECONDARY,
}

object HingeSafeDialogDefaults {
    /**
     * Full-window bounds keep [LocalSafePaneLayout] coordinates aligned with the dialog window.
     * System bars and the IME are handled inside the chosen safe pane.
     */
    val properties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    )
}

/**
 * A full-window dialog whose content is measured entirely inside one unobstructed foldable pane.
 *
 * This is a drop-in replacement for a custom Compose [Dialog]. The content receives safe-drawing
 * and IME padding, so adopters should not add the same padding to their outermost surface again.
 */
@Composable
fun HingeSafeDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = HingeSafeDialogDefaults.properties,
    panePreference: SafePanePreference = SafePanePreference.PRIMARY,
    content: @Composable BoxScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        HingeSafeDialogWindow(
            modifier = modifier,
            panePreference = panePreference,
            content = content,
        )
    }
}

/**
 * Hinge-safe content host for callers that already own a full-window [Dialog].
 *
 * The surrounding dialog should use [HingeSafeDialogDefaults.properties] so its coordinates match
 * the activity window. If no pane information is available, this safely fills the current window.
 */
@Composable
fun HingeSafeDialogWindow(
    modifier: Modifier = Modifier,
    panePreference: SafePanePreference = SafePanePreference.PRIMARY,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { dialogWindow?.let { hideAppStatusBar(it) } }
    val density = LocalDensity.current
    val safePaneLayout = LocalSafePaneLayout.current?.let { layout ->
        keyboardAwarePaneLayout(layout, with(density) { appImeInsets().getBottom(density).toDp() })
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullWindow = SafePaneBounds(0.dp, 0.dp, maxWidth, maxHeight)
        val requestedPane = when (panePreference) {
            SafePanePreference.PRIMARY -> safePaneLayout?.primaryPane
            SafePanePreference.SECONDARY ->
                safePaneLayout?.secondaryPane ?: safePaneLayout?.primaryPane
        }
        val pane = requestedPane
            ?.clampTo(width = maxWidth, height = maxHeight)
            ?.takeUnless(SafePaneBounds::isEmpty)
            ?: fullWindow

        Box(
            modifier = Modifier
                .offset(x = pane.left, y = pane.top)
                .width(pane.width)
                .height(pane.height)
                .then(modifier)
                .paneSafeDrawingPadding(pane, maxWidth, maxHeight),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/**
 * Conventional sizing for a card/surface placed directly inside [HingeSafeDialog].
 */
fun Modifier.hingeSafeDialogSurface(
    maxWidth: Dp = 720.dp,
    maxHeight: Dp? = null,
    widthFraction: Float = 0.96f,
    heightFraction: Float = 0.96f,
): Modifier {
    require(widthFraction in 0f..1f) { "widthFraction must be between 0 and 1" }
    require(heightFraction in 0f..1f) { "heightFraction must be between 0 and 1" }
    val sized = this
        .fillMaxWidth(widthFraction)
        .fillMaxHeight(heightFraction)
        .widthIn(max = maxWidth)
    return if (maxHeight == null) sized else sized.heightIn(max = maxHeight)
}

private fun SafePaneBounds.clampTo(width: Dp, height: Dp): SafePaneBounds {
    val clampedLeft = left.coerceIn(0.dp, width)
    val clampedTop = top.coerceIn(0.dp, height)
    return SafePaneBounds(
        left = clampedLeft,
        top = clampedTop,
        right = right.coerceIn(clampedLeft, width),
        bottom = bottom.coerceIn(clampedTop, height),
    )
}
