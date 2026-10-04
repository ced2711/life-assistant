package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.backup.BackupSnapshot
import com.ced2711.lifetracker.data.local.ChecklistItemEntity
import com.ced2711.lifetracker.domain.model.MAX_CHECKLIST_TITLE_LENGTH
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.design.CheckCircle
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * The daily checklist on Today: things done every day (brush teeth, shower, check homework). They
 * are not todos and never appear in Todo or the calendar; ticks count for today only, so every
 * morning starts with none ticked. Once everything is ticked the panel folds to one line. Adding,
 * renaming, reordering and removing happen in Edit.
 */
@Composable
internal fun DailyChecklistPanel(snapshot: BackupSnapshot, store: DesktopDataStore) {
    val scope = rememberSafeCoroutineScope()
    val language = LocalUiLanguage.current
    val today = LocalDate.now().toEpochDay()
    val items = remember(snapshot.checklistItems) { snapshot.checklistItems.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
    val checked = remember(snapshot.checklistChecks, today) {
        snapshot.checklistChecks.filter { it.epochDay == today }.mapTo(HashSet()) { it.itemId }
    }
    val done = items.count { it.id in checked }
    val allDone = items.isNotEmpty() && done == items.size
    var editing by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    var newItem by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ChecklistItemEntity?>(null) }
    var removing by remember { mutableStateOf<ChecklistItemEntity?>(null) }
    val folded = allDone && !editing && !showDone

    fun add() {
        val title = newItem.trim()
        if (title.isEmpty()) return
        newItem = ""
        scope.launch { store.addChecklistItem(title) }
    }

    fun move(index: Int, delta: Int) {
        val ids = items.map { it.id }.toMutableList()
        val target = index + delta
        if (target !in ids.indices) return
        ids[index] = ids[target].also { ids[target] = ids[index] }
        scope.launch { store.reorderChecklist(ids) }
    }

    Panel(padding = PaddingValues(start = Space.xs, end = Space.xs, top = Space.md, bottom = Space.sm)) {
        Row(Modifier.padding(start = Space.lg, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Checklist, null, tint = if (allDone) LifeTheme.colors.income else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(desktopText("Daily checklist"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Space.sm).weight(1f))
            if (items.isNotEmpty()) {
                Text(
                    "$done / ${items.size}",
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    color = if (allDone) LifeTheme.colors.income else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (allDone) FontWeight.SemiBold else null,
                )
                TextButton(onClick = { editing = !editing }) { Text(desktopText(if (editing) "Finish" else "Edit")) }
            }
        }
        if (folded) {
            Row(Modifier.padding(start = Space.lg, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    desktopText("All done for today. It starts fresh tomorrow."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showDone = true }) { Text(desktopText("Show")) }
            }
        } else {
            items.forEachIndexed { index, item ->
                if (editing) {
                    ListRow(
                        title = item.title,
                        supporting = desktopText("Click to rename"),
                        onClick = { renaming = item },
                        trailing = {
                            IconButton(onClick = { move(index, -1) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowUp, desktopText("Move up"), Modifier.size(18.dp))
                            }
                            IconButton(onClick = { move(index, 1) }, enabled = index < items.lastIndex, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.KeyboardArrowDown, desktopText("Move down"), Modifier.size(18.dp))
                            }
                            IconButton(onClick = { removing = item }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, removeLabel(item.title, language), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                } else {
                    val isChecked = item.id in checked
                    ListRow(
                        title = item.title,
                        struck = isChecked,
                        leading = {
                            CheckCircle(
                                checked = isChecked,
                                onCheckedChange = { value -> scope.launch { store.setChecklistChecked(item.id, today, value) } },
                                contentDescription = item.title,
                            )
                        },
                        onClick = { scope.launch { store.setChecklistChecked(item.id, today, !isChecked) } },
                    )
                }
            }
            if (allDone && showDone && !editing) {
                TextButton(onClick = { showDone = false }, modifier = Modifier.padding(start = Space.sm)) { Text(desktopText("Hide")) }
            }
        }
        if (items.isEmpty() || editing) {
            if (items.isEmpty()) {
                Text(
                    desktopText("Things you do every day, like brushing your teeth. Ticks start fresh every morning."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
                )
            }
            Spacer(Modifier.height(Space.xs))
            LifeTextField(
                value = newItem,
                onValueChange = { newItem = it.take(MAX_CHECKLIST_TITLE_LENGTH) },
                placeholder = desktopText("Add to the checklist, then press Enter"),
                leadingIcon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm).onEnter(::add),
            )
        }
    }

    renaming?.let { item ->
        SimpleNameDialog(
            title = desktopText("Rename"),
            initial = item.title,
            onDismiss = { renaming = null },
            onSave = { title ->
                renaming = null
                scope.launch { store.renameChecklistItem(item.id, title) }
            },
        )
    }
    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(removeQuestion(item.title, language)) },
            text = { Text(desktopText("It leaves the daily checklist on every device.")) },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(desktopText("Cancel")) } },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch { store.deleteChecklistItem(item.id) }
                }) { Text(desktopText("Remove"), color = LifeTheme.colors.danger, fontWeight = FontWeight.SemiBold) }
            },
        )
    }
}

private fun removeLabel(title: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "移除 $title"
    UiLanguage.ENGLISH -> "Remove $title"
}

private fun removeQuestion(title: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "从每日清单移除“$title”？"
    UiLanguage.ENGLISH -> "Remove “$title” from the daily checklist?"
}
