package com.ced2711.lifetracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.ced2711.lifetracker.data.repository.TaskLedgerRepository
import com.ced2711.lifetracker.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object WidgetRefreshCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var roomObservation: Job? = null

    /**
     * Redraws the widget whenever something it shows changes: the open todos, how many were
     * finished, and the settings it follows (language, theme, accent, time format).
     */
    fun observeRoomChanges(context: Context, repository: TaskLedgerRepository, settings: Flow<AppSettings>? = null) {
        if (roomObservation != null) return
        val appContext = context.applicationContext
        val todos = combine(repository.activeTodos, repository.completedTodos.map { it.size }) { active, done -> active to done }
        val look = settings?.map { listOf(it.uiLanguage, it.themeMode, it.accentColor, it.timeFormat) }
        roomObservation = scope.launch {
            (if (look == null) todos.map { it as Any } else combine(todos, look) { shown, style -> shown to style })
                .distinctUntilChanged()
                .collect { TodayTodoWidget().updateAll(appContext) }
        }
    }

    suspend fun refresh(context: Context) {
        TodayTodoWidget().updateAll(context.applicationContext)
    }
}
