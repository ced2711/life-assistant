package com.ced2711.lifetracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ced2711.lifetracker.TaskLedgerApplication
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.repository.TaskLedgerRepository
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.worker.WorkScheduler
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val TodoIdKey = ActionParameters.Key<Long>("todo_id")

internal suspend fun completeTodoParentOnly(
    repository: TaskLedgerRepository,
    todoId: Long,
) {
    repository.completeTodo(todoId, completeSubtasks = false)
}

class TodayTodoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 56.dp),
            DpSize(110.dp, 110.dp),
            DpSize(250.dp, 72.dp),
            DpSize(320.dp, 72.dp),
            DpSize(420.dp, 110.dp),
            DpSize(180.dp, 250.dp),
            DpSize(320.dp, 250.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val application = context.applicationContext as TaskLedgerApplication
        val recoveryReady = application.startupRecoveryCoordinator.awaitReady()
        val todos = if (recoveryReady) {
            withContext(Dispatchers.IO) {
                application.container.repository
                    .activeTodosForDeadlineDay(LocalDate.now().toEpochDay())
                    .first()
            }
        } else {
            emptyList()
        }
        val uiLanguage = application.container.settingsRepository.settings.first().uiLanguage
        provideContent {
            GlanceTheme {
                TodayTodoContent(context, todos, recoveryReady, uiLanguage)
            }
        }
    }
}

class TodayTodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTodoWidget()
}

class CompleteTodoFromWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val todoId = parameters[TodoIdKey]?.takeIf { it > 0L } ?: return
        val application = context.applicationContext as TaskLedgerApplication
        if (!application.startupRecoveryCoordinator.awaitReady()) return
        completeTodoParentOnly(application.container.repository, todoId)
        WorkScheduler.rescheduleReminders(application)
        TodayTodoWidget().updateAll(application)
    }
}

@Composable
private fun TodayTodoContent(
    context: Context,
    todos: List<TodoEntity>,
    recoveryReady: Boolean,
    uiLanguage: UiLanguage,
) {
    val size = LocalSize.current
    when (widgetLayoutForSize(size.width.value.toInt(), size.height.value.toInt())) {
        WidgetLayout.TINY -> TinyTodayTodoContent(context, todos, recoveryReady, uiLanguage)
        WidgetLayout.WIDE_SHORT -> WideTodayTodoContent(context, todos, recoveryReady, uiLanguage)
        WidgetLayout.STANDARD -> StandardTodayTodoContent(context, todos, recoveryReady, uiLanguage)
    }
}

@Composable
private fun TinyTodayTodoContent(
    context: Context,
    todos: List<TodoEntity>,
    recoveryReady: Boolean,
    uiLanguage: UiLanguage,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(18.dp)
            .clickable(actionStartActivity(WidgetNavigation.todoListIntent(context)))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "✓",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.width(7.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = translateUiText("Today", uiLanguage),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = translateUiText(
                    if (recoveryReady) "${todos.size} left" else "Unavailable",
                    uiLanguage,
                ),
                style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WideTodayTodoContent(
    context: Context,
    todos: List<TodoEntity>,
    recoveryReady: Boolean,
    uiLanguage: UiLanguage,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.width(66.dp)) {
            Text(
                text = translateUiText("Today", uiLanguage),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = translateUiText(
                    if (recoveryReady) "${todos.size} left" else "Offline",
                    uiLanguage,
                ),
                style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 10.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(6.dp))
        Box(
            modifier = GlanceModifier.defaultWeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            when {
                !recoveryReady -> Text(
                    text = translateUiText("Open Life Assistant to recover data", uiLanguage),
                    style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 12.sp),
                    maxLines = 2,
                )
                todos.isEmpty() -> Text(
                    text = translateUiText("All clear for today ✓", uiLanguage),
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp),
                    maxLines = 1,
                )
                else -> TodoRow(todos.first(), uiLanguage)
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Row {
            WidgetShortcut(
                text = translateUiText("+ Task", uiLanguage),
                compact = true,
                modifier = GlanceModifier.width(52.dp),
                onClick = actionStartActivity(WidgetNavigation.todoListIntent(context)),
            )
            Spacer(GlanceModifier.width(4.dp))
            WidgetShortcut(
                text = "+ $",
                compact = true,
                modifier = GlanceModifier.width(44.dp),
                onClick = actionStartActivity(WidgetNavigation.ledgerIntent(context)),
            )
        }
    }
}

@Composable
private fun StandardTodayTodoContent(
    context: Context,
    todos: List<TodoEntity>,
    recoveryReady: Boolean,
    uiLanguage: UiLanguage,
) {
    val size = LocalSize.current
    val visibleCount = visibleTodoCountForWidget(
        heightDp = size.height.value.toInt(),
        availableCount = todos.size,
    )
    val compact = size.height < 192.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(
                horizontal = if (compact) 8.dp else 14.dp,
                vertical = if (compact) 4.dp else 14.dp,
            ),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = translateUiText("Today", uiLanguage),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = translateUiText(
                    if (recoveryReady) "${todos.size} left" else "Unavailable",
                    uiLanguage,
                ),
                style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 12.sp),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.height(if (compact) 2.dp else 8.dp))
        Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
            if (!recoveryReady) {
                Text(
                    text = translateUiText("Open Life Assistant to finish data recovery", uiLanguage),
                    style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 13.sp),
                    maxLines = 2,
                )
            } else if (todos.isEmpty()) {
                Text(
                    text = translateUiText("All clear for today ✓", uiLanguage),
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp),
                    maxLines = 2,
                )
            } else {
                todos.take(visibleCount).forEach { todo -> TodoRow(todo, uiLanguage) }
            }
        }

        Spacer(GlanceModifier.height(if (compact) 2.dp else 6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetShortcut(
                text = translateUiText("Todo", uiLanguage),
                compact = compact,
                modifier = GlanceModifier.defaultWeight(),
                onClick = actionStartActivity(WidgetNavigation.todoListIntent(context)),
            )
            Spacer(GlanceModifier.width(if (compact) 4.dp else 8.dp))
            WidgetShortcut(
                text = translateUiText("Ledger", uiLanguage),
                compact = compact,
                modifier = GlanceModifier.defaultWeight(),
                onClick = actionStartActivity(WidgetNavigation.ledgerIntent(context)),
            )
        }
    }
}

@Composable
private fun TodoRow(todo: TodoEntity, uiLanguage: UiLanguage) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(WIDGET_TODO_ROW_HEIGHT_DP.dp)
                .height(WIDGET_TODO_ROW_HEIGHT_DP.dp)
                .semantics {
                    contentDescription = "${translateUiText("Complete", uiLanguage)} ${todo.title}"
                }
                .clickable(
                    actionRunCallback<CompleteTodoFromWidgetAction>(
                        actionParametersOf(TodoIdKey to todo.id),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "○",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 21.sp),
            )
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = todo.title,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun WidgetShortcut(
    text: String,
    compact: Boolean,
    modifier: GlanceModifier,
    onClick: androidx.glance.action.Action,
) {
    Box(
        modifier = modifier
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(if (compact) 8.dp else 12.dp)
            .clickable(onClick)
            .padding(
                horizontal = if (compact) 4.dp else 10.dp,
                vertical = if (compact) 3.dp else 7.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}
