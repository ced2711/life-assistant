package com.ced2711.lifetracker.ui.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.DiaryDraft
import com.ced2711.lifetracker.domain.model.diaryPreview
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.uiLocale
import java.time.LocalDate
import java.time.format.TextStyle
import kotlinx.coroutines.delay

private const val AUTOSAVE_DELAY_MILLIS = 700L

@Composable
fun DiaryScreen(
    viewModel: TaskLedgerViewModel,
    requestedEpochDay: Long?,
    onRequestedDayHandled: () -> Unit,
    modifier: Modifier = Modifier,
    isWide: Boolean,
) {
    val entries by viewModel.diaryEntries.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val language = LocalUiLanguage.current
    var selectedDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    // Null means "showing the stored page"; a value is the user's unsaved or just-saved text.
    var draft by rememberSaveable(selectedDay) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val stored = entries.firstOrNull { it.epochDay == selectedDay }
    val text = draft ?: stored?.body.orEmpty()

    fun flush(day: Long, value: String?) {
        if (value != null && value != entries.firstOrNull { it.epochDay == day }?.body.orEmpty()) {
            viewModel.saveDiaryEntry(DiaryDraft(day, value))
        }
    }

    fun select(day: Long) {
        if (day == selectedDay) return
        flush(selectedDay, draft)
        selectedDay = day
    }

    LaunchedEffect(requestedEpochDay) {
        if (requestedEpochDay != null) {
            select(requestedEpochDay)
            onRequestedDayHandled()
        }
    }
    LaunchedEffect(selectedDay, draft) {
        val pending = draft ?: return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MILLIS)
        flush(selectedDay, pending)
    }
    val latestDay by rememberUpdatedState(selectedDay)
    val latestDraft by rememberUpdatedState(draft)
    DisposableEffect(Unit) {
        onDispose { flush(latestDay, latestDraft) }
    }

    val locale = uiLocale(language)
    fun dayLabel(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return UserFormatting.formatDate(date, settings.dateFormat, locale) + "  " +
            date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    }

    val editor: @Composable (Modifier) -> Unit = { editorModifier ->
        Column(editorModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { select(selectedDay - 1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, localizedText("Previous day"))
                }
                Text(
                    text = dayLabel(selectedDay),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { select(selectedDay + 1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, localizedText("Next day"))
                }
                if (selectedDay != LocalDate.now().toEpochDay()) {
                    TextButton(onClick = { select(LocalDate.now().toEpochDay()) }) {
                        Text(localizedText("Today"))
                    }
                }
                if (stored != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, localizedText("Delete diary entry"))
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text(localizedText("How was your day?")) },
            )
            Text(
                text = localizedText("Saved automatically. Clearing the text removes the entry."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val list: @Composable (Modifier) -> Unit = { listModifier ->
        DiaryEntryList(
            entries = entries,
            selectedDay = selectedDay,
            label = ::dayLabel,
            onSelect = ::select,
            modifier = listModifier,
        )
    }

    if (isWide) {
        Row(modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            list(Modifier.width(320.dp).fillMaxHeight())
            editor(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            editor(Modifier.weight(0.6f).fillMaxWidth())
            HorizontalDivider()
            list(Modifier.weight(0.4f).fillMaxWidth())
        }
    }

    if (confirmDelete) {
        HingeSafeAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(localizedText("Delete this diary entry?")) },
            text = { Text(dayLabel(selectedDay)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    draft = null
                    viewModel.deleteDiaryEntry(selectedDay)
                }) { Text(localizedText("Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(localizedText("Cancel")) }
            },
        )
    }
}

@Composable
private fun DiaryEntryList(
    entries: List<DiaryEntryEntity>,
    selectedDay: Long,
    label: (Long) -> String,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Column(modifier, verticalArrangement = Arrangement.Center) {
            Text(
                text = localizedText("No diary entries yet"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries, key = DiaryEntryEntity::epochDay) { entry ->
            val selected = entry.epochDay == selectedDay
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(entry.epochDay) },
                colors = if (selected) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(label(entry.epochDay), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.padding(top = 2.dp))
                    Text(
                        text = diaryPreview(entry.body),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
