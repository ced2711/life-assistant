package com.ced2711.lifetracker.desktop

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
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    val windowIcon = remember { LifeTrackerWindowIcon() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Life Tracker",
        icon = windowIcon,
        state = WindowState(size = DpSize(1280.dp, 800.dp)),
    ) {
        LifeTrackerDesktopApp()
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
