package com.ced2711.lifetracker.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.data.local.DiaryEntryEntity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.DiaryDraft
import com.ced2711.lifetracker.domain.model.MAX_DIARY_LENGTH
import com.ced2711.lifetracker.domain.model.diaryPreview
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.components.ConfirmDialog
import com.ced2711.lifetracker.ui.design.EmptyState
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.ReadableWidth
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.notes.WritingField
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import java.time.format.TextStyle
import kotlinx.coroutines.delay

private const val AUTOSAVE_DELAY_MILLIS = 700L

/** What the quiet line under the date says about the page. */
internal enum class DiarySaveStatus { Empty, Saving, Saved }

/**
 * The diary: one page per day. The page saves itself a moment after typing stops, when another
 * day is opened, when leaving and when the app goes to the background; clearing it removes it.
 */
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
    var selectedDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    // Null means "showing the stored page"; a value is the user's unsaved or just-saved text.
    var draft by remember(selectedDay) { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val stored = entries.firstOrNull { it.epochDay == selectedDay }
    val text = draft ?: stored?.body.orEmpty()
    val latestEntries by rememberUpdatedState(entries)

    fun needsSaving(day: Long, value: String?): Boolean {
        if (value == null) return false
        val saved = latestEntries.firstOrNull { it.epochDay == day }?.body
        // Blank text on a day without a page is nothing to save.
        return value != saved.orEmpty() && !(saved == null && value.isBlank())
    }

    fun flush(day: Long, value: String?) {
        if (value != null && needsSaving(day, value)) viewModel.saveDiaryEntry(DiaryDraft(day, value))
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
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { flush(latestDay, latestDraft) }
    DisposableEffect(Unit) {
        onDispose { flush(latestDay, latestDraft) }
    }

    DiaryContent(
        entries = entries,
        settings = settings,
        selectedDay = selectedDay,
        today = LocalDate.now().toEpochDay(),
        text = text,
        status = when {
            needsSaving(selectedDay, draft) -> DiarySaveStatus.Saving
            stored != null -> DiarySaveStatus.Saved
            else -> DiarySaveStatus.Empty
        },
        hasPage = stored != null,
        isWide = isWide,
        onTextChange = { if (it.length <= MAX_DIARY_LENGTH) draft = it },
        onSelectDay = ::select,
        onDelete = { confirmDelete = true },
        modifier = modifier,
    )

    if (confirmDelete) {
        ConfirmDialog(
            title = localizedText("Delete this diary entry?"),
            text = diaryDayLabel(selectedDay, settings),
            confirmLabel = localizedText("Delete"),
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                draft = null
                viewModel.deleteDiaryEntry(selectedDay)
            },
        )
    }
}

/** The day's page to write on and the list of pages: below it on a phone, beside it when wide. */
@Composable
internal fun DiaryContent(
    entries: List<DiaryEntryEntity>,
    settings: AppSettings,
    selectedDay: Long,
    today: Long,
    text: String,
    status: DiarySaveStatus,
    hasPage: Boolean,
    isWide: Boolean,
    onTextChange: (String) -> Unit,
    onSelectDay: (Long) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = remember(entries) { entries.sortedByDescending(DiaryEntryEntity::epochDay) }
    val header: @Composable (Modifier) -> Unit = { headerModifier ->
        DiaryDayHeader(selectedDay, today, settings, status, hasPage, onSelectDay, onDelete, headerModifier)
    }
    val page: @Composable (Dp, Modifier) -> Unit = { minHeight, pageModifier ->
        WritingField(
            value = text,
            onValueChange = onTextChange,
            placeholder = localizedText("How was your day?"),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f),
            minHeight = minHeight,
            modifier = pageModifier,
        )
    }

    if (isWide) {
        Row(modifier) {
            LazyColumn(
                Modifier.width(320.dp).fillMaxHeight(),
                contentPadding = PaddingValues(start = Space.xs, end = Space.xs, bottom = Space.xxxl),
                verticalArrangement = Arrangement.spacedBy(Space.xxs),
            ) {
                diaryPages(pages, selectedDay, settings, onSelectDay)
            }
            Box(Modifier.fillMaxHeight().width(1.dp).background(LifeTheme.colors.divider))
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val pageHeight = (maxHeight - 150.dp).coerceAtLeast(200.dp)
                ReadableWidth(Modifier.verticalScroll(rememberScrollState()), maxWidth = 720.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Space.xxl)) {
                        header(Modifier)
                        page(pageHeight, Modifier.fillMaxWidth().padding(top = Space.md, bottom = Space.xxxl))
                    }
                }
            }
        }
    } else {
        BoxWithConstraints(modifier) {
            // The page takes most of the screen; the earlier pages start below it.
            val pageHeight = (maxHeight - 250.dp).coerceAtLeast(180.dp)
            ReadableWidth(Modifier.fillMaxHeight()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Space.xs, end = Space.xs, bottom = Space.xxxl),
                    verticalArrangement = Arrangement.spacedBy(Space.xxs),
                ) {
                    item(key = "header") { header(Modifier.padding(horizontal = Space.md)) }
                    item(key = "page") {
                        page(pageHeight, Modifier.fillMaxWidth().padding(start = Space.md, end = Space.md, top = Space.sm, bottom = Space.lg))
                    }
                    diaryPages(pages, selectedDay, settings, onSelectDay)
                }
            }
        }
    }
}

/** The pages, newest first, under their label. */
private fun LazyListScope.diaryPages(
    pages: List<DiaryEntryEntity>,
    selectedDay: Long,
    settings: AppSettings,
    onSelectDay: (Long) -> Unit,
) {
    item(key = "pages") {
        SectionLabel(localizedText("Pages"), count = pages.size.takeIf { it > 0 }, modifier = Modifier.padding(horizontal = Space.md))
    }
    if (pages.isEmpty()) {
        item(key = "none") {
            EmptyState(
                title = localizedText("No diary entries yet"),
                icon = Icons.Rounded.AutoStories,
                body = localizedText("Write a few lines about today. The page saves itself."),
            )
        }
    }
    items(pages, key = DiaryEntryEntity::epochDay) { entry ->
        ListRow(
            title = diaryDayLabel(entry.epochDay, settings),
            titleStyle = MaterialTheme.typography.titleSmall,
            supporting = diaryPreview(entry.body),
            maxTitleLines = 1,
            selected = entry.epochDay == selectedDay,
            onClick = { onSelectDay(entry.epochDay) },
        )
    }
}

@Composable
private fun DiaryDayHeader(
    selectedDay: Long,
    today: Long,
    settings: AppSettings,
    status: DiarySaveStatus,
    hasPage: Boolean,
    onSelectDay: (Long) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = uiLocale(LocalUiLanguage.current)
    val date = LocalDate.ofEpochDay(selectedDay)
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                Text(
                    UserFormatting.formatWeekday(date.dayOfWeek, locale, TextStyle.FULL),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(UserFormatting.formatDate(date, settings.dateFormat, locale), style = MaterialTheme.typography.bodyMedium, color = quiet)
            }
            IconButton(onClick = { onSelectDay(selectedDay - 1) }) { Icon(Icons.Rounded.ChevronLeft, localizedText("Previous day")) }
            IconButton(onClick = { onSelectDay(selectedDay + 1) }) { Icon(Icons.Rounded.ChevronRight, localizedText("Next day")) }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                localizedText(
                    when (status) {
                        DiarySaveStatus.Empty -> "Saved as you type"
                        DiarySaveStatus.Saving -> "Saving…"
                        DiarySaveStatus.Saved -> "Saved"
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = quiet,
                modifier = Modifier.weight(1f),
            )
            if (selectedDay != today) TextButton(onClick = { onSelectDay(today) }) { Text(localizedText("Today")) }
            if (hasPage) IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, localizedText("Delete diary entry"), tint = quiet) }
        }
    }
}

/** A day as the list and the confirmation show it: the date in the user's format and the weekday. */
@Composable
private fun diaryDayLabel(epochDay: Long, settings: AppSettings): String {
    val locale = uiLocale(LocalUiLanguage.current)
    val date = LocalDate.ofEpochDay(epochDay)
    return UserFormatting.formatDate(date, settings.dateFormat, locale) + "  " + UserFormatting.formatWeekday(date.dayOfWeek, locale, TextStyle.FULL)
}
