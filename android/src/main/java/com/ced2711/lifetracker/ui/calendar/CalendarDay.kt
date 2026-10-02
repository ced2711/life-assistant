package com.ced2711.lifetracker.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.diaryPreview
import com.ced2711.lifetracker.ui.design.CheckCircle
import com.ced2711.lifetracker.ui.design.Dot
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.MoneyText
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.priorityColor
import com.ced2711.lifetracker.ui.ledger.formatMoney
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * The selected day: its weekday, date and net, the todos to tick off with a quick-add field, the
 * ledger entries and the diary page. It does not scroll by itself; the page around it does.
 * [showDate] is off in the Day view, where the page title already names the date.
 */
@Composable
internal fun DayDetails(
    date: LocalDate,
    data: CalendarData,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
) {
    val locale = uiLocale(LocalUiLanguage.current)
    val day = date.toEpochDay()
    val todos = data.contents.todos[day].orEmpty()
    val entries = data.contents.ledger[day].orEmpty()
    var quick by rememberSaveable(day) { mutableStateOf("") }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.md).padding(bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                    style = if (showDate) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (showDate) {
                    Text(UserFormatting.formatDate(date, data.settings.dateFormat, locale), style = MaterialTheme.typography.titleLarge)
                }
            }
            if (entries.isNotEmpty()) NetText(data.contents.net(day), MaterialTheme.typography.titleMedium)
        }

        SectionLabel(localizedText("Todos"), count = todos.size.takeIf { it > 0 }, modifier = Modifier.padding(start = Space.md))
        todos.forEach { todo -> TodoRow(todo, data, actions, showDate = false) }
        LifeTextField(
            value = quick,
            onValueChange = { quick = it },
            placeholder = localizedText("Add a todo for this day"),
            leadingIcon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                quick.trim().takeIf(String::isNotEmpty)?.let { actions.onAddTodo(it, day) }
                quick = ""
            }),
        )

        Spacer(Modifier.height(Space.sm))
        SectionLabel(localizedText("Ledger"), count = entries.size.takeIf { it > 0 }, modifier = Modifier.padding(start = Space.md))
        if (entries.isEmpty()) QuietLine(localizedText("No ledger entries"))
        entries.forEach { entry -> LedgerRow(entry, data, actions, withDot = false) }

        if (data.showDiary) {
            Spacer(Modifier.height(Space.sm))
            val page = data.contents.diary[day]
            SectionLabel(localizedText("Diary"), modifier = Modifier.padding(start = Space.md)) {
                TextButton(onClick = { actions.onOpenDiary(day) }) { Text(localizedText(if (page != null) "Open diary" else "Write diary")) }
            }
            Text(
                page?.let { diaryPreview(it.body, 240) } ?: localizedText("No diary entry"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (page != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Space.md),
            )
        }
    }
}

/** A day's net amount with its sign, green when money came in and red when it went out. */
@Composable
internal fun NetText(cents: Long, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    Text(
        signedMoney(cents),
        modifier = modifier,
        style = style.copy(fontFeatureSettings = "tnum"),
        color = when {
            cents > 0 -> LifeTheme.colors.income
            cents < 0 -> LifeTheme.colors.expense
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
    )
}

/** A quiet sentence where a list has nothing to show. */
@Composable
internal fun QuietLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
    )
}

/**
 * A todo with its round check mark in the priority colour. Ticking finishes it (asking about open
 * subtasks first); tapping the row opens it. [showDate] adds the day, for overdue todos.
 */
@Composable
internal fun TodoRow(todo: TodoEntity, data: CalendarData, actions: CalendarActions, showDate: Boolean) {
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val subtasks: List<SubtaskEntity> = data.subtasksByTodo[todo.id].orEmpty()
    val done = todo.completedAt != null
    val overdue = !done && (todo.deadlineEpochDay ?: Long.MAX_VALUE) < data.today.toEpochDay()
    val time = todo.deadlineMinute?.let { UserFormatting.formatMinuteOfDay(it, data.settings.timeFormat, data.systemUses24Hour, locale) }
    val details = buildList {
        if (showDate) {
            todo.deadlineEpochDay?.let { add(relativeDayLabel(it, data.today, data.settings, language) + time?.let { text -> " $text" }.orEmpty()) }
        } else {
            time?.let(::add)
        }
        if (subtasks.isNotEmpty()) add("${subtasks.count { it.isCompleted }}/${subtasks.size}")
    }.joinToString(" · ")
    ListRow(
        title = todo.title.ifBlank { todo.description },
        supporting = details.ifBlank { null },
        supportingColor = if (overdue) LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
        struck = done,
        leading = {
            CheckCircle(
                checked = done,
                onCheckedChange = { checked -> actions.onToggleTodo(todo, checked) },
                color = priorityColor(todo.priority) ?: MaterialTheme.colorScheme.primary,
                contentDescription = todo.title.ifBlank { todo.description },
            )
        },
        trailing = if (todo.seriesId != null) {
            { Icon(Icons.Rounded.Repeat, localizedText("Repeating"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            null
        },
        onClick = { actions.onOpenTodo(todo.id) },
    )
}

/** A ledger entry: what it was, when, and the signed amount. Tapping opens it in the Ledger. */
@Composable
internal fun LedgerRow(entry: LedgerEntryEntity, data: CalendarData, actions: CalendarActions, withDot: Boolean) {
    val locale = uiLocale(LocalUiLanguage.current)
    ListRow(
        title = entry.merchant.ifBlank { entry.note.ifBlank { localizedText(if (entry.type == LedgerType.INCOME) "Income" else "Expense") } },
        supporting = UserFormatting.formatMinuteOfDay(entry.minuteOfDay, data.settings.timeFormat, data.systemUses24Hour, locale),
        leading = if (withDot) {
            // As wide as a check mark, so entries line up with the todos above them.
            { Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Dot(if (entry.type == LedgerType.INCOME) LifeTheme.colors.income else LifeTheme.colors.expense) } }
        } else {
            null
        },
        trailing = { MoneyText(entry.amountCents, entry.type, formatMoney(entry.amountCents), style = MaterialTheme.typography.bodyMedium) },
        maxTitleLines = 1,
        onClick = { actions.onOpenLedgerEntry(entry.id) },
    )
}
