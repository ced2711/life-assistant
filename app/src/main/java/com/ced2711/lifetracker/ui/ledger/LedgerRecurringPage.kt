package com.ced2711.lifetracker.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.ced2711.lifetracker.ui.localization.localizedText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.domain.model.LedgerDraft
import com.ced2711.lifetracker.domain.model.LedgerType
import java.util.UUID

@Composable
internal fun LedgerRecurringPage(
    series: List<LedgerSeriesEntity>,
    contentPadding: PaddingValues,
    onStop: (Long) -> Unit,
    onEditRule: (
        seriesId: Long,
        effectiveEpochDay: Long,
        draft: LedgerDraft,
        onSaved: () -> Unit,
        onFailure: (String) -> Unit,
    ) -> Unit,
    formatting: LedgerDisplayFormatting,
    uiOperations: LedgerUiOperationsViewModel,
) {
    var editingSeriesId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingSessionKey by rememberSaveable { mutableStateOf<String?>(null) }

    fun closeEditor() {
        val closingSession = editingSessionKey
        if (uiOperations.savingSessionKey == closingSession) return
        editingSeriesId = null
        editingSessionKey = null
        uiOperations.abandonSession(closingSession)
    }

    LaunchedEffect(uiOperations.savedSessionKeys, editingSessionKey) {
        val sessionKey = editingSessionKey ?: return@LaunchedEffect
        if (sessionKey in uiOperations.savedSessionKeys) {
            uiOperations.consumeSaved(sessionKey)
            closeEditor()
        }
    }

    series.firstOrNull { it.id == editingSeriesId }?.let { editingSeries ->
        val sessionKey = editingSessionKey ?: return@let
        LedgerRecurringRuleDialog(
            series = editingSeries,
            formatting = formatting,
            isSaving = uiOperations.savingSessionKey == sessionKey,
            failureMessage = uiOperations.failureFor(sessionKey),
            onDismiss = ::closeEditor,
            onSave = { effectiveEpochDay, draft ->
                val attempt = uiOperations.beginSave(sessionKey) ?: return@LedgerRecurringRuleDialog
                onEditRule(
                    editingSeries.id,
                    effectiveEpochDay,
                    draft,
                    { uiOperations.markSaveSucceeded(sessionKey, attempt) },
                    { message -> uiOperations.markSaveFailed(sessionKey, attempt, message) },
                )
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 820.dp)
                    .padding(bottom = 4.dp),
            ) {
                Text(localizedText("Recurring entries"), style = MaterialTheme.typography.titleMedium)
                Text(
                    localizedText("Scheduled entries are generated independently. Stopping a schedule keeps existing entries."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (series.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        localizedText("No recurring entries"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(series, key = { it.id }) { item ->
                RecurringCard(
                    item = item,
                    formatting = formatting,
                    onEdit = {
                        if (uiOperations.savingSessionKey == null) {
                            editingSeriesId = item.id
                            editingSessionKey = UUID.randomUUID().toString()
                        }
                    },
                    onStop = { onStop(item.id) },
                    modifier = Modifier.widthIn(max = 820.dp),
                )
            }
        }
    }
}

@Composable
private fun RecurringCard(
    item: LedgerSeriesEntity,
    formatting: LedgerDisplayFormatting,
    onEdit: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amountColor = if (item.type == LedgerType.INCOME) incomeColor() else expenseColor()
    val intervalLabel = if (item.intervalCount == 1) {
        when (item.recurrenceUnit) {
            com.ced2711.lifetracker.domain.model.RecurrenceUnit.DAY -> "Daily"
            com.ced2711.lifetracker.domain.model.RecurrenceUnit.WEEK -> "Weekly"
            com.ced2711.lifetracker.domain.model.RecurrenceUnit.MONTH -> "Monthly"
            com.ced2711.lifetracker.domain.model.RecurrenceUnit.YEAR -> "Yearly"
        }
    } else {
        "Every ${item.intervalCount} ${item.recurrenceUnit.name.lowercase()}s"
    }
    val localizedIntervalLabel = localizedText(intervalLabel)
    val startsLabel = localizedText("starts")
    val endsLabel = localizedText("ends")
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            item.merchant.isNotBlank() -> item.merchant
                            item.note.isNotBlank() -> item.note
                            else -> localizedText(item.type.displayName())
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                    )
                    Text(
                        text = (if (item.type == LedgerType.INCOME) "+" else "−") +
                            formatMoney(item.amountCents),
                        color = amountColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(localizedText(if (item.active) "Active" else "Stopped")) },
                )
            }
            Text(
                text = buildString {
                    append(localizedIntervalLabel)
                    append(" · $startsLabel ")
                    append(formatting.date(item.startEpochDay))
                    item.endEpochDay?.let {
                        append(" · $endsLabel ")
                        append(formatting.date(it))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.tagsCsv.isNotBlank()) {
                Text(
                    item.tagsCsv.split(',').joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.active) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Text(localizedText(" Edit rule"))
                    }
                    OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.StopCircle, contentDescription = null)
                        Text(localizedText(" Stop"))
                    }
                }
            }
        }
    }
}
