package com.ced2711.lifetracker.widget

internal const val WIDGET_TODO_ROW_HEIGHT_DP = 48
private const val COMPACT_WIDGET_MAX_HEIGHT_EXCLUSIVE_DP = 192
private const val COMPACT_WIDGET_CHROME_HEIGHT_DP = 62
private const val REGULAR_WIDGET_CHROME_HEIGHT_DP = 96

internal enum class WidgetLayout { TINY, WIDE_SHORT, STANDARD }

internal fun widgetLayoutForSize(widthDp: Int, heightDp: Int): WidgetLayout = when {
    heightDp < 90 && widthDp < 220 -> WidgetLayout.TINY
    heightDp < 160 && widthDp >= 220 -> WidgetLayout.WIDE_SHORT
    else -> WidgetLayout.STANDARD
}

/**
 * Calculates capacity only from vertical space. Width must never make a single-column list claim
 * room for extra rows, and one 48 dp row remains available at the minimum supported widget size.
 */
internal fun visibleTodoCountForWidget(
    heightDp: Int,
    availableCount: Int,
): Int {
    if (availableCount <= 0) return 0
    val reservedChromeHeight = if (heightDp < COMPACT_WIDGET_MAX_HEIGHT_EXCLUSIVE_DP) {
        COMPACT_WIDGET_CHROME_HEIGHT_DP
    } else {
        REGULAR_WIDGET_CHROME_HEIGHT_DP
    }
    val capacity = ((heightDp - reservedChromeHeight) / WIDGET_TODO_ROW_HEIGHT_DP)
        .coerceAtLeast(1)
    return minOf(capacity, availableCount)
}
