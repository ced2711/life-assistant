package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
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
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.uiLocale
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** What the editor pane is showing. */
private sealed interface TodoEditorTarget {
    data object New : TodoEditorTarget
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
    var newCategory by remember { mutableStateOf(false) }
    var completeWithSubtasks by remember { mutableStateOf<TodoEntity?>(null) }
    var deleteScopeFor by remember { mutableStateOf<TodoEntity?>(null) }
    val searchFocus = remember { FocusRequester() }
    val quickAddFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val today = LocalDate.now().toEpochDay()

    RegisterPageShortcuts(
        onNew = { editor = TodoEditorTarget.New },
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
    val active = matching.filter { it.completedAt == null && filters.view.matches(it, today) }.sortedWith(todoComparator(filters.sortBy))
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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val editorTarget = editor
            val editorWidth = 460.dp
            val listMinimum = 520.dp
            // The filter column shows whenever list, filters and an open editor still fit.
            val filterColumn = maxWidth - (if (editorTarget != null) editorWidth else 0.dp) >= listMinimum + 240.dp
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
                        modifier = Modifier.width(240.dp).fillMaxHeight(),
                    )
                    VerticalDivider()
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    PageHeader(if (filters.view == TodoView.ALL) "Todo" else filters.view.label, desktopActiveTasks(active.size, language))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            quickAdd,
                            { quickAdd = it },
                            placeholder = { Text(desktopText("Add a todo and press Enter")) },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f).focusRequester(quickAddFocus).onEnter(::addQuick),
                        )
                        OutlinedTextField(
                            query,
                            { query = it },
                            placeholder = { Text(desktopText("Search (Ctrl+F)")) },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).focusRequester(searchFocus),
                        )
                        if (!filterColumn) IconButton(onClick = { showFilterRow = !showFilterRow }) { Icon(Icons.Default.FilterAlt, desktopText("Filters")) }
                    }
                    if (!filterColumn && showFilterRow) TodoFilterRow(snapshot, live, filters) { newCategory = true }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)
                            .focusRequester(listFocus)
                            .listKeys(
                                onUp = { moveSelection(-1) },
                                onDown = { moveSelection(1) },
                                onOpen = { selectedId?.let { editor = TodoEditorTarget.Existing(it) } },
                                onToggle = { visible.firstOrNull { it.id == selectedId }?.let(::toggle) },
                                onDelete = { visible.firstOrNull { it.id == selectedId }?.let(::requestDelete) },
                            )
                            .focusable(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (active.isEmpty()) {
                            item { Text(desktopText("Nothing to do here. Add a todo above or press Ctrl+N."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp)) }
                        }
                        val grouped = filters.sortBy == DesktopTodoSort.DEADLINE
                        active.forEachIndexed { index, todo ->
                            val group = deadlineGroup(todo, today)
                            if (grouped && (index == 0 || deadlineGroup(active[index - 1], today) != group)) {
                                item(key = "group-$group") {
                                    Text(
                                        desktopText(group),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (group == "Overdue") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = if (index == 0) 4.dp else 14.dp, bottom = 2.dp),
                                    )
                                }
                            }
                            item(key = todo.id) {
                                TodoRow(
                                    todo = todo,
                                    snapshot = snapshot,
                                    store = store,
                                    selected = todo.id == selectedId || editor == TodoEditorTarget.Existing(todo.id),
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
                        item {
                            TextButton(onClick = { filters.showCompleted = !filters.showCompleted }) {
                                Icon(if (filters.showCompleted) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                Text(desktopCompletedCount(completed.size, language))
                            }
                        }
                        if (filters.showCompleted) items(completed, key = { it.id }) { todo ->
                            TodoRow(
                                todo = todo,
                                snapshot = snapshot,
                                store = store,
                                selected = todo.id == selectedId,
                                onClick = { selectedId = todo.id; editor = TodoEditorTarget.Existing(todo.id) },
                                onToggle = { toggle(todo) },
                                onDelete = { requestDelete(todo) },
                            )
                        }
                        item { Spacer(Modifier.padding(24.dp)) }
                    }
                }
                if (editorTarget != null) {
                    VerticalDivider()
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
    if (newCategory) {
        SimpleNameDialog(desktopText("New category"), { newCategory = false }) { name ->
            scope.launch { store.addCategory(name, filters.singleCategory) }
            newCategory = false
        }
    }
}

/** Left column of the todo page: quick views, categories as a tree, priority, tags and sort. */
@Composable
private fun TodoFilterColumn(
    snapshot: BackupSnapshot,
    live: List<TodoEntity>,
    filters: TodoFilters,
    today: Long,
    onNewCategory: () -> Unit,
    modifier: Modifier,
) {
    val open = live.filter { it.completedAt == null }
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TodoView.entries.forEach { view ->
            FilterListEntry(
                label = desktopText(view.label),
                count = open.count { view.matches(it, today) },
                selected = filters.view == view,
                emphasize = view == TodoView.OVERDUE,
            ) { filters.view = view }
        }
        FilterSectionTitle("Categories") {
            IconButton(onClick = onNewCategory, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, desktopText("New category"), Modifier.size(18.dp)) }
        }
        FilterListEntry(desktopText("All categories"), null, filters.allCategories) { filters.selectAllCategories() }
        val children = snapshot.categories.groupBy { it.parentId }
        fun walk(parent: Long?, depth: Int, entries: MutableList<Pair<com.ced2711.lifetracker.data.local.CategoryEntity, Int>>) {
            children[parent].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() })).forEach { category ->
                entries += category to depth
                walk(category.id, depth + 1, entries)
            }
        }
        val tree = mutableListOf<Pair<com.ced2711.lifetracker.data.local.CategoryEntity, Int>>().also { walk(null, 0, it) }
        tree.forEach { (category, depth) ->
            val ids = descendantCategoryIds(snapshot.categories, setOf(category.id))
            FilterListEntry(
                label = category.name,
                count = open.count { it.categoryId in ids },
                selected = !filters.allCategories && category.id in filters.selectedCategories,
                indent = depth,
            ) { filters.toggleCategory(category.id) }
        }
        FilterListEntry(desktopText("Uncategorized"), open.count { it.categoryId == null }, !filters.allCategories && filters.includeUncategorized) { filters.toggleUncategorized() }

        FilterSectionTitle("Priority")
        FilterListEntry(desktopText("Any priority"), null, filters.priority == null) { filters.priority = null }
        listOf(TodoPriority.URGENT, TodoPriority.HIGH, TodoPriority.MEDIUM, TodoPriority.LOW).forEach { priority ->
            FilterListEntry(desktopText(priorityLabel(priority)), open.count { it.priority == priority }, filters.priority == priority, dot = priorityColor(priority)) {
                filters.priority = if (filters.priority == priority) null else priority
            }
        }

        val tags = live.flatMap { parseTags(it.tagsCsv) }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        if (tags.isNotEmpty()) {
            FilterSectionTitle("Tags")
            tags.forEach { tag ->
                FilterListEntry("#$tag", open.count { todo -> parseTags(todo.tagsCsv).any { it.equals(tag, true) } }, filters.tag == tag) {
                    filters.tag = if (filters.tag == tag) null else tag
                }
            }
        }

        FilterSectionTitle("Sort")
        DesktopTodoSort.entries.forEach { sort ->
            FilterListEntry(desktopText(desktopSortLabel(sort)), null, filters.sortBy == sort) { filters.sortBy = sort }
        }
    }
}

@Composable
private fun FilterSectionTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(desktopText(title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun FilterListEntry(
    label: String,
    count: Int?,
    selected: Boolean,
    indent: Int = 0,
    emphasize: Boolean = false,
    dot: Color? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 10.dp + (indent * 14).dp, end = 10.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Surface(shape = RoundedCornerShape(50), color = dot) { Spacer(Modifier.size(8.dp)) }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (count != null && count > 0) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = if (emphasize) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The same filters as chips, for windows too narrow for the filter column. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodoFilterRow(snapshot: BackupSnapshot, live: List<TodoEntity>, filters: TodoFilters, onNewCategory: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TodoView.entries.forEach { view -> FilterChipSimple(desktopText(view.label), filters.view == view) { filters.view = view } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(desktopText("Sort"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
            DesktopTodoSort.entries.forEach { item -> FilterChipSimple(desktopText(desktopSortLabel(item)), filters.sortBy == item) { filters.sortBy = item } }
            Spacer(Modifier.width(12.dp))
            Text(desktopText("Priority"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
            FilterChipSimple(desktopText("Any"), filters.priority == null) { filters.priority = null }
            listOf(TodoPriority.URGENT, TodoPriority.HIGH, TodoPriority.MEDIUM, TodoPriority.LOW).forEach { item ->
                FilterChipSimple(desktopText(priorityLabel(item)), filters.priority == item) { filters.priority = if (filters.priority == item) null else item }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChipSimple(desktopText("All"), filters.allCategories) { filters.selectAllCategories() }
            FilterChipSimple(desktopText("Uncategorized"), !filters.allCategories && filters.includeUncategorized) { filters.toggleUncategorized() }
            snapshot.categories.forEach { category ->
                FilterChipSimple(categoryPath(category.id, snapshot), !filters.allCategories && category.id in filters.selectedCategories) { filters.toggleCategory(category.id) }
            }
            TextButton(onClick = onNewCategory) { Icon(Icons.Default.Add, null); Text(desktopText("Category")) }
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

@Composable
private fun priorityColor(priority: TodoPriority): Color = when (priority) {
    TodoPriority.URGENT -> MaterialTheme.colorScheme.error
    TodoPriority.HIGH -> Color(0xFFFFB673)
    TodoPriority.MEDIUM -> MaterialTheme.colorScheme.primary
    TodoPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    TodoPriority.NONE -> Color.Transparent
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodoRow(
    todo: TodoEntity,
    snapshot: BackupSnapshot,
    store: DesktopDataStore,
    selected: Boolean,
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
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(done, { onToggle() })
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (todo.priority != TodoPriority.NONE) {
                            Surface(shape = RoundedCornerShape(50), color = priorityColor(todo.priority)) { Spacer(Modifier.size(8.dp)) }
                        }
                        Text(
                            todo.title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                        )
                        if (todo.seriesId != null) Icon(Icons.Default.Repeat, desktopText("Repeats"), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        todo.deadlineEpochDay?.let { day ->
                            val overdue = !done && (day < LocalDate.now().toEpochDay() ||
                                (day == LocalDate.now().toEpochDay() && (todo.deadlineMinute ?: 1_440) < LocalTime.now().toSecondOfDay() / 60))
                            Text(
                                formatDeadline(day, todo.deadlineMinute, snapshot, language),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (subtasks.isNotEmpty()) Text("☑ ${subtasks.count { it.isCompleted }}/${subtasks.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        todo.categoryId?.let { Text(categoryPath(it, snapshot), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        parseTags(todo.tagsCsv).forEach { Text("#$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        if (attachments > 0) Text("📎 $attachments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onMoveUp != null) IconButton(onClick = onMoveUp) { Icon(Icons.Default.KeyboardArrowUp, desktopText("Move up")) }
                if (onMoveDown != null) IconButton(onClick = onMoveDown) { Icon(Icons.Default.KeyboardArrowDown, desktopText("Move down")) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, desktopText("Delete")) }
            }
            if (subtasks.isNotEmpty() && !done) {
                Column(Modifier.padding(start = 40.dp)) {
                    subtasks.forEach { subtask ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(subtask.isCompleted, { checked -> scope.launch { store.setSubtaskCompleted(subtask.id, checked) } }, Modifier.size(32.dp))
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
    var description by remember(key) { mutableStateOf(todo?.description.orEmpty()) }
    var title by remember(key) { mutableStateOf(todo?.title?.takeIf { todo.description.isNotBlank() && it != com.ced2711.lifetracker.domain.model.deriveTodoTitle(todo.description) }.orEmpty()) }
    var dateText by remember(key) { mutableStateOf(todo?.deadlineEpochDay?.let { UserFormatting.formatDate(LocalDate.ofEpochDay(it), snapshot.settings.dateFormat, locale) }.orEmpty()) }
    var timeText by remember(key) {
        mutableStateOf(todo?.deadlineMinute?.let { "%d:%02d".format(it / 60, it % 60) }.orEmpty())
    }
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
    var repeatEnd by remember(key) { mutableStateOf(series?.endEpochDay?.let { UserFormatting.formatDate(LocalDate.ofEpochDay(it), snapshot.settings.dateFormat, locale) }.orEmpty()) }
    var askScope by remember(key) { mutableStateOf<TodoDraft?>(null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val descriptionFocus = remember { FocusRequester() }
    LaunchedEffect(key) { runCatching { descriptionFocus.requestFocus() } }

    val day = dateText.takeIf(String::isNotBlank)?.let { SmartDateParser.parse(it, LocalDate.now())?.toEpochDay() }
    val minute = parseTimeOfDay(timeText)
    val endDay = repeatEnd.takeIf(String::isNotBlank)?.let { SmartDateParser.parse(it, LocalDate.now())?.toEpochDay() }
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

    Column(modifier.background(MaterialTheme.colorScheme.surface).editorKeys(onSave = ::save, onCancel = onClose)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(desktopText(if (todo == null) "New todo" else "Edit todo"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, desktopText("Close (Esc)")) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(description, { description = it }, label = { Text(desktopText("Description")) }, modifier = Modifier.fillMaxWidth().focusRequester(descriptionFocus), minLines = 3)
            OutlinedTextField(title, { title = it }, label = { Text(desktopText("Title (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text(desktopText("Due"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    dateText, { dateText = it },
                    label = { Text(desktopText("Date")) },
                    placeholder = { Text(desktopText("e.g. tomorrow, fri, 10/3")) },
                    isError = dateInvalid, singleLine = true, modifier = Modifier.weight(1.4f),
                    supportingText = { day?.let { Text(formatDeadline(it, null, snapshot, language)) } },
                )
                OutlinedTextField(
                    timeText, { timeText = it },
                    label = { Text(desktopText("Time")) },
                    placeholder = { Text("9:30") },
                    isError = timeInvalid, singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val today = LocalDate.now()
                listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to today.plusWeeks(1)).forEach { (label, date) ->
                    FilterChipSimple(desktopText(label), day == date.toEpochDay()) {
                        dateText = UserFormatting.formatDate(date, snapshot.settings.dateFormat, locale)
                    }
                }
                FilterChipSimple(desktopText("No date"), dateText.isBlank()) { dateText = ""; timeText = ""; repeatUnit = null }
            }

            if (day != null) {
                Text(desktopText("Reminders"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderOffsetPreset.entries.forEach { preset ->
                        val on = preset.minutesBeforeDue in reminders
                        FilterChipSimple(desktopText(reminderLabel(preset)), on) {
                            if (on) reminders.remove(preset.minutesBeforeDue) else reminders.add(preset.minutesBeforeDue)
                        }
                    }
                    reminders.filter { offset -> ReminderOffsetPreset.entries.none { it.minutesBeforeDue == offset } }.forEach { custom ->
                        FilterChipSimple(desktopReminderMinutes(custom, language), true) { reminders.remove(custom) }
                    }
                }

                Text(desktopText("Repeat"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChipSimple(desktopText("Never"), repeatUnit == null) { repeatUnit = null }
                    RecurrenceUnit.entries.forEach { unit ->
                        FilterChipSimple(desktopText(recurrenceLabel(unit)), repeatUnit == unit) { repeatUnit = unit }
                    }
                }
                if (repeatUnit != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(repeatInterval, { repeatInterval = it.filter(Char::isDigit).take(5) }, label = { Text(desktopText("Every")) }, singleLine = true, modifier = Modifier.weight(1f), isError = interval == null || interval < 1)
                    OutlinedTextField(repeatEnd, { repeatEnd = it }, label = { Text(desktopText("Until (optional)")) }, singleLine = true, modifier = Modifier.weight(2f), isError = repeatEnd.isNotBlank() && (endDay == null || endDay < day))
                }
            }

            Text(desktopText("Priority"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TodoPriority.entries.forEach { item -> FilterChipSimple(desktopText(priorityLabel(item)), item == priority) { priority = item } }
            }

            Text(desktopText("Category"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChipSimple(desktopText("None"), categoryId == null) { categoryId = null }
                snapshot.categories.forEach { category -> FilterChipSimple(categoryPath(category.id, snapshot), categoryId == category.id) { categoryId = category.id } }
            }
            OutlinedTextField(tags, { tags = it }, label = { Text(desktopText("Tags, comma separated (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text(desktopText("Subtasks"), style = MaterialTheme.typography.labelLarge)
            subtasks.forEachIndexed { index, text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { subtasks[index] = it }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(enabled = index > 0, onClick = { subtasks.add(index - 1, subtasks.removeAt(index)) }) { Icon(Icons.Default.KeyboardArrowUp, desktopText("Move up")) }
                    IconButton(enabled = index < subtasks.lastIndex, onClick = { subtasks.add(index + 1, subtasks.removeAt(index)) }) { Icon(Icons.Default.KeyboardArrowDown, desktopText("Move down")) }
                    IconButton(onClick = { subtasks.removeAt(index) }) { Icon(Icons.Default.Close, desktopText("Remove")) }
                }
            }
            OutlinedTextField(
                newSubtask, { newSubtask = it },
                placeholder = { Text(desktopText("Add a subtask and press Enter")) },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().onEnter {
                    if (newSubtask.isNotBlank()) {
                        subtasks.add(newSubtask.trim())
                        newSubtask = ""
                    }
                },
            )

            if (todo != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(desktopText("Attachments"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    IconButton({ chooseAndAttach(scope, store, AttachmentOwnerType.TODO, todo.id) }) { Icon(Icons.Default.AttachFile, desktopText("Attach")) }
                }
                AttachmentList(snapshot, AttachmentOwnerType.TODO, todo.id, store)
            }
            error?.let { Text(desktopText(it), color = MaterialTheme.colorScheme.error) }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (todo != null) TextButton(onClick = { onDelete(todo) }) { Text(desktopText("Delete"), color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text(desktopText("Cancel")) }
            Button(enabled = canSave, onClick = ::save) { Text(desktopText("Save (Ctrl+S)")) }
        }
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
internal fun TodoEditorWindow(snapshot: BackupSnapshot, store: DesktopDataStore, todoId: Long?, onClose: () -> Unit) {
    val scope = rememberSafeCoroutineScope()
    var deleteScopeFor by remember { mutableStateOf<TodoEntity?>(null) }
    androidx.compose.ui.window.DialogWindow(
        onCloseRequest = onClose,
        title = desktopText(if (todoId == null) "New todo" else "Edit todo"),
        state = androidx.compose.ui.window.rememberDialogState(size = androidx.compose.ui.unit.DpSize(560.dp, 760.dp)),
    ) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme, typography = MaterialTheme.typography) {
            TodoEditor(
                snapshot = snapshot,
                store = store,
                target = todoId?.let { TodoEditorTarget.Existing(it) } ?: TodoEditorTarget.New,
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
