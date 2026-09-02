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
    fun compactWidgetShowsOneTodo() {
        assertEquals(1, visibleTodoCountForWidget(heightDp = 110, availableCount = 8))
    }

    @Test
    fun heightAddsRowsAtFortyEightDpIntervals() {
        assertEquals(2, visibleTodoCountForWidget(heightDp = 170, availableCount = 8))
        assertEquals(3, visibleTodoCountForWidget(heightDp = 250, availableCount = 8))
        assertEquals(4, visibleTodoCountForWidget(heightDp = 300, availableCount = 8))
    }

    @Test
    fun capacityNeverInventsRows() {
        assertEquals(2, visibleTodoCountForWidget(heightDp = 300, availableCount = 2))
    }

    @Test
    fun emptyWidgetShowsNoRows() {
        assertEquals(0, visibleTodoCountForWidget(heightDp = 250, availableCount = 0))
    }

    @Test
    fun todoRowsUseMinimumRecommendedTouchTarget() {
        assertEquals(48, WIDGET_TODO_ROW_HEIGHT_DP)
    }
}
