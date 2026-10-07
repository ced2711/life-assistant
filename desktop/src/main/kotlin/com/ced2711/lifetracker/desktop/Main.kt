package com.ced2711.lifetracker.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        // Packaged-build check without a window; see DesktopSelfTest.
        "--self-test" -> kotlin.system.exitProcess(if (DesktopSelfTest.run(java.io.File(args.getOrElse(1) { "self-test.txt" }))) 0 else 1)
        // AI assistants start this as their MCP server; see DesktopMcpBridge.
        "--mcp" -> {
            DesktopMcpBridge.main()
            kotlin.system.exitProcess(0)
        }
    }
    val background = DesktopAutostart.BACKGROUND_ARGUMENT in args
    // Already running (perhaps in the tray): bring that window forward instead of a second copy.
    if (DesktopAgentServer.request(DesktopPlatform.appDirectory(), "/status") != null) {
        if (!background) DesktopAgentServer.request(DesktopPlatform.appDirectory(), "/show", "{}")
        kotlin.system.exitProcess(0)
    }
    // Only this copy runs now: the data folder of older versions can take the app's name.
    DesktopPlatform.moveLegacyAppDirectory()
    runCatching { DesktopAutostart.system.refresh() }
    Thread({ runCatching { DesktopAgentSetup.refreshRegistrations() } }, "agent-registrations").apply { isDaemon = true }.start()
    runApp(startHidden = background)
}

private fun runApp(startHidden: Boolean) = application {
    val windowIcon = remember { LifeTrackerWindowIcon() }
    val configStore = remember { DesktopConfigStore() }
    val shortcuts = remember { DesktopShortcuts() }
    // Started with the computer or by an AI assistant: wait in the tray.
    var windowVisible by remember { mutableStateOf(!startHidden) }
    // Counts requests to bring the window forward; the window reacts to each one.
    var showRequests by remember { mutableIntStateOf(0) }
    val openWindow: () -> Unit = {
        windowVisible = true
        showRequests++
    }
    val language = remember { configStore.read().uiLanguage }
    val background = remember { DesktopBackgroundSettings(configStore) }
    // The tray icon opens the window with one click, shows reminder notifications and, when
    // chosen, keeps the app running. Without a tray the window has to show.
    val tray = remember {
        val icon = if (DesktopTrayIcon.isSupported) {
            val pixels = DesktopTrayIcon.iconPixels().toFloat()
            DesktopTrayIcon(
                image = windowIcon.toAwtImage(Density(1f), LayoutDirection.Ltr, Size(pixels, pixels)),
                tooltip = desktopText(AppIdentity.NAME, language),
                openLabel = desktopText("Open", language),
                quitLabel = desktopText("Quit", language),
                onOpen = openWindow,
                onQuit = ::exitApplication,
                settingsLabel = desktopText("Settings", language),
                toggles = listOfNotNull(
                    TrayToggle(
                        desktopText(DesktopPlatform.text(windows = "Start with Windows", linux = "Start when you sign in"), language),
                        isOn = { background.startsWithComputer.value },
                        onChange = background::setStartsWithComputer,
                    ).takeIf { background.canStartWithComputer },
                    TrayToggle(
                        desktopText("Keep in the tray when closed", language),
                        isOn = { background.keepInTray.value },
                        onChange = { enabled -> background.setKeepInTray(enabled); true },
                    ),
                ),
                beforeMenu = background::reload,
            ).takeIf { it.install() }
        } else {
            null
        }
        if (icon == null) windowVisible = true
        icon
    }
    DisposableEffect(tray) { onDispose { tray?.remove() } }
    val traySupported = tray != null
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
            if (traySupported && background.keepInTray.value) windowVisible = false else exitApplication()
        },
        visible = windowVisible,
        title = AppIdentity.NAME,
        icon = windowIcon,
        state = windowState,
        onPreviewKeyEvent = shortcuts::handle,
    ) {
        window.minimumSize = java.awt.Dimension(640, 480)
        // Shown, restored and in front: also when it was already open behind other windows.
        LaunchedEffect(showRequests) {
            if (showRequests == 0) return@LaunchedEffect
            if (windowState.isMinimized) windowState.isMinimized = false
            java.awt.EventQueue.invokeLater {
                window.toFront()
                window.requestFocus()
            }
        }
        CompositionLocalProvider(
            LocalDesktopShortcuts provides shortcuts,
            LocalDesktopNotifier provides tray,
            LocalDesktopBackgroundSettings provides background,
        ) {
            LifeTrackerDesktopApp(onShowWindow = { java.awt.EventQueue.invokeLater(openWindow) })
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
