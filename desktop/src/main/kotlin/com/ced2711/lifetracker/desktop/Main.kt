package com.ced2711.lifetracker.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.ced2711.lifetracker.domain.model.AppIdentity

fun main() = application {
    val windowIcon = remember { LifeTrackerWindowIcon() }
    val configStore = remember { DesktopConfigStore() }
    val shortcuts = remember { DesktopShortcuts() }
    // Reopen where the window was left, at the size it had.
    val windowState = remember {
        val saved = runCatching { configStore.windowBounds() }.getOrNull()
        WindowState(
            placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = if (saved?.x != null && saved.y != null) WindowPosition(saved.x.dp, saved.y.dp) else WindowPosition.PlatformDefault,
            size = DpSize((saved?.width ?: 1280).dp, (saved?.height ?: 800).dp),
        )
    }
    Window(
        onCloseRequest = {
            runCatching {
                val position = windowState.position
                configStore.setWindowBounds(
                    DesktopWindowBounds(
                        width = windowState.size.width.value.toInt(),
                        height = windowState.size.height.value.toInt(),
                        x = (position as? WindowPosition.Absolute)?.x?.value?.toInt(),
                        y = (position as? WindowPosition.Absolute)?.y?.value?.toInt(),
                        maximized = windowState.placement == WindowPlacement.Maximized,
                    ),
                )
            }
            exitApplication()
        },
        title = AppIdentity.NAME,
        icon = windowIcon,
        state = windowState,
        onPreviewKeyEvent = shortcuts::handle,
    ) {
        window.minimumSize = java.awt.Dimension(640, 480)
        CompositionLocalProvider(LocalDesktopShortcuts provides shortcuts) {
            LifeTrackerDesktopApp()
        }
    }
}

private class LifeTrackerWindowIcon : Painter() {
    override val intrinsicSize: Size = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val scale = size.minDimension / 256f
        drawRoundRect(
            color = Color(0xFF181A1A),
            cornerRadius = CornerRadius(58f * scale, 58f * scale),
        )
        drawLine(
            color = Color.White,
            start = Offset(64f * scale, 132f * scale),
            end = Offset(111f * scale, 177f * scale),
            strokeWidth = 30f * scale,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(111f * scale, 177f * scale),
            end = Offset(193f * scale, 82f * scale),
            strokeWidth = 30f * scale,
            cap = StrokeCap.Round,
        )
    }
}
