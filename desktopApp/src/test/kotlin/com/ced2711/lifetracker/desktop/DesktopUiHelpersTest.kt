package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.domain.model.TodoPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopUiHelpersTest {
    @Test
    fun descendantsIncludeNestedCategoriesOnly() {
        val categories = listOf(
            CategoryEntity(1, "Life"),
            CategoryEntity(2, "School", 1),
            CategoryEntity(3, "Math", 2),
            CategoryEntity(4, "Games"),
        )
        assertEquals(setOf(1L, 2L, 3L), descendantCategoryIds(categories, setOf(1)))
    }

    @Test
    fun allCategoriesIsExclusiveAndUncategorizedCanBeSelected() {
        val filter = DesktopTodoFilter(allCategories = false, includeUncategorized = true, priority = TodoPriority.HIGH)
        assertTrue(categoryMatches(null, filter, emptySet()))
        assertFalse(categoryMatches(4, filter, emptySet()))
    }

    @Test
    fun amountInputRejectsNegativeAndMoreThanTwoDecimals() {
        assertTrue(isValidDesktopAmountInput("123.45"))
        assertTrue(isValidDesktopAmountInput(""))
        assertFalse(isValidDesktopAmountInput("-1"))
        assertFalse(isValidDesktopAmountInput("1.234"))
    }
}

