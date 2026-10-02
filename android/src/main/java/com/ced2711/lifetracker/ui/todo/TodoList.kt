package com.ced2711.lifetracker.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.date.SmartDateParser
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.collectDistinctTodoTags
import com.ced2711.lifetracker.ui.components.DatePickerButton
import com.ced2711.lifetracker.ui.components.SearchField
import com.ced2711.lifetracker.ui.design.CheckCircle
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.ProgressLine
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.priorityColor
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

/** What is typed into the quick add line and its optional fields. */
internal data class TodoQuickAdd(
    val description: String = "",
    val deadlineText: String = "",
    val priority: TodoPriority = TodoPriority.NONE,
    val categoryId: Long? = null,
    val tagsText: String = "",
    val inFlight: Boolean = false,
    /** English message of what is wrong, or null. */
    val error: String? = null,
)

internal val TodoQuickAddSaver = listSaver<TodoQuickAdd, String>(
    save = { listOf(it.description, it.deadlineText, it.priority.name, it.categoryId?.toString().orEmpty(), it.tagsText, it.error.orEmpty()) },
    restore = { saved ->
        TodoQuickAdd(
            description = saved[0],
            deadlineText = saved[1],
            priority = TodoPriority.entries.firstOrNull { it.name == saved[2] } ?: TodoPriority.NONE,
            categoryId = saved[3].toLongOrNull(),
            tagsText = saved[4],
            error = saved[5].ifEmpty { null },
        )
    },
)

/** The sheets the list can open by itself. */
internal enum class TodoSheet { FILTERS, CATEGORIES, TAGS }

/**
 * The Todo page without its data sources: progress of the day, quick add, quick views, the list
 * grouped by when things are due, and the completed todos. [editorPane] is the open todo on wide
 * screens, shown to the right of the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodoContent(
    active: List<TodoEntity>,
    completed: List<TodoEntity>,
    subtasksByTodo: Map<Long, List<SubtaskEntity>>,
    categories: List<CategoryEntity>,
    seriesTags: List<String>,
    settings: AppSettings,
    filter: TodoFilter,
    onFilterChange: (TodoFilter) -> Unit,
    quick: TodoQuickAdd,
    onQuickChange: (TodoQuickAdd) -> Unit,
    onQuickAdd: () -> Unit,
    onOpen: (TodoEntity) -> Unit,
    onNew: () -> Unit,
    onToggle: (todo: TodoEntity, done: Boolean, withSubtasks: Boolean) -> Unit,
    onToggleSubtask: (SubtaskEntity, Boolean) -> Unit,
    onDelete: (TodoEntity) -> Unit,
    onMove: (todoId: Long, neighbourId: Long) -> Unit,
    onAddCategory: (String, Long?) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onRenameTag: (String, String) -> Unit,
    onDeleteTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    selectedTodoId: Long? = null,
    quickAddFocus: FocusRequester = remember { FocusRequester() },
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    initialSheet: TodoSheet? = null,
    editorPane: (@Composable () -> Unit)? = null,
) {
    val language = LocalUiLanguage.current
    val today = LocalDate.now().toEpochDay()
    var sheet by rememberSaveable { mutableStateOf(initialSheet) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable(stateSaver = listSaver<Set<Long>, Long>({ it.toList() }, { it.toSet() })) { mutableStateOf(emptySet()) }
    var completing by remember { mutableStateOf<TodoEntity?>(null) }
    val searchFocus = remember { FocusRequester() }

    val categoryParents = remember(categories) { categories.associate { it.id to it.parentId } }
    val paths = remember(categories) { categoryPaths(categories) }
    val tags = remember(active, completed, seriesTags) {
        collectDistinctTodoTags((active + completed).map(TodoEntity::tagsCsv) + seriesTags)
    }
    val model = remember(active, completed, filter, categoryParents, today) {
        buildTodoList(active, completed, filter, categoryParents, today)
    }
    val momentum = remember(active, completed, today) { dailyTodoMomentum(active, completed, today) }

    fun toggle(todo: TodoEntity, done: Boolean) {
        if (done && subtasksByTodo[todo.id].orEmpty().any { !it.isCompleted }) completing = todo else onToggle(todo, done, false)
    }

    @Composable
    fun row(todo: TodoEntity, showDate: Boolean, index: Int = -1) {
        val reorder = filter.sort == TodoSort.CUSTOM && todo.completedAt == null && index >= 0
        TodoRow(
            todo = todo,
            subtasks = subtasksByTodo[todo.id].orEmpty(),
            categoryPath = todo.categoryId?.let(paths::get),
            settings = settings,
            showDate = showDate,
            expanded = todo.id in expanded,
            selected = todo.id == selectedTodoId,
            onExpand = { expanded = if (todo.id in expanded) expanded - todo.id else expanded + todo.id },
            onClick = { onOpen(todo) },
            onToggle = { done -> toggle(todo, done) },
            onToggleSubtask = onToggleSubtask,
            onDelete = { onDelete(todo) },
            onMoveUp = if (reorder && index > 0) ({ onMove(todo.id, model.active[index - 1].id) }) else null,
            onMoveDown = if (reorder && index < model.active.lastIndex) ({ onMove(todo.id, model.active[index + 1].id) }) else null,
            showReorder = reorder,
        )
    }

    val list: @Composable (Modifier) -> Unit = { listModifier ->
        LazyColumn(
            modifier = listModifier,
            state = listState,
            contentPadding = PaddingValues(top = Space.xs, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Text(localizedText(momentum.summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (momentum.total > 0) ProgressLine(momentum.progress)
                    LifeTextField(
                        value = quick.description,
                        onValueChange = { onQuickChange(quick.copy(description = it, error = quick.error.takeIf { _ -> it.isBlank() })) },
                        placeholder = localizedText("Add a todo"),
                        leadingIcon = Icons.Rounded.Add,
                        enabled = !quick.inFlight,
                        isError = quick.error == "Description is required",
                        modifier = Modifier.fillMaxWidth().focusRequester(quickAddFocus),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onQuickAdd() }),
                        trailing = if (quick.description.isNotBlank()) {
                            {
                                IconButton(onClick = onQuickAdd, enabled = !quick.inFlight, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.ArrowUpward, localizedText("Add todo"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else {
                            null
                        },
                    )
                    if (settings.todoQuickAddFields.isNotEmpty()) {
                        QuickAddFields(quick, onQuickChange, settings, categories, paths)
                    }
                    quick.error?.let { Text(localizedText(it), style = MaterialTheme.typography.bodySmall, color = LifeTheme.colors.danger) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            items(TodoView.entries) { view ->
                                Pill(
                                    text = localizedText(view.label),
                                    selected = filter.view == view,
                                    onClick = { onFilterChange(filter.copy(view = view)) },
                                    count = model.viewCounts[view]?.takeIf { it > 0 && view != TodoView.ALL },
                                    exclusive = true,
                                )
                            }
                        }
                        IconButton(onClick = { searchOpen = !searchOpen || filter.search.isNotEmpty() }) {
                            Icon(Icons.Rounded.Search, localizedText("Search"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { sheet = TodoSheet.FILTERS }) {
                            BadgedBox(badge = { if (filter.changedInSheet > 0) Badge { Text(filter.changedInSheet.toString()) } }) {
                                Icon(Icons.Rounded.Tune, localizedText("Filter and sort"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (searchOpen || filter.search.isNotEmpty()) {
                        SearchField(
                            value = filter.search,
                            onValueChange = { onFilterChange(filter.copy(search = it)) },
                            placeholder = localizedText("Search todos"),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        )
                        LaunchedEffect(searchOpen) { if (searchOpen && filter.search.isEmpty()) runCatching { searchFocus.requestFocus() } }
                    }
                    if (filter.changedInSheet > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                filterSummary(filter, paths, language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onFilterChange(filter.cleared().copy(search = filter.search)) }) { Text(localizedText("Clear")) }
                        }
                    }
                }
            }

            if (model.active.isEmpty()) {
                item(key = "empty") {
                    val nothingAtAll = active.isEmpty()
                    EmptyState(
                        title = localizedText(if (nothingAtAll) "Nothing to do yet" else "No todos here"),
                        icon = Icons.Rounded.Checklist,
                        body = localizedText(if (nothingAtAll) "Type above and press Enter to add your first todo." else "Try another view or clear the filters."),
                        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xl),
                    )
                }
            }
            var position = 0
            model.groups.forEach { group ->
                if (group.label != null) {
                    item(key = "group-${group.label}") {
                        SectionLabel(
                            localizedText(group.label),
                            modifier = Modifier.padding(horizontal = Space.lg),
                            count = group.todos.size,
                            color = if (group.label == "Overdue") LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val showDate = group.label != "Today" && group.label != "Tomorrow"
                group.todos.forEach { todo ->
                    val index = position++
                    item(key = "todo-${todo.id}") { row(todo, showDate, index) }
                }
            }

            if (model.completed.isNotEmpty()) {
                item(key = "completed-header") {
                    SectionLabel(
                        localizedText("Completed"),
                        modifier = Modifier.padding(start = Space.lg, end = Space.sm),
                        count = model.completed.size,
                        trailing = {
                            TextButton(onClick = { onFilterChange(filter.copy(showCompleted = !filter.showCompleted)) }) {
                                Text(localizedText(if (filter.showCompleted) "Hide" else "Show"))
                            }
                        },
                    )
                }
                if (filter.showCompleted) {
                    model.completed.forEach { todo -> item(key = "done-${todo.id}") { row(todo, showDate = true) } }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!(isWide && editorPane != null)) {
                FloatingActionButton(onClick = onNew, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Icon(Icons.Rounded.Add, localizedText("New task"))
                }
            }
        },
    ) { padding ->
        if (isWide && editorPane != null) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                list(Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight().padding(end = Space.lg, bottom = Space.lg, top = Space.xs)) { editorPane() }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                list(Modifier.widthIn(max = 760.dp).fillMaxSize())
            }
        }
    }

    when (sheet) {
        TodoSheet.FILTERS -> TodoFilterSheet(
            filter = filter,
            onFilterChange = onFilterChange,
            categories = categories,
            tags = tags,
            onManageCategories = { sheet = TodoSheet.CATEGORIES },
            onManageTags = { sheet = TodoSheet.TAGS },
            onClose = { sheet = null },
        )
        TodoSheet.CATEGORIES -> CategoryManagerSheet(
            categories = categories,
            onClose = { sheet = TodoSheet.FILTERS },
            onAdd = onAddCategory,
            onDelete = { id ->
                onFilterChange(filter.copy(categories = sanitizeTodoCategoryFilters(filter.categories, categories.map { it.id }.toSet() - id)))
                onDeleteCategory(id)
            },
        )
        TodoSheet.TAGS -> TagManagerSheet(
            tags = tags,
            onClose = { sheet = TodoSheet.FILTERS },
            onRename = { source, replacement ->
                if (filter.tag.equals(source, ignoreCase = true)) onFilterChange(filter.copy(tag = replacement.trim()))
                onRenameTag(source, replacement)
            },
            onDelete = { tag ->
                if (filter.tag.equals(tag, ignoreCase = true)) onFilterChange(filter.copy(tag = null))
                onDeleteTag(tag)
            },
        )
        null -> Unit
    }

    completing?.let { todo ->
        CompleteWithSubtasksDialog(
            onDismiss = { completing = null },
            onTaskOnly = { completing = null; onToggle(todo, true, false) },
            onWithSubtasks = { completing = null; onToggle(todo, true, true) },
        )
    }
}

/** The optional quick add fields the user switched on in Settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddFields(
    quick: TodoQuickAdd,
    onQuickChange: (TodoQuickAdd) -> Unit,
    settings: AppSettings,
    categories: List<CategoryEntity>,
    paths: Map<Long, String>,
) {
    val fields = settings.todoQuickAddFields
    val locale = uiLocale(LocalUiLanguage.current)
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (TodoQuickAddField.DEADLINE in fields || TodoQuickAddField.TAGS in fields) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (TodoQuickAddField.DEADLINE in fields) {
                    LifeTextField(
                        value = quick.deadlineText,
                        onValueChange = { onQuickChange(quick.copy(deadlineText = it, error = null)) },
                        placeholder = localizedText("Due"),
                        enabled = !quick.inFlight,
                        isError = quick.error == "Enter a valid deadline",
                        modifier = Modifier.weight(1f),
                        trailing = {
                            DatePickerButton(SmartDateParser.parse(quick.deadlineText, LocalDate.now(), settings.dateFormat, locale), onPicked = { picked ->
                                onQuickChange(quick.copy(deadlineText = UserFormatting.formatDate(picked, settings.dateFormat, locale), error = null))
                            })
                        },
                    )
                }
                if (TodoQuickAddField.TAGS in fields) {
                    LifeTextField(
                        value = quick.tagsText,
                        onValueChange = { onQuickChange(quick.copy(tagsText = it)) },
                        placeholder = localizedText("Tags"),
                        enabled = !quick.inFlight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (TodoQuickAddField.CATEGORY in fields) {
            ChoiceField(quick.categoryId?.let(paths::get) ?: localizedText("Uncategorized"), Modifier.fillMaxWidth(), enabled = !quick.inFlight) { close ->
                DropdownMenuItem(text = { Text(localizedText("Uncategorized")) }, onClick = { onQuickChange(quick.copy(categoryId = null)); close() })
                categories.sortedBy { paths[it.id] }.forEach { category ->
                    DropdownMenuItem(text = { Text(paths[category.id] ?: category.name) }, onClick = { onQuickChange(quick.copy(categoryId = category.id)); close() })
                }
            }
        }
        if (TodoQuickAddField.PRIORITY in fields) {
            PriorityPills(quick.priority, { onQuickChange(quick.copy(priority = it ?: TodoPriority.NONE)) }, allowAny = false, enabled = !quick.inFlight)
        }
    }
}

/**
 * One todo: the round check mark in its priority colour, the title and one quiet line (when,
 * category, tags). Subtasks unfold under it. Swiping to the left deletes; the caller offers Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoRow(
    todo: TodoEntity,
    subtasks: List<SubtaskEntity>,
    categoryPath: String?,
    settings: AppSettings,
    showDate: Boolean,
    expanded: Boolean,
    selected: Boolean,
    onExpand: () -> Unit,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onToggleSubtask: (SubtaskEntity, Boolean) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    showReorder: Boolean,
) {
    val language = LocalUiLanguage.current
    val done = todo.completedAt != null
    val now = LocalDate.now().toEpochDay()
    val overdue = !done && todo.deadlineEpochDay != null && (todo.deadlineEpochDay < now ||
        (todo.deadlineEpochDay == now && (todo.deadlineMinute ?: 1_440) < LocalTime.now().toSecondOfDay() / 60))
    val details = buildList {
        todo.deadlineEpochDay?.let { day ->
            if (showDate) add(todoDeadlineLabel(day, todo.deadlineMinute, settings, language))
            else todo.deadlineMinute?.let { add(UserFormatting.formatMinuteOfDay(it, settings.timeFormat, false, uiLocale(language))) }
        }
        categoryPath?.let(::add)
        todo.tagsCsv.split(',').map(String::trim).filter(String::isNotEmpty).forEach { add("#$it") }
    }.joinToString(" · ")
    val deleteAction = deleteLabel(todo.title, language)
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.padding(horizontal = Space.xs),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(LifeTheme.colors.dangerContainer).padding(horizontal = Space.lg),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Rounded.DeleteOutline, null, tint = LifeTheme.colors.danger) }
        },
    ) {
        ListRow(
            title = todo.title,
            supporting = details.ifBlank { null },
            supportingColor = if (overdue) LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
            struck = done,
            selected = selected,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .semantics { customActions = listOf(CustomAccessibilityAction(deleteAction) { onDelete(); true }) },
            leading = {
                CheckCircle(
                    checked = done,
                    onCheckedChange = onToggle,
                    color = priorityColor(todo.priority) ?: MaterialTheme.colorScheme.primary,
                    contentDescription = todo.title,
                )
            },
            trailing = {
                if (todo.seriesId != null) {
                    Icon(Icons.Rounded.Repeat, localizedText("Repeating"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (subtasks.isNotEmpty() && !done) {
                    Row(
                        Modifier.clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClick = onExpand).padding(horizontal = Space.sm, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${subtasks.count { it.isCompleted }}/${subtasks.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            localizedText(if (expanded) "Hide subtasks" else "Show subtasks"),
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showReorder) {
                    IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowUp, localizedText("Move up"))
                    }
                    IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowDown, localizedText("Move down"))
                    }
                }
            },
            onClick = onClick,
            extra = if (expanded && subtasks.isNotEmpty() && !done) {
                {
                    Column(Modifier.padding(top = Space.xs)) {
                        subtasks.sortedBy { it.sortOrder }.forEach { subtask ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CheckCircle(
                                    checked = subtask.isCompleted,
                                    onCheckedChange = { checked -> onToggleSubtask(subtask, checked) },
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
            } else {
                null
            },
        )
    }
}

/** "Today 6:00 PM", "Tomorrow", "Fri 10/09/2026": when a todo is due, in the user's formats. */
internal fun todoDeadlineLabel(day: Long, minute: Int?, settings: AppSettings, language: UiLanguage): String {
    val locale = uiLocale(language)
    val date = LocalDate.ofEpochDay(day)
    val today = LocalDate.now()
    val text = when (date) {
        today -> translateUiText("Today", language)
        today.plusDays(1) -> translateUiText("Tomorrow", language)
        today.minusDays(1) -> translateUiText("Yesterday", language)
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + UserFormatting.formatDate(date, settings.dateFormat, locale)
    }
    return text + (minute?.let { " " + UserFormatting.formatMinuteOfDay(it, settings.timeFormat, false, locale) } ?: "")
}

private fun filterSummary(filter: TodoFilter, paths: Map<Long, String>, language: UiLanguage): String = buildList {
    if (filter.sort != TodoSort.DEADLINE) {
        val sort = translateUiText(filter.sort.label, language)
        add(if (language == UiLanguage.SIMPLIFIED_CHINESE) "按${sort}排序" else "Sorted by ${sort.lowercase()}")
    }
    filter.priority?.let { add(translateUiText(it.displayName(), language)) }
    if (!filter.allCategories) {
        filter.categories.forEach { key ->
            add(if (key == UNCATEGORIZED_FILTER_KEY) translateUiText("Uncategorized", language) else paths[key] ?: return@forEach)
        }
    }
    filter.tag?.let { add("#$it") }
}.joinToString(" · ")
