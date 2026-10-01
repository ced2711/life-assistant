package com.ced2711.lifetracker.ui.adaptive

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Hide only the status bar; keep system navigation and edge-swipe access available. */
internal fun hideAppStatusBar(window: Window, drawIntoDisplayCutout: Boolean = true) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        // Legacy Android otherwise restores the status bar whenever the IME opens.
        // PaneWindowInsets observes the visible frame for keyboard avoidance on these APIs,
        // where FLAG_FULLSCREEN prevents the system's usual adjustResize behavior.
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
    if (drawIntoDisplayCutout && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val cutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (window.attributes.layoutInDisplayCutoutMode != cutoutMode) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = cutoutMode }
        }
    }
    WindowInsetsControllerCompat(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.statusBars())
    }
}
