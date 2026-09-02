package com.ced2711.lifetracker.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.repository.TaskLedgerRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal enum class LauncherIconMood(val aliasClassName: String) {
    QUIET("com.ced2711.lifetracker.launcher.LifeTrackerQuiet"),
    MOMENTUM("com.ced2711.lifetracker.launcher.LifeTrackerMomentum"),
    COMPLETE("com.ced2711.lifetracker.launcher.LifeTrackerComplete"),
}

internal fun launcherIconMood(
    activeTodos: List<TodoEntity>,
    completedTodos: List<TodoEntity>,
    todayEpochDay: Long,
): LauncherIconMood {
    val activeDueToday = activeTodos.count { it.deadlineEpochDay == todayEpochDay }
    val completedDueToday = completedTodos.count { it.deadlineEpochDay == todayEpochDay }
    return when {
        activeDueToday > 0 -> LauncherIconMood.MOMENTUM
        completedDueToday > 0 -> LauncherIconMood.COMPLETE
        else -> LauncherIconMood.QUIET
    }
}

class LauncherIconMoodCoordinator(
    context: Context,
    private val repository: TaskLedgerRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private var observationJob: Job? = null

    fun start() {
        if (observationJob != null) return
        observationJob = scope.launch {
            combine(repository.activeTodos, repository.completedTodos) { active, completed ->
                launcherIconMood(
                    activeTodos = active,
                    completedTodos = completed,
                    todayEpochDay = LocalDate.now().toEpochDay(),
                )
            }
                .distinctUntilChanged()
                .collect { mood -> runCatching { applyLauncherIconMood(appContext, mood) } }
        }
    }

    suspend fun refresh() {
        val today = LocalDate.now().toEpochDay()
        val mood = launcherIconMood(
            activeTodos = repository.activeTodos.first(),
            completedTodos = repository.completedTodos.first(),
            todayEpochDay = today,
        )
        runCatching { applyLauncherIconMood(appContext, mood) }
    }
}

internal fun applyLauncherIconMood(context: Context, targetMood: LauncherIconMood) {
    val packageManager = context.packageManager
    val settings = LauncherIconMood.entries.map { mood ->
        val component = ComponentName(context.packageName, mood.aliasClassName)
        val state = if (mood == targetMood) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        component to state
    }
    if (settings.all { (component, desiredState) ->
            packageManager.getComponentEnabledSetting(component) == desiredState
        }
    ) {
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.setComponentEnabledSettings(
            settings.map { (component, state) ->
                PackageManager.ComponentEnabledSetting(
                    component,
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            },
        )
    } else {
        settings
            .sortedByDescending { (_, state) ->
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            .forEach { (component, state) ->
                packageManager.setComponentEnabledSetting(
                    component,
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }
    }
}
