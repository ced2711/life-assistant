package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.domain.model.TodoPriority

/** Pure desktop UI rules kept separate so filtering behavior can be regression-tested. */
data class DesktopTodoFilter(
    val allCategories: Boolean = true,
    val categoryIds: Set<Long> = emptySet(),
    val includeUncategorized: Boolean = false,
    val priority: TodoPriority? = null,
    val tag: String? = null,
    val showCompleted: Boolean = false,
)

fun descendantCategoryIds(categories: List<CategoryEntity>, roots: Set<Long>): Set<Long> {
    if (roots.isEmpty()) return emptySet()
    val children = categories.groupBy { it.parentId }
    val result = roots.toMutableSet()
    val pending = ArrayDeque(roots.toList())
    while (pending.isNotEmpty()) {
        children[pending.removeFirst()].orEmpty().forEach { child ->
            if (result.add(child.id)) pending.addLast(child.id)
        }
    }
    return result
}

fun categoryMatches(categoryId: Long?, filter: DesktopTodoFilter, includedCategoryIds: Set<Long>): Boolean =
    filter.allCategories || (categoryId == null && filter.includeUncategorized) || categoryId in includedCategoryIds

fun isValidDesktopAmountInput(value: String): Boolean =
    value.isEmpty() || value.matches(Regex("\\d*(\\.\\d{0,2})?"))

fun desktopSortLabel(sortBy: DesktopTodoSort): String = when (sortBy) {
    DesktopTodoSort.DEADLINE -> "Deadline"
    DesktopTodoSort.PRIORITY -> "Priority"
    DesktopTodoSort.TITLE -> "Title"
}

enum class DesktopTodoSort { DEADLINE, PRIORITY, TITLE }

