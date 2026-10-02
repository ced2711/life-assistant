package com.ced2711.lifetracker.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.ui.design.Dot
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.RowDivider
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.priorityColor
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * The top of the Calendar: the month or range, Today (only when another day is selected),
 * previous and next, and the view switch. On phones the switch gets a line of its own.
 */
@Composable
internal fun CalendarHeader(
    title: String,
    view: CalendarView,
    showToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onView: (CalendarView) -> Unit,
    modifier: Modifier = Modifier,
) {
    @Composable
    fun titleAndSteps(rowModifier: Modifier) {
        Row(rowModifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = Space.xs).semantics { heading() },
            )
            if (view != CalendarView.AGENDA) {
                if (showToday) TextButton(onClick = onToday) { Text(localizedText("Today"), maxLines = 1) }
                IconButton(onClick = onPrevious) { Icon(Icons.Rounded.ChevronLeft, localizedText("Previous")) }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, localizedText("Next")) }
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                titleAndSteps(Modifier.weight(1f))
                Segmented(CalendarView.entries, view, onView, { localizedText(it.label) })
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                titleAndSteps(Modifier.fillMaxWidth())
                ViewSwitch(view, onView, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The shared Segmented control with tighter sides, so four views fit a 320dp phone and large
 * fonts without cutting a label.
 */
@Composable
private fun ViewSwitch(view: CalendarView, onView: (CalendarView) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CalendarView.entries.forEach { option ->
            val isSelected = option == view
            val background by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.surfaceBright else Color.Transparent,
                tween(150),
                label = "view",
            )
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(background)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onView(option) })
                    .defaultMinSize(minHeight = 40.dp)
                    .padding(horizontal = Space.xs, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    localizedText(option.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Calls [onPage] with -1 or 1 after a clear swipe to the right or left. */
@Composable
private fun Modifier.swipeToPage(onPage: (Int) -> Unit): Modifier {
    val page by rememberUpdatedState(onPage)
    return pointerInput(Unit) {
        val threshold = 56.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onHorizontalDrag = { _, amount -> total += amount },
            onDragEnd = {
                if (total > threshold) page(-1) else if (total < -threshold) page(1)
            },
        )
    }
}

/**
 * The month as a grid with hairlines. Narrow cells (phones) show the number, the net amount and
 * a mark per todo; roomy cells show todo titles. [minHeight] lets the grid fill a tall pane.
 * Swiping left or right changes the month.
 */
@Composable
internal fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    data: CalendarData,
    firstDayOfWeek: DayOfWeek,
    onSelect: (LocalDate) -> Unit,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 0.dp,
) {
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val fontScale = LocalDensity.current.fontScale
    val first = monthGridStart(month, firstDayOfWeek)
    val weeks = monthGridWeeks(month, firstDayOfWeek)
    val line = LifeTheme.colors.divider
    val selectLabel = localizedText("Select date")
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val roomy = (maxWidth - 8.dp) / 7 >= MONTH_CELL_TITLES_MIN_WIDTH
        val weekdayRow = 28.dp * fontScale.coerceAtLeast(1f)
        val rowHeight = maxOf(monthGridRowHeight(fontScale, roomy), (minHeight - weekdayRow) / weeks - 1.dp)
        Column(Modifier.swipeToPage(onPage)) {
            Row(Modifier.fillMaxWidth().height(weekdayRow), verticalAlignment = Alignment.CenterVertically) {
                repeat(7) { index ->
                    Text(
                        UserFormatting.formatWeekday(firstDayOfWeek.plus(index.toLong()), locale),
                        Modifier.weight(1f).padding(horizontal = if (roomy) Space.sm else 0.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = if (roomy) TextAlign.Start else TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, line, RoundedCornerShape(16.dp))) {
                repeat(weeks) { week ->
                    if (week > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(line))
                    Row(Modifier.fillMaxWidth().height(rowHeight)) {
                        repeat(7) { index ->
                            if (index > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(line))
                            val date = first.plusDays((week * 7 + index).toLong())
                            val day = date.toEpochDay()
                            val todos = data.contents.todos[day].orEmpty()
                            val net = data.contents.net(day)
                            val open = todos.count { it.completedAt == null }
                            val hasDiary = data.showDiary && day in data.contents.diary
                            val isSelected = date == selected
                            val isToday = date == data.today
                            val inMonth = YearMonth.from(date) == month
                            val summary = daySummary(date, todos.size - open, open, net, hasDiary, isToday, data.settings, language)
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(if (isSelected) LifeTheme.colors.accentSoft else Color.Transparent)
                                    .clickable(onClickLabel = selectLabel, role = Role.Button) { onSelect(date) }
                                    .clearAndSetSemantics {
                                        contentDescription = summary
                                        this.selected = isSelected
                                    },
                            ) {
                                if (roomy) {
                                    RoomyDayCell(date, todos, net, hasDiary, isToday, isSelected, inMonth, rowHeight)
                                } else {
                                    CompactDayCell(date, todos, net, hasDiary, isToday, isSelected, inMonth)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The day's number; today sits in a filled accent circle, the selected day is bold in the accent. */
@Composable
private fun DayNumber(date: LocalDate, isToday: Boolean, isSelected: Boolean, inMonth: Boolean, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                isToday -> MaterialTheme.colorScheme.onPrimary
                isSelected -> MaterialTheme.colorScheme.primary
                inMonth -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun CellNet(net: Long, inMonth: Boolean) {
    Text(
        compactSignedAmount(net),
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
        color = (if (net > 0) LifeTheme.colors.income else LifeTheme.colors.expense).copy(alpha = if (inMonth) 1f else 0.5f),
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun todoMarkColor(todo: TodoEntity, dimmed: Boolean): Color = when {
    todo.completedAt != null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dimmed) 0.2f else 0.35f)
    else -> (priorityColor(todo.priority) ?: MaterialTheme.colorScheme.primary).copy(alpha = if (dimmed) 0.5f else 1f)
}

/** A phone's day cell: number, net amount, and a dot per todo in its priority colour (grey when done). */
@Composable
private fun CompactDayCell(
    date: LocalDate,
    todos: List<TodoEntity>,
    net: Long,
    hasDiary: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    inMonth: Boolean,
) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(Modifier.fillMaxSize().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DayNumber(date, isToday, isSelected, inMonth, 24.dp * scale)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (net != 0L) CellNet(net, inMonth)
        }
        Row(
            Modifier.height(14.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Up to four marks; with more todos, three marks and how many are left.
            val shown = if (todos.size > 4) todos.take(3) else todos
            shown.forEach { todo -> Dot(todoMarkColor(todo, !inMonth), size = 6.dp) }
            if (todos.size > shown.size) {
                Text(
                    "+${todos.size - shown.size}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (inMonth) 1f else 0.5f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
    if (hasDiary) {
        Box(Modifier.fillMaxSize().padding(5.dp), contentAlignment = Alignment.TopEnd) {
            Dot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (inMonth) 0.9f else 0.4f), size = 5.dp)
        }
    }
}

/** A wide day cell, as on the desktop: number, diary dot and net on top, then the todo titles that fit. */
@Composable
private fun RoomyDayCell(
    date: LocalDate,
    todos: List<TodoEntity>,
    net: Long,
    hasDiary: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    inMonth: Boolean,
    height: Dp,
) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val numberSize = 24.dp * scale
    val titleHeight = 19.dp * scale
    Column(Modifier.fillMaxSize().padding(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(numberSize)) {
            DayNumber(date, isToday, isSelected, inMonth, numberSize)
            if (hasDiary) Dot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (inMonth) 0.9f else 0.4f), Modifier.padding(start = 5.dp), size = 5.dp)
            Spacer(Modifier.weight(1f))
            if (net != 0L) CellNet(net, inMonth)
        }
        val room = height - 12.dp - numberSize - 3.dp
        val fits = ((room + 2.dp) / (titleHeight + 2.dp)).toInt().coerceAtLeast(0)
        Column(Modifier.padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val shown = if (todos.size > fits) todos.take((fits - 1).coerceAtLeast(0)) else todos
            shown.forEach { todo -> CellTodo(todo, dimmed = !inMonth, height = titleHeight) }
            if (todos.size > shown.size && fits > 0) {
                Text(
                    moreCount(todos.size - shown.size, LocalUiLanguage.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** A todo inside a roomy day cell: a bar in its priority colour and the title. */
@Composable
private fun CellTodo(todo: TodoEntity, dimmed: Boolean, height: Dp) {
    val done = todo.completedAt != null
    val color = priorityColor(todo.priority) ?: MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(5.dp))
            .background(if (done) Color.Transparent else color.copy(alpha = if (dimmed) 0.08f else 0.16f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (done) Color.Transparent else color.copy(alpha = if (dimmed) 0.5f else 1f)))
        Text(
            todo.title.ifBlank { todo.description },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done || dimmed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 5.dp),
        )
    }
}

/**
 * The week as seven rows: weekday and number, the day's todos by title, and its net. Tapping a
 * day selects it. Swiping left or right changes the week.
 */
@Composable
internal fun WeekList(
    weekStart: LocalDate,
    selected: LocalDate,
    data: CalendarData,
    onSelect: (LocalDate) -> Unit,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val selectLabel = localizedText("Select date")
    Column(modifier.fillMaxWidth().swipeToPage(onPage)) {
        repeat(7) { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val day = date.toEpochDay()
            val todos = data.contents.todos[day].orEmpty()
            val net = data.contents.net(day)
            val isSelected = date == selected
            val isToday = date == data.today
            if (offset > 0) RowDivider(inset = 0.dp)
            Row(
                Modifier.fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (isSelected) LifeTheme.colors.accentSoft else Color.Transparent)
                    .clickable(onClickLabel = selectLabel, role = Role.Button) { onSelect(date) }
                    .semantics { this.selected = isSelected }
                    .heightIn(min = 60.dp)
                    .padding(horizontal = Space.sm, vertical = Space.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.width(44.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    DayNumber(date, isToday, isSelected, inMonth = true, size = 26.dp * scale)
                }
                Column(
                    Modifier.weight(1f).padding(start = Space.sm, top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    // The day's net shares the first line, so the other titles keep the full width.
                    val netText: (@Composable () -> Unit)? = if (net != 0L) {
                        { NetText(net, MaterialTheme.typography.labelLarge, Modifier.padding(start = Space.sm)) }
                    } else {
                        null
                    }
                    if (todos.isEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                localizedText("No todos"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f),
                            )
                            netText?.invoke()
                        }
                    }
                    // With large fonts the first title needs the whole line; the net moves under the todos.
                    val netBelow = scale >= 1.3f && todos.isNotEmpty()
                    val shown = if (todos.size > 5) todos.take(4) else todos
                    shown.forEachIndexed { index, todo ->
                        if (index == 0 && netText != null && !netBelow) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { WeekTodoLine(todo, data) }
                                netText()
                            }
                        } else {
                            WeekTodoLine(todo, data)
                        }
                    }
                    if (netBelow && netText != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { netText() }
                    }
                    if (todos.size > shown.size) {
                        Text(
                            moreCount(todos.size - shown.size, language),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One todo in the week list: a dot in its priority colour, the title and its time. */
@Composable
private fun WeekTodoLine(todo: TodoEntity, data: CalendarData) {
    val done = todo.completedAt != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(todoMarkColor(todo, dimmed = false), size = 6.dp)
        Text(
            todo.title.ifBlank { todo.description },
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = Space.sm),
        )
        todo.deadlineMinute?.let { minute ->
            Text(
                UserFormatting.formatMinuteOfDay(minute, data.settings.timeFormat, data.systemUses24Hour, uiLocale(LocalUiLanguage.current)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
    }
}

/**
 * The Agenda's rows: overdue todos first under a red label, then each of the coming 30 days that
 * has todos or ledger entries, with the day's net beside its name.
 */
internal fun LazyListScope.agendaItems(data: CalendarData, actions: CalendarActions) {
    val today = data.today.toEpochDay()
    val overdue = data.contents.overdue(today)
    val days = data.contents.agendaDays(today)
    if (overdue.isNotEmpty()) {
        item(key = "overdue") {
            SectionLabel(localizedText("Overdue"), count = overdue.size, color = LifeTheme.colors.danger, modifier = Modifier.padding(start = Space.md))
        }
        items(overdue, key = { "overdue-${it.id}" }) { todo -> TodoRow(todo, data, actions, showDate = true) }
    }
    if (overdue.isEmpty() && days.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                title = localizedText("Nothing planned for the next 30 days"),
                icon = Icons.Rounded.EventAvailable,
                body = localizedText("Todos with a date and ledger entries show up here."),
            )
        }
    }
    days.forEach { day ->
        item(key = "day-$day") {
            SectionLabel(
                relativeDayLabel(day, data.today, data.settings, LocalUiLanguage.current),
                modifier = Modifier.padding(start = Space.md, top = Space.sm),
                color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                data.contents.net(day).takeIf { it != 0L }?.let { net ->
                    NetText(net, MaterialTheme.typography.labelLarge, Modifier.padding(end = Space.md))
                }
            }
        }
        items(data.contents.todos[day].orEmpty(), key = { "todo-${it.id}" }) { todo -> TodoRow(todo, data, actions, showDate = false) }
        items(data.contents.ledger[day].orEmpty(), key = { "ledger-${it.id}" }) { entry -> LedgerRow(entry, data, actions, withDot = true) }
    }
}
