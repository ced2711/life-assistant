package com.ced2711.lifetracker.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.repository.TaskLedgerRepository
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
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
) : DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val lifecycleAttached = AtomicBoolean(false)
    private val deferral = LauncherIconMoodDeferral()
    private var observationJob: Job? = null

    fun start() {
        if (observationJob != null) return
        attachProcessLifecycle()
        observationJob = scope.launch {
            combine(repository.activeTodos, repository.completedTodos) { active, completed ->
                launcherIconMood(
                    activeTodos = active,
                    completedTodos = completed,
                    todayEpochDay = LocalDate.now().toEpochDay(),
                )
            }
                .distinctUntilChanged()
                .collectLatest { mood ->
                    delay(BACKGROUND_SETTLE_MILLIS)
                    updateNowOrAfterBackground(mood)
                }
        }
    }

    suspend fun refresh() {
        val today = LocalDate.now().toEpochDay()
        val mood = launcherIconMood(
            activeTodos = repository.activeTodos.first(),
            completedTodos = repository.completedTodos.first(),
            todayEpochDay = today,
        )
        delay(BACKGROUND_SETTLE_MILLIS)
        updateNowOrAfterBackground(mood)
    }

    override fun onStop(owner: LifecycleOwner) {
        val pendingMood = deferral.consumePending() ?: return
        scope.launch { runCatching { applyLauncherIconMood(appContext, pendingMood) } }
    }

    private fun updateNowOrAfterBackground(mood: LauncherIconMood) {
        val appIsForeground = processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val immediateMood = deferral.submit(mood, appIsForeground) ?: return
        runCatching { applyLauncherIconMood(appContext, immediateMood) }
    }

    private fun attachProcessLifecycle() {
        if (!lifecycleAttached.compareAndSet(false, true)) return
        val attach = { processLifecycle.addObserver(this) }
        if (Looper.myLooper() == Looper.getMainLooper()) attach()
        else Handler(Looper.getMainLooper()).post(attach)
    }

    private companion object {
        const val BACKGROUND_SETTLE_MILLIS = 750L
    }
}

internal class LauncherIconMoodDeferral {
    private var pendingMood: LauncherIconMood? = null

    @Synchronized
    fun submit(mood: LauncherIconMood, appIsForeground: Boolean): LauncherIconMood? {
        if (!appIsForeground) return mood
        pendingMood = mood
        return null
    }

    @Synchronized
    fun consumePending(): LauncherIconMood? = pendingMood.also { pendingMood = null }
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
