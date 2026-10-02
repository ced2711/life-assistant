package com.ced2711.lifetracker.ui.render

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.ced2711.lifetracker.domain.model.TodayOverview
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.ui.today.TodayContent

/** Scenes of the Shell screens for ScreenRenderTest; see RenderScene. */
internal val shellScenes: List<RenderScene> = listOf(
    RenderScene("today", TopLevelDestination.TODAY) { isWide ->
        TodayContent(
            overview = TodayOverview.of(RenderSamples.todos, RenderSamples.ledger, RenderSamples.today),
            settings = RenderSamples.settings,
            diaryToday = null,
            pinnedNotes = RenderSamples.notes.filter { it.pinned },
            showLedger = true,
            showDiary = true,
            showNotes = true,
            modifier = Modifier.fillMaxSize(),
            isWide = isWide,
            onToggle = { _, _, _ -> },
            subtasksByTodo = RenderSamples.subtasksByTodo,
            onAdd = {},
            onOpenTodo = {},
            onOpenLedger = {},
            onOpenDiary = {},
            onOpenNote = {},
        )
    },
    RenderScene("today-empty", TopLevelDestination.TODAY) { isWide ->
        TodayContent(
            overview = TodayOverview.of(emptyList(), emptyList(), RenderSamples.today),
            settings = RenderSamples.settings,
            diaryToday = null,
            pinnedNotes = emptyList(),
            showLedger = true,
            showDiary = false,
            showNotes = true,
            modifier = Modifier.fillMaxSize(),
            isWide = isWide,
            onToggle = { _, _, _ -> },
            subtasksByTodo = emptyMap(),
            onAdd = {},
            onOpenTodo = {},
            onOpenLedger = {},
            onOpenDiary = {},
            onOpenNote = {},
        )
    },
)
