package com.ced2711.lifetracker.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.ced2711.lifetracker.data.repository.TaskLedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

object WidgetRefreshCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var roomObservation: Job? = null

    fun observeRoomChanges(context: Context, repository: TaskLedgerRepository) {
        if (roomObservation != null) return
        val appContext = context.applicationContext
        roomObservation = scope.launch {
            repository.activeTodos
                .distinctUntilChanged()
                .collect { TodayTodoWidget().updateAll(appContext) }
        }
    }

    suspend fun refresh(context: Context) {
        TodayTodoWidget().updateAll(context.applicationContext)
    }
}
