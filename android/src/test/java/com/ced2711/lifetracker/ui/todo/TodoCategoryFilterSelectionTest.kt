package com.ced2711.lifetracker.ui.todo

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoCategoryFilterSelectionTest {
    @Test
    fun `selecting a category turns off all and allows multiple selections`() {
        var selection = setOf(ALL_CATEGORIES_FILTER_KEY)

        selection = toggleTodoCategoryFilter(selection, 10L)
        selection = toggleTodoCategoryFilter(selection, 20L)

        assertEquals(setOf(10L, 20L), selection)
    }

    @Test
    fun `selecting all clears every individual category`() {
        val selection = toggleTodoCategoryFilter(
            setOf(10L, 20L, UNCATEGORIZED_FILTER_KEY),
            ALL_CATEGORIES_FILTER_KEY,
        )

        assertEquals(setOf(ALL_CATEGORIES_FILTER_KEY), selection)
    }

    @Test
    fun `unticking the final individual category returns to all`() {
        val selection = toggleTodoCategoryFilter(setOf(10L), 10L)

        assertEquals(setOf(ALL_CATEGORIES_FILTER_KEY), selection)
    }

    @Test
    fun `deleted category filters are removed without leaving an empty selection`() {
        assertEquals(
            setOf(UNCATEGORIZED_FILTER_KEY),
            sanitizeTodoCategoryFilters(setOf(10L, UNCATEGORIZED_FILTER_KEY), emptySet()),
        )
        assertEquals(
            setOf(ALL_CATEGORIES_FILTER_KEY),
            sanitizeTodoCategoryFilters(setOf(10L), emptySet()),
        )
    }
}
