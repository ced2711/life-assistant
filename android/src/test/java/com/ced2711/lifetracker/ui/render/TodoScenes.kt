package com.ced2711.lifetracker.ui.render

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ced2711.lifetracker.data.local.AttachmentEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.RecurrenceRule
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.TodoDraft
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.ui.todo.TodoContent
import com.ced2711.lifetracker.ui.todo.TodoEditor
import com.ced2711.lifetracker.ui.todo.TodoFilter
import com.ced2711.lifetracker.ui.todo.TodoQuickAdd
import com.ced2711.lifetracker.ui.todo.TodoSheet
import com.ced2711.lifetracker.ui.todo.TodoSort

private val sampleDraft = TodoDraft(
    id = 2,
    title = "Water the plants",
    description = "Water the plants",
    categoryId = 1,
    deadlineEpochDay = RenderSamples.today.toEpochDay(),
    deadlineMinute = 18 * 60,
    priority = TodoPriority.MEDIUM,
    tags = listOf("home"),
    reminderOffsetsMinutes = listOf(0, 60),
    subtasks = listOf("Balcony", "Kitchen herbs"),
    recurrence = RecurrenceRule(RecurrenceUnit.WEEK, 1, null),
)

@Composable
private fun SampleEditor(inline: Boolean, draft: TodoDraft = sampleDraft) {
    TodoEditor(
        initialDraft = draft,
        inline = inline,
        categories = RenderSamples.categories,
        availableTags = listOf("errands", "health", "home", "report"),
        settings = RenderSamples.settings,
        existingAttachments = if (draft.id == null) emptyList() else listOf(
            AttachmentEntity(id = 1, ownerType = AttachmentOwnerType.TODO, ownerId = 2, privatePath = "", originalName = "watering-plan.pdf", mimeType = "application/pdf", sizeBytes = 48_000),
        ),
        editingSeriesOccurrence = draft.recurrence != null,
        isSaving = false,
        recordSavedAwaitingAttachments = false,
        todoCompleted = draft.id?.let { false },
        snackbarHostState = remember { SnackbarHostState() },
        onCompletionChange = {},
        onDismiss = {},
        onSave = { _, _, _ -> },
        onDelete = draft.id?.let { {} },
        onOpenAttachment = {},
        onRemoveAttachment = {},
    )
}

@Composable
private fun SampleTodos(
    isWide: Boolean,
    active: List<TodoEntity> = RenderSamples.activeTodos,
    completed: List<TodoEntity> = RenderSamples.completedTodos,
    filter: TodoFilter = TodoFilter(),
    quickFields: Set<TodoQuickAddField> = emptySet(),
    sheet: TodoSheet? = null,
    editor: Boolean = false,
) {
    TodoContent(
        active = active,
        completed = completed,
        subtasksByTodo = RenderSamples.subtasksByTodo,
        categories = RenderSamples.categories,
        seriesTags = emptyList(),
        settings = RenderSamples.settings.copy(todoQuickAddFields = quickFields),
        filter = filter,
        onFilterChange = {},
        quick = TodoQuickAdd(),
        onQuickChange = {},
        onQuickAdd = {},
        onOpen = {},
        onNew = {},
        onToggle = { _, _, _ -> },
        onToggleSubtask = { _, _ -> },
        onDelete = {},
        onMove = { _, _ -> },
        onAddCategory = { _, _ -> },
        onDeleteCategory = {},
        onRenameTag = { _, _ -> },
        onDeleteTag = {},
        modifier = Modifier.fillMaxSize(),
        isWide = isWide,
        selectedTodoId = if (editor && isWide) 2 else null,
        initialSheet = sheet,
        editorPane = if (editor && isWide) ({ SampleEditor(inline = true) }) else null,
    )
    if (editor && !isWide) SampleEditor(inline = false)
}

/** Scenes of the Todo screens for ScreenRenderTest; see RenderScene. */
internal val todoScenes: List<RenderScene> = listOf(
    RenderScene("todo", TopLevelDestination.TODO) { isWide -> SampleTodos(isWide) },
    RenderScene("todo-empty", TopLevelDestination.TODO) { isWide -> SampleTodos(isWide, active = emptyList(), completed = emptyList()) },
    RenderScene("todo-completed-custom", TopLevelDestination.TODO) { isWide ->
        SampleTodos(isWide, filter = TodoFilter(sort = TodoSort.CUSTOM, priority = null, tag = null, showCompleted = true), quickFields = TodoQuickAddField.entries.toSet())
    },
    RenderScene("todo-filters", TopLevelDestination.TODO) { isWide -> SampleTodos(isWide, sheet = TodoSheet.FILTERS) },
    RenderScene("todo-categories", TopLevelDestination.TODO) { isWide -> SampleTodos(isWide, sheet = TodoSheet.CATEGORIES) },
    RenderScene("todo-editor", TopLevelDestination.TODO) { isWide -> SampleTodos(isWide, editor = true) },
    RenderScene("todo-editor-new", TopLevelDestination.TODO) { isWide ->
        SampleTodos(isWide)
        SampleEditor(inline = false, draft = TodoDraft(description = ""))
    },
)
