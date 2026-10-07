package com.ced2711.lifetracker.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.ced2711.lifetracker.desktop.windows.WindowsTitleBar
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.ui.theme.TaskLedgerTheme
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The window's title bar takes the app's theme instead of Windows' light default. */
@OptIn(ExperimentalTestApi::class)
class DesktopTitleBarTest {
    @Test
    fun coloursAreGivenToWindowsBlueGreenRed() {
        assertEquals(0x00332211, WindowsTitleBar.colorRef(0x112233))
        assertEquals(0x00FFFFFF, WindowsTitleBar.colorRef(0xFFFFFF))
        assertEquals(0x000000FF, WindowsTitleBar.colorRef(0xFF0000))
    }

    private fun colorsFor(mode: ThemeMode): TitleBarColors {
        var seen: TitleBarColors? = null
        runDesktopComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDesktopTitleBar provides { seen = it }) {
                    TaskLedgerTheme(mode, AccentColor.TEAL) { TitleBarFollowsTheme() }
                }
            }
            waitForIdle()
        }
        return assertNotNull(seen).let { seen!! }
    }

    @Test
    fun aDarkThemeAsksForADarkTitleBarInTheAppBackgroundAndALightOneForALightTheme() {
        val dark = colorsFor(ThemeMode.DARK)
        assertTrue(dark.dark)
        assertTrue("a dark caption", brightness(dark.caption) < 0x40)
        assertTrue("light text on it", brightness(dark.text) > 0xA0)

        val light = colorsFor(ThemeMode.LIGHT)
        assertFalse(light.dark)
        assertTrue("a light caption", brightness(light.caption) > 0xC0)
        assertTrue("dark text on it", brightness(light.text) < 0x60)
    }

    @Test
    fun windowsTakesTheColoursForARealWindow() {
        assumeTrue(DesktopPlatform.isWindows)
        assumeFalse(GraphicsEnvironment.isHeadless())
        val frame = JFrame("title bar test")
        try {
            // Displayable (it has a native window) but never shown on screen.
            frame.pack()
            assertTrue(WindowsTitleBar.apply(frame, dark = true, caption = 0x111214, text = 0xE6E6E6))
            assertTrue(WindowsTitleBar.apply(frame, dark = false, caption = 0xF6F6F4, text = 0x1B1B1B))
            assertFalse("never shown", frame.isVisible)
        } finally {
            frame.dispose()
        }
    }

    private fun brightness(rgb: Int): Int = ((rgb shr 16 and 0xFF) + (rgb shr 8 and 0xFF) + (rgb and 0xFF)) / 3
}
