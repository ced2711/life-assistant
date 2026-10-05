package com.ced2711.lifetracker.ui.render

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ced2711.lifetracker.data.local.ChecklistItemEntity
import com.ced2711.lifetracker.domain.model.TodayOverview
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.ui.today.DailyChecklistState
import com.ced2711.lifetracker.ui.today.DailyChecklistUi
import com.ced2711.lifetracker.ui.today.TodayContent

private val checklistItems = listOf(
    ChecklistItemEntity(id = 1, title = "Brush teeth", sortOrder = 0, createdAt = 1, updatedAt = 1),
    ChecklistItemEntity(id = 2, title = "Shower", sortOrder = 1, createdAt = 2, updatedAt = 2),
    ChecklistItemEntity(id = 3, title = "Check homework", sortOrder = 2, createdAt = 3, updatedAt = 3),
    ChecklistItemEntity(id = 4, title = "Take vitamins", sortOrder = 3, createdAt = 4, updatedAt = 4),
)

private fun checklist(items: List<ChecklistItemEntity>, checked: Set<Long>) = DailyChecklistState(
    items = items,
    checkedToday = checked,
    onToggle = { _, _ -> },
    onAdd = {},
    onRename = { _, _ -> },
    onDelete = {},
    onReorder = {},
)

@Composable
private fun SampleToday(
    isWide: Boolean,
    withData: Boolean = true,
    checklist: DailyChecklistState = checklist(checklistItems, setOf(1L, 2L)),
    checklistUi: DailyChecklistUi = remember { DailyChecklistUi() },
) {
    TodayContent(
        overview = if (withData) {
            TodayOverview.of(RenderSamples.todos, RenderSamples.ledger, RenderSamples.today)
        } else {
            TodayOverview.of(emptyList(), emptyList(), RenderSamples.today)
        },
        settings = RenderSamples.settings,
        diaryToday = null,
        pinnedNotes = if (withData) RenderSamples.notes.filter { it.pinned } else emptyList(),
        showLedger = true,
        showDiary = withData,
        showNotes = true,
        modifier = Modifier.fillMaxSize(),
        isWide = isWide,
        onToggle = { _, _, _ -> },
        subtasksByTodo = if (withData) RenderSamples.subtasksByTodo else emptyMap(),
        onAdd = {},
        onOpenTodo = {},
        onOpenLedger = {},
        onOpenDiary = {},
        onOpenNote = {},
        checklist = checklist,
        checklistUi = checklistUi,
    )
}

/** Scenes of the Shell screens for ScreenRenderTest; see RenderScene. */
internal val shellScenes: List<RenderScene> = listOf(
    RenderScene("today", TopLevelDestination.TODAY) { isWide -> SampleToday(isWide) },
    RenderScene("today-empty", TopLevelDestination.TODAY) { isWide ->
        SampleToday(isWide, withData = false, checklist = checklist(emptyList(), emptySet()))
    },
    // Everything ticked: the checklist folds into its heading.
    RenderScene("today-checklist-done", TopLevelDestination.TODAY) { isWide ->
        SampleToday(isWide, checklist = checklist(checklistItems, checklistItems.mapTo(HashSet()) { it.id }))
    },
    RenderScene("today-checklist-edit", TopLevelDestination.TODAY) { isWide ->
        SampleToday(isWide, checklistUi = remember { DailyChecklistUi().apply { editing = true } })
    },
    RenderScene("today-checklist-remove", TopLevelDestination.TODAY) { isWide ->
        SampleToday(isWide, checklistUi = remember { DailyChecklistUi().apply { editing = true; deleting = 3L } })
    },
)
