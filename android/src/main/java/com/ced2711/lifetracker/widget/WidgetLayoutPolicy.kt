package com.ced2711.lifetracker.widget

/** Height of one todo line: the smallest comfortable touch target. */
internal const val WIDGET_TODO_ROW_HEIGHT_DP = 48

internal enum class WidgetLayout { TINY, WIDE_SHORT, STANDARD }

/**
 * Which of the three layouts fits: a single small cell shows the count, a wide single row shows
 * the next todo, and everything taller shows the list, which scrolls.
 */
internal fun widgetLayoutForSize(widthDp: Int, heightDp: Int): WidgetLayout = when {
    heightDp < 90 && widthDp < 220 -> WidgetLayout.TINY
    heightDp < 160 && widthDp >= 220 -> WidgetLayout.WIDE_SHORT
    else -> WidgetLayout.STANDARD
}
