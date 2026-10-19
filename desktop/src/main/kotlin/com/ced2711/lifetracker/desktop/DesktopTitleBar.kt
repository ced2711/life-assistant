package com.ced2711.lifetracker.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** The title bar's look, following the app's theme: colours as 0xRRGGBB. */
data class TitleBarColors(val dark: Boolean, val caption: Int, val text: Int)

/** Receives the colours the window's title bar should have; null where nothing draws one (tests). */
val LocalDesktopTitleBar = staticCompositionLocalOf<((TitleBarColors) -> Unit)?> { null }

/** Passes the current theme's background and text colours on to the window's title bar. */
@Composable
internal fun TitleBarFollowsTheme() {
    val titleBar = LocalDesktopTitleBar.current ?: return
    val background = MaterialTheme.colorScheme.background
    val text = MaterialTheme.colorScheme.onBackground
    val colors = TitleBarColors(
        dark = background.luminance() < 0.5f,
        caption = background.toArgb() and 0xFFFFFF,
        text = text.toArgb() and 0xFFFFFF,
    )
    SideEffect { titleBar(colors) }
}
