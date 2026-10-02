package com.ced2711.lifetracker.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.domain.model.parseTags
import com.ced2711.lifetracker.ui.components.ConfirmDialog
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.MoneyText
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.Tag
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.theme.LifeTheme

/**
 * The Recurring page: every schedule with what it adds and how often. Active ones can be changed
 * or stopped, stopped ones deleted; stopping and deleting ask first, because Undo cannot bring a
 * schedule back.
 */
@Composable
internal fun LedgerRecurringPage(
    series: List<LedgerSeriesEntity>,
    formatting: LedgerDisplayFormatting,
    selectedSeriesId: Long?,
    onEditRule: (Long) -> Unit,
    onStop: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    initialStopRequest: Long? = null,
) {
    var stoppingSeriesId by rememberSaveable { mutableStateOf(initialStopRequest) }
    var deletingSeriesId by rememberSaveable { mutableStateOf<Long?>(null) }
    val ordered = remember(series) {
        series.sortedWith(compareByDescending<LedgerSeriesEntity> { it.active }.thenBy { it.startEpochDay }.thenBy { it.id })
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.xs, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item(key = "intro") {
            Text(
                localizedText("Entries are added on schedule. Stopping a schedule keeps the entries it already created."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ordered.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = localizedText("No recurring entries"),
                    icon = Icons.Rounded.Repeat,
                    body = localizedText("Choose Repeat when adding an entry, for rent, salary or subscriptions."),
                )
            }
        }
        items(ordered, key = { it.id }) { item ->
            ScheduleRow(
                item = item,
                formatting = formatting,
                selected = item.id == selectedSeriesId,
                onEdit = { onEditRule(item.id) },
                onStop = { stoppingSeriesId = item.id },
                onDelete = { deletingSeriesId = item.id },
            )
        }
    }

    series.firstOrNull { it.id == stoppingSeriesId && it.active }?.let { active ->
        ConfirmDialog(
            title = localizedText("Stop this schedule?"),
            text = localizedText("No new entries will be added. Entries it already created stay."),
            confirmLabel = localizedText("Stop"),
            destructive = true,
            onConfirm = {
                stoppingSeriesId = null
                onStop(active.id)
            },
            onDismiss = { stoppingSeriesId = null },
        )
    }
    series.firstOrNull { it.id == deletingSeriesId && !it.active }?.let { stopped ->
        ConfirmDialog(
            title = localizedText("Delete this stopped schedule?"),
            text = localizedText("It disappears from Recurring. Entries it already created stay in your ledger."),
            confirmLabel = localizedText("Delete"),
            destructive = true,
            onConfirm = {
                deletingSeriesId = null
                onDelete(stopped.id)
            },
            onDismiss = { deletingSeriesId = null },
        )
    }
}

/** One schedule: its name and state, how often and since when, its amount, and its actions. */
@Composable
private fun ScheduleRow(
    item: LedgerSeriesEntity,
    formatting: LedgerDisplayFormatting,
    selected: Boolean,
    onEdit: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    val language = LocalUiLanguage.current
    val summary = ledgerRepeatSummary(
        unit = item.recurrenceUnit,
        interval = item.intervalCount,
        start = formatting.date(item.startEpochDay),
        end = item.endEpochDay?.let(formatting::date),
        language = language,
    )
    val tags = parseTags(item.tagsCsv).joinToString(" ") { "#$it" }
    Panel(
        Modifier.fillMaxWidth(),
        padding = PaddingValues(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.xs),
        color = if (selected) LifeTheme.colors.accentSoft else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(end = Space.sm), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    ledgerLabel(item.merchant, item.note, item.type, language),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(summary, tags).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoneyText(
                item.amountCents, item.type, formatMoney(item.amountCents),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Space.md),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (item.active) {
                Tag(localizedText("Active"), MaterialTheme.colorScheme.primary)
            } else {
                Tag(localizedText("Stopped"), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (item.active) {
                TextButton(onClick = onEdit) { Text(localizedText("Edit rule")) }
                TextButton(onClick = onStop) { Text(localizedText("Stop")) }
            } else {
                TextButton(onClick = onDelete) { Text(localizedText("Delete"), color = LifeTheme.colors.danger) }
            }
        }
    }
}
