package com.ced2711.lifetracker.ui.todo

import androidx.compose.runtime.saveable.listSaver
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.categoryIdsIncludedByTodoFilter

/*
 * What the todo list shows, worked out from the todos and the chosen filters. No drawing here, so
 * it can be tested without a screen.
 */

internal enum class TodoSort(val label: String) {
    DEADLINE("Deadline"),
    PRIORITY("Priority"),
    CREATED("Created"),
    TITLE("Title"),
    CUSTOM("Custom"),
}

/** Quick views of the list, shown as pills above it. */
internal enum class TodoView(val label: String) {
    ALL("All"),
    TODAY("Today"),
    UPCOMING("Next 7 days"),
    OVERDUE("Overdue"),
    NO_DATE("No date"),
}

internal const val ALL_CATEGORIES_FILTER_KEY = Long.MIN_VALUE
internal const val UNCATEGORIZED_FILTER_KEY = Long.MIN_VALUE + 1

internal fun toggleTodoCategoryFilter(current: Set<Long>, key: Long): Set<Long> {
    if (key == ALL_CATEGORIES_FILTER_KEY) return setOf(ALL_CATEGORIES_FILTER_KEY)
    val specific = current - ALL_CATEGORIES_FILTER_KEY
    return if (key in specific) {
        (specific - key).ifEmpty { setOf(ALL_CATEGORIES_FILTER_KEY) }
    } else {
        specific + key
    }
}

internal fun sanitizeTodoCategoryFilters(
    current: Set<Long>,
    validCategoryIds: Set<Long>,
): Set<Long> {
    if (ALL_CATEGORIES_FILTER_KEY in current) return setOf(ALL_CATEGORIES_FILTER_KEY)
    return current
        .filterTo(linkedSetOf()) { it == UNCATEGORIZED_FILTER_KEY || it in validCategoryIds }
        .ifEmpty { setOf(ALL_CATEGORIES_FILTER_KEY) }
}

/** Every choice that narrows or orders the list. */
internal data class TodoFilter(
    val view: TodoView = TodoView.ALL,
    val search: String = "",
    val priority: TodoPriority? = null,
    val tag: String? = null,
    val categories: Set<Long> = setOf(ALL_CATEGORIES_FILTER_KEY),
    val sort: TodoSort = TodoSort.DEADLINE,
    val showCompleted: Boolean = false,
) {
    val allCategories: Boolean get() = ALL_CATEGORIES_FILTER_KEY in categories

    /** How many choices of the filter sheet differ from the defaults. */
    val changedInSheet: Int
        get() = listOf(priority != null, tag != null, !allCategories, sort != TodoSort.DEADLINE).count { it }

    fun cleared(): TodoFilter = TodoFilter(view = view, showCompleted = showCompleted)
}

internal val TodoFilterSaver = listSaver<TodoFilter, String>(
    save = {
        listOf(
            it.view.name,
            it.search,
            it.priority?.name.orEmpty(),
            it.tag.orEmpty(),
            it.categories.joinToString(","),
            it.sort.name,
            it.showCompleted.toString(),
        )
    },
    restore = { saved ->
        TodoFilter(
            view = TodoView.entries.firstOrNull { it.name == saved[0] } ?: TodoView.ALL,
            search = saved[1],
            priority = TodoPriority.entries.firstOrNull { it.name == saved[2] },
            tag = saved[3].ifEmpty { null },
            categories = saved[4].split(',').mapNotNull(String::toLongOrNull).toSet().ifEmpty { setOf(ALL_CATEGORIES_FILTER_KEY) },
            sort = TodoSort.entries.firstOrNull { it.name == saved[5] } ?: TodoSort.DEADLINE,
            showCompleted = saved[6].toBoolean(),
        )
    },
)

/** A run of todos under one heading; [label] is null when the list is not grouped. */
internal data class TodoGroup(val label: String?, val todos: List<TodoEntity>)

internal data class TodoListModel(
    val groups: List<TodoGroup>,
    val active: List<TodoEntity>,
    val completed: List<TodoEntity>,
    /** Open todos per quick view, before the other filters, for the counts on the pills. */
    val viewCounts: Map<TodoView, Int>,
)

private fun TodoView.matches(todo: TodoEntity, today: Long): Boolean = when (this) {
    TodoView.ALL -> true
    TodoView.TODAY -> todo.deadlineEpochDay != null && todo.deadlineEpochDay <= today
    TodoView.UPCOMING -> todo.deadlineEpochDay != null && todo.deadlineEpochDay <= today + 7
    TodoView.OVERDUE -> todo.deadlineEpochDay != null && todo.deadlineEpochDay < today
    TodoView.NO_DATE -> todo.deadlineEpochDay == null
}

/** The heading a todo goes under when the list is ordered by deadline. */
internal fun deadlineGroup(todo: TodoEntity, today: Long): String {
    val day = todo.deadlineEpochDay ?: return "No date"
    return when {
        day < today -> "Overdue"
        day == today -> "Today"
        day == today + 1 -> "Tomorrow"
        day <= today + 7 -> "Next 7 days"
        else -> "Later"
    }
}

private fun comparator(sort: TodoSort): Comparator<TodoEntity> = when (sort) {
    TodoSort.DEADLINE -> compareBy<TodoEntity> { it.deadlineEpochDay == null }
        .thenBy { it.deadlineEpochDay ?: Long.MAX_VALUE }
        .thenBy { it.deadlineMinute ?: Int.MAX_VALUE }
        .thenByDescending { it.priority.ordinal }
        .thenByDescending { it.createdAt }
    TodoSort.PRIORITY -> compareByDescending<TodoEntity> { it.priority.ordinal }
        .thenBy { it.deadlineEpochDay ?: Long.MAX_VALUE }
        .thenByDescending { it.createdAt }
    TodoSort.CREATED -> compareByDescending { it.createdAt }
    TodoSort.TITLE -> compareBy { it.title.lowercase() }
    TodoSort.CUSTOM -> compareByDescending<TodoEntity> { it.customOrder }
        .thenByDescending { it.createdAt }
        .thenByDescending { it.id }
}

internal fun buildTodoList(
    active: List<TodoEntity>,
    completed: List<TodoEntity>,
    filter: TodoFilter,
    categoryParentIds: Map<Long, Long?>,
    today: Long,
): TodoListModel {
    val query = filter.search.trim()
    val tagQuery = query.removePrefix("#")
    val categoryIds: Set<Long?> = buildSet {
        if (UNCATEGORIZED_FILTER_KEY in filter.categories) add(null)
        filter.categories
            .filter { it != ALL_CATEGORIES_FILTER_KEY && it != UNCATEGORIZED_FILTER_KEY }
            .forEach { addAll(categoryIdsIncludedByTodoFilter(it, categoryParentIds)) }
    }

    fun matches(todo: TodoEntity): Boolean =
        (query.isEmpty() || todo.title.contains(query, true) || todo.description.contains(query, true) ||
            todo.tagsCsv.split(',').any { tagQuery.isNotEmpty() && it.contains(tagQuery, true) }) &&
            (filter.priority == null || todo.priority == filter.priority) &&
            (filter.tag == null || todo.tagsCsv.split(',').any { it.trim().equals(filter.tag, true) }) &&
            (filter.allCategories || todo.categoryId in categoryIds)

    // A repeating todo shows its occurrences of the coming week and otherwise only the next one;
    // the calendar shows them all.
    val nextOfSeries = active.filter { it.seriesId != null }
        .groupBy { it.seriesId }
        .mapValues { (_, occurrences) -> occurrences.minOf { it.deadlineEpochDay ?: Long.MAX_VALUE } }
    fun listed(todo: TodoEntity) = todo.seriesId == null ||
        (todo.deadlineEpochDay ?: Long.MAX_VALUE).let { it <= today + 7 || it == nextOfSeries[todo.seriesId] }

    val listable = active.filter(::listed)
    val shown = listable.filter { matches(it) && filter.view.matches(it, today) }.sortedWith(comparator(filter.sort))
    val groups = if (filter.sort == TodoSort.DEADLINE) {
        val order = listOf("Overdue", "Today", "Tomorrow", "Next 7 days", "Later", "No date")
        shown.groupBy { deadlineGroup(it, today) }.entries.sortedBy { order.indexOf(it.key) }.map { TodoGroup(it.key, it.value) }
    } else {
        if (shown.isEmpty()) emptyList() else listOf(TodoGroup(null, shown))
    }
    return TodoListModel(
        groups = groups,
        active = shown,
        completed = completed.filter(::matches).sortedByDescending { it.completedAt },
        viewCounts = TodoView.entries.associateWith { view -> listable.count { view.matches(it, today) } },
    )
}
