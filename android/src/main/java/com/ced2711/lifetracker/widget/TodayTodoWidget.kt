package com.ced2711.lifetracker.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ced2711.lifetracker.MainActivity
import com.ced2711.lifetracker.R
import com.ced2711.lifetracker.TaskLedgerApplication
import com.ced2711.lifetracker.data.repository.TaskLedgerRepository
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.theme.Neutral
import com.ced2711.lifetracker.ui.theme.accentOf
import com.ced2711.lifetracker.ui.theme.lifeColors
import com.ced2711.lifetracker.worker.WorkScheduler
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

private val TodoIdKey = ActionParameters.Key<Long>("todo_id")

internal suspend fun completeTodoParentOnly(
    repository: TaskLedgerRepository,
    todoId: Long,
) {
    repository.completeTodo(todoId, completeSubtasks = false)
}

/**
 * The home-screen widget: what is overdue and due today, to tick off without opening the app.
 * It wears the app's own colours and accent and follows the app's light or dark setting.
 */
class TodayTodoWidget : GlanceAppWidget() {
    // Exact: the layout is chosen for the size the widget really has. With a list of preset sizes
    // the launcher picked the nearest smaller one, and a 4x2 widget showed the one-row layout.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val application = context.applicationContext as TaskLedgerApplication
        val ready = application.startupRecoveryCoordinator.awaitReady()
        val repository = application.container.repository
        val settingsFlow = application.container.settingsRepository.settings
        // Read once so the first picture is complete, then follow the data: ticking a todo off
        // must show at once, also while the launcher keeps this widget's session alive.
        val firstSettings = settingsFlow.first()
        val firstActive = if (ready) withContext(Dispatchers.IO) { repository.activeTodos.first() } else emptyList()
        val firstCompleted = if (ready) withContext(Dispatchers.IO) { repository.completedTodos.first() } else emptyList()
        val systemUses24Hour = DateFormat.is24HourFormat(context)
        provideContent {
            val settings by settingsFlow.collectAsState(firstSettings)
            val active by (if (ready) repository.activeTodos else flowOf(firstActive)).collectAsState(firstActive)
            val completed by (if (ready) repository.completedTodos else flowOf(firstCompleted)).collectAsState(firstCompleted)
            val model = if (ready) {
                buildWidgetModel(active, completed, LocalDate.now(), settings.uiLanguage, settings.timeFormat, systemUses24Hour)
            } else {
                null
            }
            TodayWidgetContent(context, WidgetState(model, settings.uiLanguage, widgetPalette(settings.themeMode, settings.accentColor)))
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

/** [model] is null while the app still has to finish recovering its data. */
internal data class WidgetState(val model: WidgetModel?, val language: UiLanguage, val palette: WidgetPalette)

/** The widget's colours: the app's neutral surface, its text colours and the chosen accent. */
internal data class WidgetPalette(
    val background: ColorProvider,
    val text: ColorProvider,
    val secondary: ColorProvider,
    val accent: ColorProvider,
    val accentSoft: ColorProvider,
    val danger: ColorProvider,
    val track: ColorProvider,
    val urgent: ColorProvider,
    val high: ColorProvider,
    val medium: ColorProvider,
    val low: ColorProvider,
) {
    fun priority(priority: TodoPriority): ColorProvider = when (priority) {
        TodoPriority.URGENT -> urgent
        TodoPriority.HIGH -> high
        TodoPriority.MEDIUM -> medium
        TodoPriority.LOW -> low
        TodoPriority.NONE -> accent
    }
}

internal fun widgetPalette(themeMode: ThemeMode, accentColor: AccentColor): WidgetPalette {
    // System: one colour for day and one for night, picked by the launcher. Otherwise the app's choice.
    fun both(light: Color, dark: Color): ColorProvider = when (themeMode) {
        ThemeMode.SYSTEM -> androidx.glance.color.ColorProvider(day = light, night = dark)
        ThemeMode.LIGHT -> ColorProvider(light)
        ThemeMode.DARK -> ColorProvider(dark)
    }
    val lightAccent = accentOf(accentColor, dark = false)
    val darkAccent = accentOf(accentColor, dark = true)
    val light = lifeColors(dark = false, accent = lightAccent)
    val dark = lifeColors(dark = true, accent = darkAccent)
    return WidgetPalette(
        background = both(Neutral.lightContainer, Neutral.darkContainer),
        text = both(Neutral.lightText, Neutral.darkText),
        secondary = both(Neutral.lightTextSecondary, Neutral.darkTextSecondary),
        accent = both(lightAccent, darkAccent),
        accentSoft = both(light.accentSoft, dark.accentSoft),
        danger = both(light.danger, dark.danger),
        track = both(Neutral.lightHigh, Neutral.darkHighest),
        urgent = both(light.priorityUrgent, dark.priorityUrgent),
        high = both(light.priorityHigh, dark.priorityHigh),
        medium = both(light.priorityMedium, dark.priorityMedium),
        low = both(light.priorityLow, dark.priorityLow),
    )
}

/**
 * [scrolling] is off only where a list cannot be drawn (design review pictures): the todos that
 * fit are then laid out one under the other.
 */
@Composable
internal fun TodayWidgetContent(context: Context, state: WidgetState, scrolling: Boolean = true) {
    val size = LocalSize.current
    val frame = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(state.palette.background)
        .cornerRadius(22.dp)
    when (widgetLayoutForSize(size.width.value.toInt(), size.height.value.toInt())) {
        WidgetLayout.TINY -> TinyWidget(context, state, frame)
        WidgetLayout.WIDE_SHORT -> WideWidget(context, state, frame)
        WidgetLayout.STANDARD -> StandardWidget(context, state, frame, size, scrolling)
    }
}

/** One cell high and narrow: how many are left. Tapping opens the Todo page. */
@Composable
private fun TinyWidget(context: Context, state: WidgetState, frame: GlanceModifier) {
    val palette = state.palette
    val model = state.model
    Row(
        modifier = frame
            .clickable(actionStartActivity(WidgetNavigation.todoListIntent(context)))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (model != null && model.left == 0) {
            Image(ImageProvider(R.drawable.ic_widget_done), null, GlanceModifier.size(26.dp), colorFilter = ColorFilter.tint(palette.accent))
        } else {
            Text(
                text = model?.left?.toString() ?: "–",
                style = TextStyle(color = if ((model?.overdueCount ?: 0) > 0) palette.danger else palette.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(translateUiText("Today", state.language), style = TextStyle(color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(tinyCaption(model, state.language), style = TextStyle(color = palette.secondary, fontSize = 11.sp), maxLines = 1)
        }
    }
}

/** One cell high and wide: the count, the next thing to do, and the two shortcuts. */
@Composable
private fun WideWidget(context: Context, state: WidgetState, frame: GlanceModifier) {
    val palette = state.palette
    val model = state.model
    Row(
        modifier = frame.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier.width(74.dp).clickable(actionStartActivity(WidgetNavigation.todoListIntent(context))),
        ) {
            Text(translateUiText("Today", state.language), style = TextStyle(color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(
                text = if (model == null) translateUiText("Unavailable", state.language) else tinyStatus(model, state.language),
                style = TextStyle(color = if ((model?.overdueCount ?: 0) > 0) palette.danger else palette.secondary, fontSize = 11.sp),
                maxLines = 1,
            )
        }
        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.CenterStart) {
            when {
                model == null -> Message(translateUiText("Open Life Assistant to recover data", state.language), palette)
                model.todos.isEmpty() -> Message(widgetStatus(model, state.language), palette, accent = model.doneToday > 0)
                else -> TodoRow(context, model.todos.first(), state)
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Shortcuts(context, state)
    }
}

/** Two cells or more high: header, progress of the day and the list, which scrolls. */
@Composable
private fun StandardWidget(context: Context, state: WidgetState, frame: GlanceModifier, size: DpSize, scrolling: Boolean) {
    val palette = state.palette
    val model = state.model
    val roomy = size.width >= 200.dp
    val edge = if (roomy) 14.dp else 10.dp
    Column(modifier = frame.padding(top = if (roomy) 12.dp else 8.dp, bottom = 6.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(start = edge, end = if (roomy) 10.dp else edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(WidgetNavigation.todoListIntent(context))),
            ) {
                // The date sits beside the title where there is room, and takes the status line
                // when there is nothing left to say about todos.
                val nothingLeft = model != null && model.left == 0
                Text(
                    text = if (model != null && !nothingLeft && size.width >= 300.dp) {
                        translateUiText("Today", state.language) + " · " + model.dateLabel
                    } else {
                        translateUiText("Today", state.language)
                    },
                    style = TextStyle(color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    text = when {
                        model == null -> translateUiText("Unavailable", state.language)
                        nothingLeft -> model.dateLabel
                        else -> widgetStatus(model, state.language)
                    },
                    style = TextStyle(color = if ((model?.overdueCount ?: 0) > 0) palette.danger else palette.secondary, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            if (roomy) Shortcuts(context, state)
        }
        if (model != null && model.left + model.doneToday > 0) {
            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = model.progress,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp).padding(horizontal = edge),
                color = palette.accent,
                backgroundColor = palette.track,
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        when {
            model == null -> Box(GlanceModifier.defaultWeight().fillMaxWidth().padding(horizontal = edge), contentAlignment = Alignment.CenterStart) {
                Message(translateUiText("Open Life Assistant to finish data recovery", state.language), palette)
            }
            model.todos.isEmpty() -> Column(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth().clickable(actionStartActivity(WidgetNavigation.todoListIntent(context))),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(ImageProvider(R.drawable.ic_widget_done), null, GlanceModifier.size(30.dp), colorFilter = ColorFilter.tint(palette.accent))
                Spacer(GlanceModifier.height(6.dp))
                Text(widgetStatus(model, state.language), style = TextStyle(color = palette.secondary, fontSize = 13.sp), maxLines = 2)
            }
            scrolling -> LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth().padding(horizontal = edge - 8.dp)) {
                items(model.todos, itemId = { it.id }) { todo -> TodoRow(context, todo, state) }
            }
            else -> Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth().padding(horizontal = edge - 8.dp)) {
                model.todos.take(((size.height.value - 70) / WIDGET_TODO_ROW_HEIGHT_DP).toInt().coerceAtLeast(1)).forEach { todo -> TodoRow(context, todo, state) }
            }
        }
    }
}

/**
 * One todo: the round check mark in its priority colour finishes it, the rest opens it in the
 * app. Each half is a touch target of 48dp.
 */
@Composable
private fun TodoRow(context: Context, todo: WidgetTodo, state: WidgetState) {
    val palette = state.palette
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(WIDGET_TODO_ROW_HEIGHT_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(40.dp, WIDGET_TODO_ROW_HEIGHT_DP.dp)
                .semantics { contentDescription = "${translateUiText("Complete", state.language)} ${todo.title}" }
                .clickable(actionRunCallback<CompleteTodoFromWidgetAction>(actionParametersOf(TodoIdKey to todo.id))),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_widget_ring), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(palette.priority(todo.priority)))
        }
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(actionStartActivity(MainActivity.todoReminderIntent(context, todo.id))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(todo.title, style = TextStyle(color = palette.text, fontSize = 14.sp), maxLines = 1)
            if (todo.detail != null) {
                Text(todo.detail, style = TextStyle(color = if (todo.overdue) palette.danger else palette.secondary, fontSize = 11.sp), maxLines = 1)
            }
        }
        if (todo.repeats) {
            Image(ImageProvider(R.drawable.ic_widget_repeat), translateUiText("Repeating", state.language), GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(palette.secondary))
            Spacer(GlanceModifier.width(6.dp))
        }
    }
}

/** Add a todo, and write down an expense: both land in the app with the keyboard open. */
@Composable
private fun Shortcuts(context: Context, state: WidgetState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(R.drawable.ic_widget_add, translateUiText("Add todo", state.language), state.palette, actionStartActivity(WidgetNavigation.todoListIntent(context)))
        Spacer(GlanceModifier.width(6.dp))
        RoundButton(R.drawable.ic_widget_wallet, translateUiText("Add entry", state.language), state.palette, actionStartActivity(WidgetNavigation.ledgerIntent(context)))
    }
}

@Composable
private fun RoundButton(icon: Int, label: String, palette: WidgetPalette, onClick: Action, size: Dp = 40.dp) {
    Box(
        modifier = GlanceModifier.size(size).background(palette.accentSoft).cornerRadius(size / 2).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), label, GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(palette.accent))
    }
}

@Composable
private fun Message(text: String, palette: WidgetPalette, accent: Boolean = false) {
    Text(text, style = TextStyle(color = if (accent) palette.accent else palette.secondary, fontSize = 13.sp), maxLines = 2)
}

private fun tinyCaption(model: WidgetModel?, language: UiLanguage): String = when {
    model == null -> translateUiText("Unavailable", language)
    model.left == 0 -> if (language == UiLanguage.SIMPLIFIED_CHINESE) "都完成了" else "All done"
    model.overdueCount > 0 -> if (language == UiLanguage.SIMPLIFIED_CHINESE) "逾期 ${model.overdueCount}" else "${model.overdueCount} overdue"
    else -> if (language == UiLanguage.SIMPLIFIED_CHINESE) "项待办" else "left"
}

private fun tinyStatus(model: WidgetModel, language: UiLanguage): String = when {
    model.left == 0 -> if (language == UiLanguage.SIMPLIFIED_CHINESE) "都完成了" else "All done"
    else -> if (language == UiLanguage.SIMPLIFIED_CHINESE) "剩 ${model.left} 项" else "${model.left} left"
}
