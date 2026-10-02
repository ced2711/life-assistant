package com.ced2711.lifetracker.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.ReminderOffsetPreset
import com.ced2711.lifetracker.domain.model.SeriesEditScope
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.parseTags
import com.ced2711.lifetracker.ui.design.CheckCircle
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.priorityColor
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** What the editor pane is showing. */
private sealed interface TodoEditorTarget {
    data class New(val epochDay: Long? = null) : TodoEditorTarget
    data class Existing(val id: Long) : TodoEditorTarget
}

/** Quick views of the todo list, shown at the top of the filter column. */
internal enum class TodoView(val label: String) {
    ALL("All todos"),
    TODAY("Today"),
    UPCOMING("Next 7 days"),
    OVERDUE("Overdue"),
    NO_DATE("No date"),
}

private fun TodoView.matches(todo: TodoEntity, today: Long): Boolean = when (this) {
    TodoView.ALL -> true
    TodoView.TODAY -> todo.deadlineEpochDay != null && todo.deadlineEpochDay <= today
    TodoView.UPCOMING -> todo.deadlineEpochDay != null && todo.deadlineEpochDay <= today + 7
    TodoView.OVERDUE -> todo.deadlineEpochDay != null && todo.deadlineEpochDay < today
    TodoView.NO_DATE -> todo.deadlineEpochDay == null
}

/** Every filter of the todo list in one place, shared by the filter column and the compact filter row. */
private class TodoFilters {
    var view by mutableStateOf(TodoView.ALL)
    var allCategories by mutableStateOf(true)
    var selectedCategories by mutableStateOf(emptySet<Long>())
    var includeUncategorized by mutableStateOf(false)
    var priority by mutableStateOf<TodoPriority?>(null)
    var tag by mutableStateOf<String?>(null)
    var sortBy by mutableStateOf(DesktopTodoSort.DEADLINE)
    var showCompleted by mutableStateOf(false)

    fun selectAllCategories() {
        allCategories = true
        selectedCategories = emptySet()
        includeUncategorized = false
    }

    fun toggleCategory(id: Long) {
        allCategories = false
        selectedCategories = if (id in selectedCategories) selectedCategories - id else selectedCategories + id
        if (selectedCategories.isEmpty() && !includeUncategorized) allCategories = true
    }

    fun toggleUncategorized() {
        allCategories = false
        includeUncategorized = !includeUncategorized
        if (selectedCategories.isEmpty() && !includeUncategorized) allCategories = true
    }

    /** The category a quick-added todo goes into: the one selected, if exactly one is. */
    val singleCategory: Long? get() = selectedCategories.singleOrNull().takeIf { !allCategories && !includeUncategorized }
}

/** Date groups of the list when it is sorted by deadline. */
private fun deadlineGroup(todo: TodoEntity, today: Long): String {
    val day = todo.deadlineEpochDay ?: return "No date"
    return when {
        day < today -> "Overdue"
        day == today -> "Today"
        day == today + 1 -> "Tomorrow"
        day <= today + 7 -> "Next 7 days"
        else -> "Later"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodoPage(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val snackbar = remember { SnackbarHostState() }
    val filters = remember { TodoFilters() }
    var query by remember { mutableStateOf("") }
    var quickAdd by remember { mutableStateOf("") }
    var showFilterRow by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var editor by remember { mutableStateOf<TodoEditorTarget?>(null) }
    var expanded by remember { mutableStateOf(emptySet<Long>()) }
    var newCategory by remember { mutableStateOf(false) }
    var renamingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var deletingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var renamingTag by remember { mutableStateOf<String?>(null) }
    var deletingTag by remember { mutableStateOf<String?>(null) }
    var completeWithSubtasks by remember { mutableStateOf<TodoEntity?>(null) }
    var deleteScopeFor by remember { mutableStateOf<TodoEntity?>(null) }
    val searchFocus = remember { FocusRequester() }
    val quickAddFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val today = LocalDate.now().toEpochDay()

    RegisterPageShortcuts(
        onNew = { editor = TodoEditorTarget.New() },
        onFind = { runCatching { searchFocus.requestFocus() } },
    )

    val includedCategoryIds = descendantCategoryIds(snapshot.categories, filters.selectedCategories)
    val filter = DesktopTodoFilter(filters.allCategories, filters.selectedCategories, filters.includeUncategorized, filters.priority, filters.tag, filters.showCompleted)
    val live = snapshot.todos.filter { it.deletedAt == null }
    val matching = live
        .filter { categoryMatches(it.categoryId, filter, includedCategoryIds) }
        .filter { filters.priority == null || it.priority == filters.priority }
        .filter { filters.tag == null || parseTags(it.tagsCsv).any { tag -> tag.equals(filters.tag, true) } }
        .filter { todo ->
            query.isBlank() || todo.title.contains(query, true) || todo.description.contains(query, true) ||
                parseTags(todo.tagsCsv).any { it.contains(query.trim().removePrefix("#"), true) }
        }
    // A repeating todo shows its occurrences of the coming week and otherwise only the next one;
    // the calendar shows them all.
    val nextOfSeries = live.filter { it.completedAt == null && it.seriesId != null }
        .groupBy { it.seriesId }
        .mapValues { (_, occurrences) -> occurrences.minOf { it.deadlineEpochDay ?: Long.MAX_VALUE } }
    val active = matching
        .filter { it.completedAt == null && filters.view.matches(it, today) }
        .filter { todo -> todo.seriesId == null || (todo.deadlineEpochDay ?: Long.MAX_VALUE).let { it <= today + 7 || it == nextOfSeries[todo.seriesId] } }
        .sortedWith(todoComparator(filters.sortBy))
    val completed = matching.filter { it.completedAt != null }.sortedByDescending { it.completedAt }
    val visible = active + if (filters.showCompleted) completed else emptyList()
    val subtasksByTodo = snapshot.subtasks.groupBy { it.todoId }

    fun deleteTodo(todo: TodoEntity, deleteScope: SeriesEditScope) {
        scope.launch {
            val deletion = store.deleteTodoWithUndo(todo.id, deleteScope) ?: return@launch
            if (selectedId == todo.id) selectedId = null
            if (editor == TodoEditorTarget.Existing(todo.id)) editor = null
            val result = snackbar.showSnackbar(
                message = desktopText(if (deletion.todoIds.size > 1) "Todos deleted" else "Todo deleted", language),
                actionLabel = desktopText("Undo", language),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) store.undoDeleteTodo(deletion) else store.runMaintenance()
        }
    }

    fun requestDelete(todo: TodoEntity) {
        if (todo.seriesId != null) deleteScopeFor = todo else deleteTodo(todo, SeriesEditScope.ONLY_THIS_OCCURRENCE)
    }

    fun toggle(todo: TodoEntity) {
        val open = subtasksByTodo[todo.id].orEmpty().count { !it.isCompleted }
        when {
            todo.completedAt != null -> scope.launch { store.setTodoCompleted(todo.id, false) }
            open > 0 -> completeWithSubtasks = todo
            else -> scope.launch { store.setTodoCompleted(todo.id, true) }
        }
    }

    fun addQuick() {
        val text = quickAdd.trim()
        if (text.isEmpty()) return
        quickAdd = ""
        // A todo added while looking at Today or the next 7 days is due today.
        val due = today.takeIf { filters.view == TodoView.TODAY || filters.view == TodoView.UPCOMING }
        scope.launch {
            store.saveTodo(
                TodoDraft(
                    description = text,
                    categoryId = filters.singleCategory,
                    deadlineEpochDay = due,
                    reminderOffsetsMinutes = if (due != null) snapshot.settings.defaultReminderOffsetsMinutes.toList() else emptyList(),
                ),
                SeriesEditScope.ONLY_THIS_OCCURRENCE,
            )
        }
    }

    fun moveSelection(delta: Int) {
        if (visible.isEmpty()) return
        val index = visible.indexOfFirst { it.id == selectedId }
        val next = (if (index < 0) 0 else index + delta).coerceIn(0, visible.lastIndex)
        selectedId = visible[next].id
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val editorTarget = editor
            val editorWidth = 440.dp
            val listMinimum = 520.dp
            // The filter column shows whenever list, filters and an open editor still fit.
            val filterColumn = maxWidth - (if (editorTarget != null) editorWidth else 0.dp) >= listMinimum + 232.dp
            val editorBeside = maxWidth >= listMinimum + editorWidth
            if (!editorBeside && editorTarget != null) {
                TodoEditor(
                    snapshot = snapshot,
                    store = store,
                    target = editorTarget,
                    onClose = { editor = null },
                    onDelete = { requestDelete(it) },
                    modifier = Modifier.fillMaxSize(),
                )
                return@BoxWithConstraints
            }
            Row(Modifier.fillMaxSize()) {
                if (filterColumn) {
                    TodoFilterColumn(
                        snapshot = snapshot,
                        live = live,
                        filters = filters,
                        today = today,
                        onNewCategory = { newCategory = true },
                        onRenameCategory = { renamingCategory = it },
                        onDeleteCategory = { deletingCategory = it },
                        onRenameTag = { renamingTag = it },
                        onDeleteTag = { deletingTag = it },
                        modifier = Modifier.width(232.dp),
                    )
                    ColumnDivider()
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    PageHeader(
                        if (filters.view == TodoView.ALL) "Todo" else filters.view.label,
                        desktopActiveTasks(active.size, language),
                    ) {
                        ChoiceMenu(
                            label = desktopText("Sort"),
                            options = DesktopTodoSort.entries,
                            selected = filters.sortBy,
                            optionLabel = { desktopText(desktopSortLabel(it), language) },
                            onSelect = { filters.sortBy = it },
                        )
                        if (!filterColumn) {
                            IconButton(onClick = { showFilterRow = !showFilterRow }) { Icon(Icons.Rounded.FilterList, desktopText("Filters")) }
                        }
                        Button(onClick = { editor = TodoEditorTarget.New() }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(desktopText("New todo"))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = PagePadding),
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LifeTextField(
                            value = quickAdd,
                            onValueChange = { quickAdd = it },
                            placeholder = desktopText("Add a todo and press Enter"),
                            leadingIcon = Icons.Rounded.Add,
                            modifier = Modifier.weight(1f).focusRequester(quickAddFocus).onEnter(::addQuick),
                        )
                        LifeTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = desktopText("Search (Ctrl+F)"),
                            leadingIcon = Icons.Rounded.Search,
                            trailing = if (query.isNotEmpty()) {
                                { IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, desktopText("Clear"), Modifier.size(16.dp)) } }
                            } else {
                                null
                            },
                            modifier = Modifier.width(240.dp).focusRequester(searchFocus),
                        )
                    }
                    if (!filterColumn && showFilterRow) TodoFilterRow(snapshot, live, filters) { newCategory = true }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PagePadding - Space.md)
                            .focusRequester(listFocus)
                            .listKeys(
                                onUp = { moveSelection(-1) },
                                onDown = { moveSelection(1) },
                                onOpen = { selectedId?.let { editor = TodoEditorTarget.Existing(it) } },
                                onToggle = { visible.firstOrNull { it.id == selectedId }?.let(::toggle) },
                                onDelete = { visible.firstOrNull { it.id == selectedId }?.let(::requestDelete) },
                            )
                            .focusable(),
                    ) {
                        item { Spacer(Modifier.height(Space.md)) }
                        if (active.isEmpty()) {
                            item {
                                com.ced2711.lifetracker.ui.design.EmptyState(
                                    title = desktopText(if (query.isBlank()) "Nothing to do here" else "No todos match"),
                                    icon = Icons.Rounded.Search.takeIf { query.isNotBlank() } ?: Icons.Rounded.Add,
                                    body = desktopText(if (query.isBlank()) "Add a todo above, or press Ctrl+N for one with all details." else "Try other words, or clear the filters."),
                                )
                            }
                        }
                        val grouped = filters.sortBy == DesktopTodoSort.DEADLINE
                        active.forEachIndexed { index, todo ->
                            val group = deadlineGroup(todo, today)
                            if (grouped && (index == 0 || deadlineGroup(active[index - 1], today) != group)) {
                                item(key = "group-$group") {
                                    SectionLabel(
                                        desktopText(group),
                                        count = active.count { deadlineGroup(it, today) == group },
                                        color = if (group == "Overdue") LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = Space.md, top = if (index == 0) 0.dp else Space.md),
                                    )
                                }
                            }
                            item(key = todo.id) {
                                TodoRow(
                                    todo = todo,
                                    snapshot = snapshot,
                                    store = store,
                                    selected = todo.id == selectedId || editor == TodoEditorTarget.Existing(todo.id),
                                    expanded = todo.id in expanded,
                                    onExpand = { expanded = if (todo.id in expanded) expanded - todo.id else expanded + todo.id },
                                    onClick = {
                                        selectedId = todo.id
                                        editor = TodoEditorTarget.Existing(todo.id)
                                        runCatching { listFocus.requestFocus() }
                                    },
                                    onToggle = { toggle(todo) },
                                    onDelete = { requestDelete(todo) },
                                    onMoveUp = active.getOrNull(index - 1)?.takeIf { filters.sortBy == DesktopTodoSort.CUSTOM }?.let { above -> { scope.launch { store.moveTodo(todo.id, above.id) } } },
                                    onMoveDown = active.getOrNull(index + 1)?.takeIf { filters.sortBy == DesktopTodoSort.CUSTOM }?.let { below -> { scope.launch { store.moveTodo(todo.id, below.id) } } },
                                )
                            }
                        }
                        if (completed.isNotEmpty()) {
                            item(key = "completed-header") {
                                Row(
                                    Modifier.padding(start = Space.xs, top = Space.lg).clip(RoundedCornerShape(8.dp))
                                        .clickable(role = Role.Button) { filters.showCompleted = !filters.showCompleted }
                                        .padding(horizontal = Space.sm, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(if (filters.showCompleted) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(desktopCompletedCount(completed.size, language), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                        if (filters.showCompleted) items(completed, key = { "done-${it.id}" }) { todo ->
                            TodoRow(
                                todo = todo,
                                snapshot = snapshot,
                                store = store,
                                selected = todo.id == selectedId,
                                expanded = false,
                                onExpand = {},
                                onClick = { selectedId = todo.id; editor = TodoEditorTarget.Existing(todo.id) },
                                onToggle = { toggle(todo) },
                                onDelete = { requestDelete(todo) },
                            )
                        }
                        item { Spacer(Modifier.height(48.dp)) }
                    }
                }
                if (editorTarget != null) {
                    ColumnDivider()
                    TodoEditor(
                        snapshot = snapshot,
                        store = store,
                        target = editorTarget,
                        onClose = { editor = null },
                        onDelete = { requestDelete(it) },
                        modifier = Modifier.width(editorWidth).fillMaxHeight(),
                    )
                }
            }
        }
    }

    completeWithSubtasks?.let { todo ->
        val open = subtasksByTodo[todo.id].orEmpty().count { !it.isCompleted }
        AlertDialog(
            onDismissRequest = { completeWithSubtasks = null },
            title = { Text(desktopText("Complete this todo?")) },
            text = { Text(desktopOpenSubtasks(open, language)) },
            dismissButton = {
                TextButton(onClick = {
                    completeWithSubtasks = null
                    scope.launch { store.setTodoCompleted(todo.id, true, completeSubtasks = false) }
                }) { Text(desktopText("Only the todo")) }
            },
            confirmButton = {
                Button(onClick = {
                    completeWithSubtasks = null
                    scope.launch { store.setTodoCompleted(todo.id, true, completeSubtasks = true) }
                }) { Text(desktopText("Todo and subtasks")) }
            },
        )
    }
    deleteScopeFor?.let { todo ->
        SeriesScopeDialog(
            title = "Delete repeating todo",
            onDismiss = { deleteScopeFor = null },
            onChoose = { chosen ->
                deleteScopeFor = null
                deleteTodo(todo, chosen)
            },
        )
    }
    renamingCategory?.let { category ->
        SimpleNameDialog(desktopText("Rename category"), { renamingCategory = null }, initial = category.name) { name ->
            scope.launch { store.renameCategory(category.id, name) }
            renamingCategory = null
        }
    }
    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text(desktopText("Delete category")) },
            text = { Text(desktopText("Its todos become uncategorized and its subcategories move up one level.")) },
            dismissButton = { TextButton(onClick = { deletingCategory = null }) { Text(desktopText("Cancel")) } },
            confirmButton = {
                Button(onClick = {
                    scope.launch { store.deleteCategory(category.id) }
                    filters.selectAllCategories()
                    deletingCategory = null
                }) { Text(desktopText("Delete")) }
            },
        )
    }
    renamingTag?.let { tag ->
        SimpleNameDialog(desktopText("Rename tag"), { renamingTag = null }, initial = tag) { name ->
            scope.launch { store.renameTodoTag(tag, name) }
            if (filters.tag == tag) filters.tag = null
            renamingTag = null
        }
    }
    deletingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text(desktopText("Delete tag")) },
            text = { Text("#$tag") },
            dismissButton = { TextButton(onClick = { deletingTag = null }) { Text(desktopText("Cancel")) } },
            confirmButton = {
                Button(onClick = {
                    scope.launch { store.deleteTodoTag(tag) }
                    if (filters.tag == tag) filters.tag = null
                    deletingTag = null
                }) { Text(desktopText("Delete")) }
            },
        )
    }
    if (newCategory) {
        NewCategoryDialog(snapshot, defaultParent = filters.singleCategory, onDismiss = { newCategory = false }) { name, parent ->
            scope.launch { store.addCategory(name, parent) }
            newCategory = false
        }
    }
}

/** New category with an explicit parent choice (no longer taken silently from the filter). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewCategoryDialog(snapshot: BackupSnapshot, defaultParent: Long?, onDismiss: () -> Unit, onSave: (String, Long?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf(defaultParent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(desktopText("New category")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                LifeTextField(name, { name = it }, placeholder = desktopText("Name"), modifier = Modifier.fillMaxWidth().onEnter { if (name.isNotBlank()) onSave(name.trim(), parent) })
                FieldLabel("Inside")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(desktopText("Top level"), parent == null, { parent = null }, exclusive = true)
                    snapshot.categories.sortedBy { categoryPath(it.id, snapshot).lowercase() }.forEach { category ->
                        Pill(categoryPath(category.id, snapshot), parent == category.id, { parent = category.id }, exclusive = true)
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) } },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), parent) }) { Text(desktopText("Add")) } },
    )
}

/** Left column of the todo page: quick views, categories as a tree, priority and tags. */
@Composable
private fun TodoFilterColumn(
    snapshot: BackupSnapshot,
    live: List<TodoEntity>,
    filters: TodoFilters,
    today: Long,
    onNewCategory: () -> Unit,
    onRenameCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onRenameTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    modifier: Modifier,
) {
    val open = live.filter { it.completedAt == null }
    FilterColumn(modifier) {
        TodoView.entries.forEach { view ->
            FilterEntry(
                label = desktopText(view.label),
                count = open.count { view.matches(it, today) },
                selected = filters.view == view,
                emphasize = view == TodoView.OVERDUE,
            ) { filters.view = view }
        }
        FilterHeader("Categories") {
            if (!filters.allCategories) ClearFilter { filters.selectAllCategories() }
            IconButton(onClick = onNewCategory, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.Add, desktopText("New category"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val children = snapshot.categories.groupBy { it.parentId }
        fun walk(parent: Long?, depth: Int, entries: MutableList<Pair<CategoryEntity, Int>>) {
            children[parent].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() })).forEach { category ->
                entries += category to depth
                walk(category.id, depth + 1, entries)
            }
        }
        val tree = mutableListOf<Pair<CategoryEntity, Int>>().also { walk(null, 0, it) }
        tree.forEach { (category, depth) ->
            val ids = descendantCategoryIds(snapshot.categories, setOf(category.id))
            FilterEntry(
                label = category.name,
                count = open.count { it.categoryId in ids },
                selected = !filters.allCategories && category.id in filters.selectedCategories,
                indent = depth,
                onRename = { onRenameCategory(category) },
                onDelete = { onDeleteCategory(category) },
            ) { filters.toggleCategory(category.id) }
        }
        FilterEntry(desktopText("Uncategorized"), open.count { it.categoryId == null }, !filters.allCategories && filters.includeUncategorized) { filters.toggleUncategorized() }

        FilterHeader("Priority") { if (filters.priority != null) ClearFilter { filters.priority = null } }
        listOf(TodoPriority.URGENT, TodoPriority.HIGH, TodoPriority.MEDIUM, TodoPriority.LOW).forEach { priority ->
            FilterEntry(desktopText(priorityLabel(priority)), open.count { it.priority == priority }, filters.priority == priority, dot = priorityColor(priority)) {
                filters.priority = if (filters.priority == priority) null else priority
            }
        }

        val tags = live.flatMap { parseTags(it.tagsCsv) }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        if (tags.isNotEmpty()) {
            FilterHeader("Tags") { if (filters.tag != null) ClearFilter { filters.tag = null } }
            tags.forEach { tag ->
                FilterEntry("#$tag", open.count { todo -> parseTags(todo.tagsCsv).any { it.equals(tag, true) } }, filters.tag == tag, onRename = { onRenameTag(tag) }, onDelete = { onDeleteTag(tag) }) {
                    filters.tag = if (filters.tag == tag) null else tag
                }
            }
        }
    }
}

/** "Clear" next to a filter heading, shown only while that filter is on. */
@Composable
private fun ClearFilter(onClick: () -> Unit) {
    Text(
        desktopText("Clear"),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** The same filters as pills, for windows too narrow for the filter column. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodoFilterRow(snapshot: BackupSnapshot, live: List<TodoEntity>, filters: TodoFilters, onNewCategory: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PagePadding, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TodoView.entries.forEach { view -> FilterChipSimple(desktopText(view.label), filters.view == view, exclusive = true) { filters.view = view } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChipSimple(desktopText("Any priority"), filters.priority == null, exclusive = true) { filters.priority = null }
            listOf(TodoPriority.URGENT, TodoPriority.HIGH, TodoPriority.MEDIUM, TodoPriority.LOW).forEach { item ->
                FilterChipSimple(desktopText(priorityLabel(item)), filters.priority == item, exclusive = true) { filters.priority = if (filters.priority == item) null else item }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChipSimple(desktopText("All categories"), filters.allCategories) { filters.selectAllCategories() }
            FilterChipSimple(desktopText("Uncategorized"), !filters.allCategories && filters.includeUncategorized) { filters.toggleUncategorized() }
            snapshot.categories.forEach { category ->
                FilterChipSimple(categoryPath(category.id, snapshot), !filters.allCategories && category.id in filters.selectedCategories) { filters.toggleCategory(category.id) }
            }
            TextButton(onClick = onNewCategory) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text(desktopText("Category")) }
        }
        val tags = live.flatMap { parseTags(it.tagsCsv) }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { tag -> FilterChipSimple("#$tag", filters.tag == tag) { filters.tag = if (filters.tag == tag) null else tag } }
        }
    }
}

private fun todoComparator(sortBy: DesktopTodoSort): Comparator<TodoEntity> = when (sortBy) {
    DesktopTodoSort.DEADLINE -> compareBy<TodoEntity> { it.deadlineEpochDay ?: Long.MAX_VALUE }.thenBy { it.deadlineMinute ?: Int.MAX_VALUE }.thenByDescending { it.priority.ordinal }
    DesktopTodoSort.PRIORITY -> compareByDescending<TodoEntity> { it.priority.ordinal }.thenBy { it.deadlineEpochDay ?: Long.MAX_VALUE }
    DesktopTodoSort.CREATED -> compareByDescending { it.createdAt }
    DesktopTodoSort.TITLE -> compareBy { it.title.lowercase() }
    DesktopTodoSort.CUSTOM -> compareByDescending<TodoEntity> { it.customOrder }.thenByDescending { it.createdAt }.thenByDescending { it.id }
}

internal fun priorityLabel(priority: TodoPriority): String = when (priority) {
    TodoPriority.NONE -> "None"
    TodoPriority.LOW -> "Low"
    TodoPriority.MEDIUM -> "Medium"
    TodoPriority.HIGH -> "High"
    TodoPriority.URGENT -> "Urgent"
}

/**
 * One todo: round check mark in its priority colour, title, and one line of details (when, open
 * subtasks, category, tags, files). Subtasks unfold under it; delete and reordering show on hover.
 */
@Composable
private fun TodoRow(
    todo: TodoEntity,
    snapshot: BackupSnapshot,
    store: DesktopDataStore,
    selected: Boolean,
    expanded: Boolean,
    onExpand: () -> Unit,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val subtasks = snapshot.subtasks.filter { it.todoId == todo.id }.sortedBy { it.sortOrder }
    val done = todo.completedAt != null
    val attachments = snapshot.attachments.count { it.ownerType == AttachmentOwnerType.TODO && it.ownerId == todo.id && it.pendingDeleteAt == null }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> LifeTheme.colors.accentSoft
            hovered -> MaterialTheme.colorScheme.surfaceContainer
            else -> Color.Transparent
        },
        tween(120),
        label = "todo-row",
    )
    val now = LocalDate.now().toEpochDay()
    val overdue = !done && todo.deadlineEpochDay != null && (todo.deadlineEpochDay < now ||
        (todo.deadlineEpochDay == now && (todo.deadlineMinute ?: 1_440) < LocalTime.now().toSecondOfDay() / 60))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(background).hoverable(interaction).clickable(onClick = onClick)
            .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Row(Modifier.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(
                checked = done,
                onCheckedChange = { onToggle() },
                color = priorityColor(todo.priority) ?: MaterialTheme.colorScheme.primary,
                contentDescription = todo.title,
            )
            Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val details = buildList {
                    todo.deadlineEpochDay?.let { add(formatDeadline(it, todo.deadlineMinute, snapshot, language) to (if (overdue) LifeTheme.colors.danger else null)) }
                    todo.categoryId?.let { add(categoryPath(it, snapshot) to null) }
                    parseTags(todo.tagsCsv).forEach { add("#$it" to MaterialTheme.colorScheme.primary) }
                }
                if (details.isNotEmpty() || subtasks.isNotEmpty() || attachments > 0 || todo.seriesId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        details.forEach { (text, color) ->
                            Text(text, style = MaterialTheme.typography.bodySmall, color = color ?: MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (todo.seriesId != null) Icon(Icons.Rounded.Repeat, desktopText("Repeats"), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (attachments > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AttachFile, desktopText("Attachments"), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(attachments.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (subtasks.isNotEmpty() && !done) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onExpand).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${subtasks.count { it.isCompleted }}/${subtasks.size}", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, desktopText(if (expanded) "Hide subtasks" else "Show subtasks"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (hovered || selected) {
                if (onMoveUp != null) IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.KeyboardArrowUp, desktopText("Move up"), Modifier.size(18.dp)) }
                if (onMoveDown != null) IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.KeyboardArrowDown, desktopText("Move down"), Modifier.size(18.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.DeleteOutline, desktopText("Delete"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                Spacer(Modifier.width(32.dp))
            }
        }
        if (expanded && subtasks.isNotEmpty() && !done) {
            Column(Modifier.padding(start = 40.dp, bottom = 6.dp)) {
                subtasks.forEach { subtask ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckCircle(
                            checked = subtask.isCompleted,
                            onCheckedChange = { checked -> scope.launch { store.setSubtaskCompleted(subtask.id, checked) } },
                            size = 16.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = subtask.description,
                        )
                        Text(
                            subtask.description,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else null,
                            color = if (subtask.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

internal fun formatDeadline(day: Long, minute: Int?, snapshot: BackupSnapshot, language: UiLanguage): String {
    val locale = uiLocale(language)
    val date = LocalDate.ofEpochDay(day)
    val today = LocalDate.now()
    val dayText = when (date) {
        today -> desktopText("Today", language)
        today.plusDays(1) -> desktopText("Tomorrow", language)
        today.minusDays(1) -> desktopText("Yesterday", language)
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + UserFormatting.formatDate(date, snapshot.settings.dateFormat, locale)
    }
    val time = minute?.let { " " + UserFormatting.formatMinuteOfDay(it, snapshot.settings.timeFormat, systemUses24Hour = false, locale = locale) }.orEmpty()
    return dayText + time
}

/** Reads a date field: the user's date format, ISO, or words such as tomorrow and fri. */
internal fun parseUserDate(text: String, snapshot: BackupSnapshot, language: UiLanguage): LocalDate? =
    SmartDateParser.parse(text, LocalDate.now(), snapshot.settings.dateFormat, uiLocale(language))

/** Parses "9:30", "21:30", "9pm", "9:30 pm" into minutes after midnight. */
internal fun parseTimeOfDay(text: String): Int? {
    val value = text.trim().lowercase(Locale.ROOT).replace(" ", "")
    if (value.isEmpty()) return null
    val match = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm|上午|下午)?$").matchEntire(value) ?: return null
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
    when (match.groupValues[3]) {
        "am", "上午" -> if (hour == 12) hour = 0
        "pm", "下午" -> if (hour < 12) hour += 12
    }
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

@Composable
internal fun SeriesScopeDialog(title: String, onDismiss: () -> Unit, onChoose: (SeriesEditScope) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(desktopText(title)) },
        text = { Text(desktopText("Apply this to only this one, or to this one and all later ones?")) },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(desktopText("Cancel")) }
                TextButton(onClick = { onChoose(SeriesEditScope.ONLY_THIS_OCCURRENCE) }) { Text(desktopText("Only this one")) }
            }
        },
        confirmButton = { Button(onClick = { onChoose(SeriesEditScope.THIS_AND_FUTURE_OCCURRENCES) }) { Text(desktopText("This and later ones")) } },
    )
}

/** A calendar button for a date field; picks a day and hands it back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerButton(initial: LocalDate?, onPicked: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Rounded.CalendarMonth, desktopText("Pick a date"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: LocalDate.now()).toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            dismissButton = { TextButton(onClick = { open = false }) { Text(desktopText("Cancel")) } },
            confirmButton = {
                Button(onClick = {
                    state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) }
                    open = false
                }) { Text(desktopText("OK")) }
            },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodoEditor(
    snapshot: BackupSnapshot,
    store: DesktopDataStore,
    target: TodoEditorTarget,
    onClose: () -> Unit,
    onDelete: (TodoEntity) -> Unit,
    modifier: Modifier,
) {
    val todo = (target as? TodoEditorTarget.Existing)?.let { t -> snapshot.todos.firstOrNull { it.id == t.id && it.deletedAt == null } }
    if (target is TodoEditorTarget.Existing && todo == null) {
        LaunchedEffect(target) { onClose() }
        return
    }
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val series = todo?.seriesId?.let { id -> snapshot.todoSeries.firstOrNull { it.id == id } }
    // Re-create the editor state when another todo is opened.
    val key = (todo?.id ?: -1L)
    fun format(day: Long) = UserFormatting.formatDate(LocalDate.ofEpochDay(day), snapshot.settings.dateFormat, locale)
    var description by remember(key) { mutableStateOf(todo?.description.orEmpty()) }
    var title by remember(key) { mutableStateOf(todo?.title?.takeIf { todo.description.isNotBlank() && it != com.ced2711.lifetracker.domain.model.deriveTodoTitle(todo.description) }.orEmpty()) }
    var dateText by remember(key) { mutableStateOf((todo?.deadlineEpochDay ?: (target as? TodoEditorTarget.New)?.epochDay)?.let(::format).orEmpty()) }
    var timeText by remember(key) { mutableStateOf(todo?.deadlineMinute?.let { "%d:%02d".format(it / 60, it % 60) }.orEmpty()) }
    var priority by remember(key) { mutableStateOf(todo?.priority ?: TodoPriority.NONE) }
    var categoryId by remember(key) { mutableStateOf(todo?.categoryId) }
    var tags by remember(key) { mutableStateOf(todo?.tagsCsv?.replace(",", ", ").orEmpty()) }
    val reminders = remember(key) {
        mutableStateListOf<Long>().apply {
            addAll(todo?.let { t -> snapshot.todoReminders.filter { it.todoId == t.id }.map { it.offsetMinutes } } ?: snapshot.settings.defaultReminderOffsetsMinutes)
        }
    }
    val subtasks = remember(key) {
        mutableStateListOf<String>().apply { addAll(todo?.let { t -> snapshot.subtasks.filter { it.todoId == t.id }.sortedBy { it.sortOrder }.map { it.description } }.orEmpty()) }
    }
    var newSubtask by remember(key) { mutableStateOf("") }
    var repeatUnit by remember(key) { mutableStateOf(series?.recurrenceUnit) }
    var repeatInterval by remember(key) { mutableStateOf((series?.intervalCount ?: 1).toString()) }
    var repeatEnd by remember(key) { mutableStateOf(series?.endEpochDay?.let(::format).orEmpty()) }
    var customReminder by remember(key) { mutableStateOf<String?>(null) }
    var customUnit by remember(key) { mutableStateOf(60L) }
    var askScope by remember(key) { mutableStateOf<TodoDraft?>(null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val descriptionFocus = remember { FocusRequester() }
    LaunchedEffect(key) { runCatching { descriptionFocus.requestFocus() } }

    val day = dateText.takeIf(String::isNotBlank)?.let { parseUserDate(it, snapshot, language)?.toEpochDay() }
    val minute = parseTimeOfDay(timeText)
    val endDay = repeatEnd.takeIf(String::isNotBlank)?.let { parseUserDate(it, snapshot, language)?.toEpochDay() }
    val interval = repeatInterval.toIntOrNull()
    val dateInvalid = dateText.isNotBlank() && day == null
    val timeInvalid = timeText.isNotBlank() && (minute == null || day == null)
    val repeatInvalid = repeatUnit != null && (day == null || interval == null || interval !in 1..10_000 || (repeatEnd.isNotBlank() && (endDay == null || endDay < day)))
    val canSave = description.isNotBlank() && !dateInvalid && !timeInvalid && !repeatInvalid

    fun buildDraft() = TodoDraft(
        id = todo?.id,
        title = title,
        description = description,
        categoryId = categoryId,
        deadlineEpochDay = day,
        deadlineMinute = minute.takeIf { day != null },
        priority = priority,
        tags = tags.split(','),
        reminderOffsetsMinutes = if (day == null) emptyList() else reminders.toList(),
        subtasks = subtasks.toList(),
        recurrence = repeatUnit?.let { RecurrenceRule(it, interval ?: 1, endDay) },
    )

    fun commit(draft: TodoDraft, editScope: SeriesEditScope) {
        scope.launch {
            try {
                if (store.saveTodo(draft, editScope) != null) onClose()
            } catch (failure: IllegalArgumentException) {
                error = failure.message
            }
        }
    }

    fun save() {
        if (!canSave) return
        val draft = buildDraft()
        // Changing the repeat rule of an occurrence asks whether later ones change too.
        if (todo?.seriesId != null) askScope = draft else commit(draft, SeriesEditScope.ONLY_THIS_OCCURRENCE)
    }

    EditorPane(
        title = desktopText(if (todo == null) "New todo" else "Edit todo"),
        onClose = onClose,
        modifier = modifier.editorKeys(onSave = ::save, onCancel = onClose),
        headerActions = {
            if (todo != null) {
                TextButton(onClick = { scope.launch { store.setTodoCompleted(todo.id, todo.completedAt == null) } }) {
                    Text(desktopText(if (todo.completedAt == null) "Mark as done" else "Mark as not done"))
                }
            }
        },
        footer = {
            if (todo != null) TextButton(onClick = { onDelete(todo) }) { Text(desktopText("Delete"), color = LifeTheme.colors.danger) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text(desktopText("Cancel")) }
            Button(enabled = canSave, onClick = ::save) { Text(desktopText("Save (Ctrl+S)")) }
        },
    ) {
        LifeTextField(
            description,
            { description = it },
            placeholder = desktopText("What needs to be done?"),
            minLines = 3,
            modifier = Modifier.fillMaxWidth().focusRequester(descriptionFocus),
        )
        LifeTextField(title, { title = it }, placeholder = desktopText("Title (optional)"), modifier = Modifier.fillMaxWidth())

        FieldLabel("Due")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            LifeTextField(
                dateText,
                { dateText = it },
                placeholder = desktopText("Date, e.g. tomorrow or fri"),
                isError = dateInvalid,
                trailing = { DatePickerButton(day?.let(LocalDate::ofEpochDay)) { dateText = format(it.toEpochDay()) } },
                modifier = Modifier.weight(1.5f),
            )
            LifeTextField(timeText, { timeText = it }, placeholder = desktopText("Time, e.g. 9:30"), isError = timeInvalid, modifier = Modifier.weight(1f))
        }
        day?.let {
            Text(formatDeadline(it, minute, snapshot, language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val today = LocalDate.now()
            listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to today.plusWeeks(1)).forEach { (label, date) ->
                Pill(desktopText(label), day == date.toEpochDay(), { dateText = format(date.toEpochDay()) }, exclusive = true)
            }
            Pill(desktopText("No date"), dateText.isBlank(), { dateText = ""; timeText = ""; repeatUnit = null }, exclusive = true)
        }

        if (day != null) {
            FieldLabel("Reminders")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReminderOffsetPreset.entries.forEach { preset ->
                    val on = preset.minutesBeforeDue in reminders
                    Pill(desktopText(reminderLabel(preset)), on, { if (on) reminders.remove(preset.minutesBeforeDue) else reminders.add(preset.minutesBeforeDue) })
                }
                reminders.filter { offset -> ReminderOffsetPreset.entries.none { it.minutesBeforeDue == offset } }.forEach { custom ->
                    Pill(desktopReminderMinutes(custom, language), true, { reminders.remove(custom) }, leading = { Icon(Icons.Rounded.Close, null, Modifier.size(14.dp)) })
                }
                if (customReminder == null) {
                    Pill(desktopText("Custom…"), false, { customReminder = "" }, leading = { Icon(Icons.Rounded.Add, null, Modifier.size(14.dp)) })
                }
            }
            customReminder?.let { value ->
                val amount = value.toLongOrNull()
                val minutes = amount?.let { it * customUnit }
                val valid = minutes != null && minutes in 1..527_040 && minutes !in reminders
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    LifeTextField(value, { customReminder = it.filter(Char::isDigit).take(4) }, placeholder = "2", modifier = Modifier.width(72.dp))
                    Segmented(listOf(60L, 1_440L, 10_080L), customUnit, { customUnit = it }, { desktopText(when (it) { 60L -> "Hours"; 1_440L -> "Days"; else -> "Weeks" }) })
                    Text(desktopText("before"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { customReminder = null }) { Text(desktopText("Cancel")) }
                    Button(enabled = valid, onClick = { minutes?.let(reminders::add); customReminder = null }) { Text(desktopText("Add")) }
                }
            }

            FieldLabel("Repeat")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(desktopText("Never"), repeatUnit == null, { repeatUnit = null }, exclusive = true)
                RecurrenceUnit.entries.forEach { unit -> Pill(desktopText(recurrenceLabel(unit)), repeatUnit == unit, { repeatUnit = unit }, exclusive = true) }
            }
            if (repeatUnit != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(desktopText("Every"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LifeTextField(repeatInterval, { repeatInterval = it.filter(Char::isDigit).take(5) }, isError = interval == null || interval < 1, modifier = Modifier.width(72.dp))
                    LifeTextField(
                        repeatEnd,
                        { repeatEnd = it },
                        placeholder = desktopText("Until (optional)"),
                        isError = repeatEnd.isNotBlank() && (endDay == null || endDay < day),
                        trailing = { DatePickerButton(endDay?.let(LocalDate::ofEpochDay)) { repeatEnd = format(it.toEpochDay()) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        FieldLabel("Priority")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TodoPriority.entries.forEach { item ->
                Pill(
                    desktopText(priorityLabel(item)),
                    item == priority,
                    { priority = item },
                    exclusive = true,
                    leading = priorityColor(item)?.let { color -> { com.ced2711.lifetracker.ui.design.Dot(color) } },
                )
            }
        }

        FieldLabel("Category")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(desktopText("None"), categoryId == null, { categoryId = null }, exclusive = true)
            snapshot.categories.sortedBy { categoryPath(it.id, snapshot).lowercase() }.forEach { category ->
                Pill(categoryPath(category.id, snapshot), categoryId == category.id, { categoryId = category.id }, exclusive = true)
            }
        }

        FieldLabel("Tags")
        LifeTextField(tags, { tags = it }, placeholder = desktopText("Comma separated, e.g. home, errands"), modifier = Modifier.fillMaxWidth())
        // Existing tags that match what is being typed, to keep tags consistent.
        val typing = tags.substringAfterLast(',').trim().removePrefix("#")
        val chosen = parseTags(tags).map { it.lowercase() }.toSet()
        val suggestions = snapshot.todos.flatMap { parseTags(it.tagsCsv) }.distinctBy { it.lowercase() }
            .filter { it.lowercase() !in chosen && (typing.isEmpty() || it.startsWith(typing, ignoreCase = true)) }
            .sortedBy { it.lowercase() }.take(8)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { suggestion ->
                    Pill("#$suggestion", false, {
                        val kept = tags.split(',').dropLast(1).map(String::trim).filter(String::isNotEmpty)
                        tags = (kept + suggestion).joinToString(", ") + ", "
                    })
                }
            }
        }

        FieldLabel("Subtasks")
        subtasks.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LifeTextField(text, { subtasks[index] = it }, modifier = Modifier.weight(1f))
                IconButton(enabled = index > 0, onClick = { subtasks.add(index - 1, subtasks.removeAt(index)) }) { Icon(Icons.Rounded.KeyboardArrowUp, desktopText("Move up")) }
                IconButton(enabled = index < subtasks.lastIndex, onClick = { subtasks.add(index + 1, subtasks.removeAt(index)) }) { Icon(Icons.Rounded.KeyboardArrowDown, desktopText("Move down")) }
                IconButton(onClick = { subtasks.removeAt(index) }) { Icon(Icons.Rounded.Close, desktopText("Remove")) }
            }
        }
        LifeTextField(
            newSubtask,
            { newSubtask = it },
            placeholder = desktopText("Add a subtask and press Enter"),
            leadingIcon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth().onEnter {
                if (newSubtask.isNotBlank()) {
                    subtasks.add(newSubtask.trim())
                    newSubtask = ""
                }
            },
        )

        if (todo != null) {
            FieldLabel("Attachments") {
                TextButton(onClick = { chooseAndAttach(scope, store, AttachmentOwnerType.TODO, todo.id) }) {
                    Icon(Icons.Rounded.AttachFile, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(desktopText("Attach"))
                }
            }
            AttachmentList(snapshot, AttachmentOwnerType.TODO, todo.id, store)
        } else {
            Text(desktopText("Files can be attached after the first save."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(desktopText(it), color = LifeTheme.colors.danger) }
        Spacer(Modifier.height(Space.lg))
    }

    askScope?.let { draft ->
        SeriesScopeDialog(
            title = "Edit repeating todo",
            onDismiss = { askScope = null },
            onChoose = { chosen ->
                askScope = null
                commit(draft, chosen)
            },
        )
    }
}

/** The todo editor in its own dialog, for opening a todo from another page such as the calendar. */
@Composable
internal fun TodoEditorWindow(snapshot: BackupSnapshot, store: DesktopDataStore, todoId: Long?, initialDay: Long? = null, onClose: () -> Unit) {
    val scope = rememberSafeCoroutineScope()
    var deleteScopeFor by remember { mutableStateOf<TodoEntity?>(null) }
    androidx.compose.ui.window.DialogWindow(
        onCloseRequest = onClose,
        title = desktopText(if (todoId == null) "New todo" else "Edit todo"),
        state = androidx.compose.ui.window.rememberDialogState(size = androidx.compose.ui.unit.DpSize(560.dp, 780.dp)),
    ) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            TodoEditor(
                snapshot = snapshot,
                store = store,
                target = todoId?.let { TodoEditorTarget.Existing(it) } ?: TodoEditorTarget.New(initialDay),
                onClose = onClose,
                onDelete = { todo ->
                    if (todo.seriesId != null) deleteScopeFor = todo else {
                        scope.launch { store.deleteTodoWithUndo(todo.id) }
                        onClose()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            deleteScopeFor?.let { todo ->
                SeriesScopeDialog("Delete repeating todo", { deleteScopeFor = null }) { chosen ->
                    deleteScopeFor = null
                    scope.launch { store.deleteTodoWithUndo(todo.id, chosen) }
                    onClose()
                }
            }
        }
    }
}

internal fun reminderLabel(preset: ReminderOffsetPreset): String = when (preset) {
    ReminderOffsetPreset.AT_DUE -> "At due time"
    ReminderOffsetPreset.ONE_HOUR -> "1 hour before"
    ReminderOffsetPreset.ONE_DAY -> "1 day before"
    ReminderOffsetPreset.THREE_DAYS -> "3 days before"
    ReminderOffsetPreset.ONE_WEEK -> "1 week before"
}

internal fun recurrenceLabel(unit: RecurrenceUnit): String = when (unit) {
    RecurrenceUnit.DAY -> "Daily"
    RecurrenceUnit.WEEK -> "Weekly"
    RecurrenceUnit.MONTH -> "Monthly"
    RecurrenceUnit.YEAR -> "Yearly"
}
