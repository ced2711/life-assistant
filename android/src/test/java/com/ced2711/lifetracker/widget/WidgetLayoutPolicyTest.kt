package com.ced2711.lifetracker.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutPolicyTest {
    @Test
    fun wideOneRowWidgetUsesDedicatedHorizontalLayout() {
        assertEquals(WidgetLayout.WIDE_SHORT, widgetLayoutForSize(widthDp = 320, heightDp = 72))
        assertEquals(WidgetLayout.WIDE_SHORT, widgetLayoutForSize(widthDp = 250, heightDp = 110))
    }

    @Test
    fun tinyAndRegularWidgetsKeepPurposeBuiltLayouts() {
        assertEquals(WidgetLayout.TINY, widgetLayoutForSize(widthDp = 110, heightDp = 56))
        assertEquals(WidgetLayout.STANDARD, widgetLayoutForSize(widthDp = 110, heightDp = 110))
        assertEquals(WidgetLayout.STANDARD, widgetLayoutForSize(widthDp = 320, heightDp = 250))
    }

    @Test
    fun todoRowsUseMinimumRecommendedTouchTarget() {
        assertEquals(48, WIDGET_TODO_ROW_HEIGHT_DP)
    }
}
