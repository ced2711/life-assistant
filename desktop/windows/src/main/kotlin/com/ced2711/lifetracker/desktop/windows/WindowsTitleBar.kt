package com.ced2711.lifetracker.desktop.windows

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary

/**
 * The window's title bar, which Windows draws itself and by default light whenever Windows is in
 * light mode, whatever the app looks like. Desktop Window Manager attributes make it dark (Windows
 * 10 and 11) and, on Windows 11, give it the app's own background and text colours.
 */
object WindowsTitleBar {
    private interface Dwm : StdCallLibrary {
        @Suppress("FunctionName")
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int
    }

    private val dwm: Dwm? by lazy { runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() }

    /**
     * Colours the title bar of [window], which must be displayable. [caption] and [text] are
     * 0xRRGGBB. Returns whether the light or dark frame was set; the exact colours need Windows 11
     * and are skipped quietly on Windows 10.
     */
    fun apply(window: java.awt.Window, dark: Boolean, caption: Int, text: Int): Boolean {
        val api = dwm ?: return false
        val pointer = runCatching { Native.getComponentPointer(window) }.getOrNull() ?: return false
        val hwnd = WinDef.HWND(pointer)
        fun set(attribute: Int, value: Int): Boolean {
            val memory = Memory(4).apply { setInt(0, value) }
            return api.DwmSetWindowAttribute(hwnd, attribute, memory, 4) == 0
        }
        val frame = set(DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0) ||
            set(DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1, if (dark) 1 else 0)
        set(DWMWA_CAPTION_COLOR, colorRef(caption))
        set(DWMWA_TEXT_COLOR, colorRef(text))
        // Redraws the frame now rather than when the window is next activated.
        User32.INSTANCE.SetWindowPos(
            hwnd, null, 0, 0, 0, 0,
            WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or WinUser.SWP_NOZORDER or SWP_NOACTIVATE or WinUser.SWP_FRAMECHANGED,
        )
        return frame
    }

    /** 0xRRGGBB as a Windows COLORREF, which is 0x00BBGGRR. */
    internal fun colorRef(rgb: Int): Int =
        ((rgb and 0xFF) shl 16) or (rgb and 0xFF00) or ((rgb shr 16) and 0xFF)

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36
    private const val SWP_NOACTIVATE = 0x0010
}
