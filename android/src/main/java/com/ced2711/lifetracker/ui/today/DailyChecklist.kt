package com.ced2711.lifetracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.ChecklistItemEntity
import com.ced2711.lifetracker.domain.model.MAX_CHECKLIST_TITLE_LENGTH
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog
import com.ced2711.lifetracker.ui.components.ConfirmDialog
import com.ced2711.lifetracker.ui.design.CheckCircle
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.theme.LifeTheme

/** What the daily checklist on Today needs: its items, what is ticked today, and the changes. */
internal data class DailyChecklistState(
    val items: List<ChecklistItemEntity>,
    val checkedToday: Set<Long>,
    val onToggle: (itemId: Long, checked: Boolean) -> Unit,
    val onAdd: (String) -> Unit,
    val onRename: (itemId: Long, title: String) -> Unit,
    val onDelete: (itemId: Long) -> Unit,
    val onReorder: (itemIds: List<Long>) -> Unit,
) {
    val done: Int get() = items.count { it.id in checkedToday }
    val allDone: Boolean get() = items.isNotEmpty() && done == items.size
}

/**
 * The daily checklist: things done every day (brush teeth, shower, check homework). They are not
 * todos and never appear in Todo or the calendar; ticks count for today only, so every morning
 * starts with none ticked. Once everything is ticked the list folds into its heading. Adding,
 * renaming, reordering and removing items happen in Edit, so the everyday view is just ticking.
 */
internal fun LazyListScope.dailyChecklistItems(state: DailyChecklistState, ui: DailyChecklistUi) {
    val editing = ui.editing
    val folded = state.allDone && !editing && !ui.showDone
    item(key = "checklist-header") {
        SectionLabel(
            text = localizedText("Daily checklist"),
            modifier = Modifier.padding(start = Space.md),
            color = if (state.allDone && !editing) LifeTheme.colors.income else MaterialTheme.colorScheme.onSurfaceVariant,
            trailing = {
                if (state.items.isNotEmpty()) {
                    Text(
                        "${state.done}/${state.items.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Space.xs),
                    )
                }
                if (state.allDone && !editing) {
                    TextButton(onClick = { ui.showDone = !ui.showDone }) { Text(localizedText(if (ui.showDone) "Hide" else "Show")) }
                }
                if (state.items.isNotEmpty()) {
                    TextButton(onClick = { ui.editing = !editing }) { Text(localizedText(if (editing) "Done" else "Edit")) }
                }
            },
        )
    }
    if (!folded) {
        state.items.forEachIndexed { index, item ->
            item(key = "checklist-${item.id}") {
                if (editing) {
                    EditingRow(item, index, state, ui)
                } else {
                    val checked = item.id in state.checkedToday
                    ListRow(
                        title = item.title,
                        struck = checked,
                        leading = {
                            CheckCircle(
                                checked = checked,
                                onCheckedChange = { state.onToggle(item.id, it) },
                                contentDescription = item.title,
                            )
                        },
                        onClick = { state.onToggle(item.id, !checked) },
                    )
                }
            }
        }
    }
    if (state.items.isEmpty() || editing) {
        item(key = "checklist-add") {
            Column(Modifier.padding(horizontal = Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (state.items.isEmpty()) {
                    Text(
                        localizedText("Things you do every day, like brushing your teeth. Ticks start fresh every morning."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.md),
                    )
                }
                LifeTextField(
                    value = ui.newItem,
                    onValueChange = { ui.newItem = it.take(MAX_CHECKLIST_TITLE_LENGTH) },
                    placeholder = localizedText("Add to the checklist"),
                    leadingIcon = Icons.Rounded.Add,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        ui.newItem.trim().takeIf(String::isNotEmpty)?.let(state.onAdd)
                        ui.newItem = ""
                    }),
                )
            }
        }
    }
}

@Composable
private fun EditingRow(item: ChecklistItemEntity, index: Int, state: DailyChecklistState, ui: DailyChecklistUi) {
    val language = LocalUiLanguage.current
    fun move(delta: Int) {
        val ids = state.items.map { it.id }.toMutableList()
        val target = index + delta
        if (target !in ids.indices) return
        ids[index] = ids[target].also { ids[target] = ids[index] }
        state.onReorder(ids)
    }
    ListRow(
        title = item.title,
        supporting = localizedText("Tap to rename"),
        onClick = { ui.renaming = item.id; ui.renameText = item.title },
        trailing = {
            IconButton(onClick = { move(-1) }, enabled = index > 0, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, localizedText("Move up"))
            }
            IconButton(onClick = { move(1) }, enabled = index < state.items.lastIndex, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, localizedText("Move down"))
            }
            IconButton(onClick = { ui.deleting = item.id }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.DeleteOutline, removeLabel(item.title, language), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/** The checklist's screen state, kept across rotation and folding. */
internal class DailyChecklistUi {
    var editing by mutableStateOf(false)
    var showDone by mutableStateOf(false)
    var newItem by mutableStateOf("")
    var renaming by mutableStateOf<Long?>(null)
    var renameText by mutableStateOf("")
    var deleting by mutableStateOf<Long?>(null)

    companion object {
        val Saver = listSaver<DailyChecklistUi, Any>(
            save = { listOf(it.editing, it.showDone, it.newItem, it.renaming ?: NONE, it.renameText, it.deleting ?: NONE) },
            restore = { saved ->
                DailyChecklistUi().apply {
                    editing = saved[0] as Boolean
                    showDone = saved[1] as Boolean
                    newItem = saved[2] as String
                    renaming = (saved[3] as Long).takeIf { it != NONE }
                    renameText = saved[4] as String
                    deleting = (saved[5] as Long).takeIf { it != NONE }
                }
            },
        )
        private const val NONE = -1L
    }
}

@Composable
internal fun rememberDailyChecklistUi(): DailyChecklistUi = rememberSaveable(saver = DailyChecklistUi.Saver) { DailyChecklistUi() }

/** The rename and remove questions of the checklist; put next to the list. */
@Composable
internal fun DailyChecklistDialogs(state: DailyChecklistState, ui: DailyChecklistUi) {
    val language = LocalUiLanguage.current
    ui.renaming?.let { itemId ->
        HingeSafeAlertDialog(
            onDismissRequest = { ui.renaming = null },
            title = { Text(localizedText("Rename")) },
            text = {
                LifeTextField(
                    value = ui.renameText,
                    onValueChange = { ui.renameText = it.take(MAX_CHECKLIST_TITLE_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        state.onRename(itemId, ui.renameText)
                        ui.renaming = null
                    }),
                )
            },
            dismissButton = { TextButton(onClick = { ui.renaming = null }) { Text(localizedText("Cancel")) } },
            confirmButton = {
                TextButton(enabled = ui.renameText.isNotBlank(), onClick = { state.onRename(itemId, ui.renameText); ui.renaming = null }) {
                    Text(localizedText("Save"))
                }
            },
        )
    }
    ui.deleting?.let { itemId ->
        val title = state.items.firstOrNull { it.id == itemId }?.title.orEmpty()
        ConfirmDialog(
            title = removeQuestion(title, language),
            text = localizedText("It leaves the daily checklist on every device."),
            confirmLabel = localizedText("Remove"),
            destructive = true,
            onConfirm = { state.onDelete(itemId); ui.deleting = null },
            onDismiss = { ui.deleting = null },
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
