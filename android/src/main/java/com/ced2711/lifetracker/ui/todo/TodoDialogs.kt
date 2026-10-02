package com.ced2711.lifetracker.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.categoryPathLabel
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog
import com.ced2711.lifetracker.ui.components.ConfirmDialog
import com.ced2711.lifetracker.ui.components.EditorSheet
import com.ced2711.lifetracker.ui.components.FieldLabel
import com.ced2711.lifetracker.ui.design.Dot
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.ListRow
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.design.priorityColor
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.theme.LifeTheme

/* The smaller sheets and questions of the Todo screen: filters, categories, tags, confirmations. */

internal fun TodoPriority.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

internal fun categoryPaths(categories: List<CategoryEntity>): Map<Long, String> {
    val names = categories.associate { it.id to it.name }
    val parents = categories.associate { it.id to it.parentId }
    return categories.associate { it.id to (categoryPathLabel(it.id, names, parents) ?: it.name) }
}

/** A field that opens a menu of choices; looks like the text fields around it. */
@Composable
internal fun ChoiceField(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                .heightIn(min = 44.dp)
                .padding(horizontal = Space.md, vertical = Space.sm)
                .alpha(if (enabled) 1f else 0.45f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

/** Pills to choose one priority; [allowAny] adds "All" for filters (null). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PriorityPills(selected: TodoPriority?, onSelect: (TodoPriority?) -> Unit, allowAny: Boolean, enabled: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (allowAny) Pill(localizedText("All"), selected == null, { onSelect(null) }, exclusive = true, enabled = enabled)
        TodoPriority.entries.forEach { option ->
            val color = priorityColor(option)
            Pill(
                localizedText(option.displayName()),
                selected == option,
                { onSelect(option) },
                exclusive = true,
                enabled = enabled,
                leading = color?.let { { Dot(it) } },
            )
        }
    }
}

/** Sort order and the filters that are needed less often than the quick views. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodoFilterSheet(
    filter: TodoFilter,
    onFilterChange: (TodoFilter) -> Unit,
    categories: List<CategoryEntity>,
    tags: List<String>,
    onManageCategories: () -> Unit,
    onManageTags: () -> Unit,
    onClose: () -> Unit,
) {
    val paths = remember(categories) { categoryPaths(categories) }
    EditorSheet(
        title = localizedText("Filter and sort"),
        onClose = onClose,
        actionLabel = localizedText("Done"),
        onAction = onClose,
        footer = {
            TextButton(onClick = { onFilterChange(filter.cleared().copy(search = filter.search)) }, enabled = filter.changedInSheet > 0) {
                Text(localizedText("Clear filters"))
            }
        },
    ) {
        FieldLabel(localizedText("Sort"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            TodoSort.entries.forEach { option ->
                Pill(localizedText(option.label), filter.sort == option, { onFilterChange(filter.copy(sort = option)) }, exclusive = true)
            }
        }
        if (filter.sort == TodoSort.CUSTOM) {
            Text(
                localizedText("Moves follow the visible filtered list; hidden tasks keep their relative order."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FieldLabel(localizedText("Priority"))
        PriorityPills(filter.priority, { onFilterChange(filter.copy(priority = it)) }, allowAny = true)
        FieldLabel(localizedText("Category")) {
            TextButton(onClick = onManageCategories) { Text(localizedText("Manage")) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            fun toggle(key: Long) = onFilterChange(filter.copy(categories = toggleTodoCategoryFilter(filter.categories, key)))
            Pill(localizedText("All categories"), filter.allCategories, { toggle(ALL_CATEGORIES_FILTER_KEY) })
            Pill(localizedText("Uncategorized"), UNCATEGORIZED_FILTER_KEY in filter.categories, { toggle(UNCATEGORIZED_FILTER_KEY) })
            categories.sortedBy { paths[it.id] }.forEach { category ->
                Pill(paths[category.id] ?: category.name, category.id in filter.categories, { toggle(category.id) })
            }
        }
        FieldLabel(localizedText("Tag")) {
            TextButton(onClick = onManageTags, enabled = tags.isNotEmpty()) { Text(localizedText("Manage")) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Pill(localizedText("All tags"), filter.tag == null, { onFilterChange(filter.copy(tag = null)) }, exclusive = true)
            tags.forEach { tag ->
                Pill("#$tag", filter.tag.equals(tag, ignoreCase = true), { onFilterChange(filter.copy(tag = tag)) }, exclusive = true)
            }
        }
        Spacer(Modifier.size(Space.lg))
    }
}

@Composable
internal fun CategoryManagerSheet(
    categories: List<CategoryEntity>,
    onClose: () -> Unit,
    onAdd: (String, Long?) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var parentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val paths = remember(categories) { categoryPaths(categories) }
    val language = LocalUiLanguage.current

    EditorSheet(title = localizedText("Categories"), onClose = onClose, actionLabel = localizedText("Done"), onAction = onClose) {
        FieldLabel(localizedText("New category"))
        LifeTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = localizedText("Category name"))
        ChoiceField(parentId?.let { id -> paths[id]?.let { insideLabel(it, language) } } ?: localizedText("No parent"), Modifier.fillMaxWidth()) { close ->
            DropdownMenuItem(text = { Text(localizedText("No parent")) }, onClick = { parentId = null; close() })
            categories.sortedBy { paths[it.id] }.forEach { category ->
                DropdownMenuItem(text = { Text(paths[category.id] ?: category.name) }, onClick = { parentId = category.id; close() })
            }
        }
        Button(onClick = { onAdd(name, parentId); name = "" }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(localizedText("Add category"))
        }
        FieldLabel(localizedText("Categories"))
        if (categories.isEmpty()) {
            Text(localizedText("No categories yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            categories.sortedBy { paths[it.id] }.forEach { category ->
                ListRow(
                    title = paths[category.id] ?: category.name,
                    trailing = {
                        IconButton(onClick = { deletingId = category.id }) {
                            Icon(Icons.Rounded.DeleteOutline, deleteLabel(category.name, language), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            }
        }
    }

    categories.firstOrNull { it.id == deletingId }?.let { category ->
        ConfirmDialog(
            title = deleteQuestion(category.name, language),
            text = localizedText("Tasks will become Uncategorized. Child categories will move to this category's parent."),
            confirmLabel = localizedText("Delete"),
            destructive = true,
            onConfirm = {
                if (parentId == category.id) parentId = null
                onDelete(category.id)
                deletingId = null
            },
            onDismiss = { deletingId = null },
        )
    }
}

@Composable
internal fun TagManagerSheet(
    tags: List<String>,
    onClose: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val language = LocalUiLanguage.current

    EditorSheet(title = localizedText("Tags"), onClose = onClose, actionLabel = localizedText("Done"), onAction = onClose) {
        Text(
            localizedText("Changes apply to existing tasks and repeating rules, including future occurrences."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tags.isEmpty()) {
            Text(localizedText("No tags yet."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        tags.forEach { tag ->
            ListRow(
                title = "#$tag",
                trailing = {
                    IconButton(onClick = { renaming = tag; renameText = tag }) {
                        Icon(Icons.Rounded.Edit, renameLabel(tag, language), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { deleting = tag }) {
                        Icon(Icons.Rounded.DeleteOutline, deleteLabel("#$tag", language), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        }
    }

    renaming?.let { source ->
        val hasComma = ',' in renameText
        HingeSafeAlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(renameLabel(source, language)) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    LifeTextField(renameText, { renameText = it }, Modifier.fillMaxWidth(), placeholder = localizedText("Tag name"), isError = hasComma)
                    if (hasComma) {
                        Text(localizedText("A tag name cannot contain commas."), style = MaterialTheme.typography.bodySmall, color = LifeTheme.colors.danger)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(localizedText("Cancel")) } },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank() && !hasComma, onClick = { onRename(source, renameText); renaming = null }) {
                    Text(localizedText("Rename"))
                }
            },
        )
    }

    deleting?.let { tag ->
        ConfirmDialog(
            title = deleteQuestion("#$tag", language),
            text = localizedText("This removes the tag from all tasks and repeating rules."),
            confirmLabel = localizedText("Delete"),
            destructive = true,
            onConfirm = { onDelete(tag); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

/** Finishing a todo that still has open subtasks: with or without them. */
@Composable
internal fun CompleteWithSubtasksDialog(onDismiss: () -> Unit, onTaskOnly: () -> Unit, onWithSubtasks: () -> Unit) {
    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("Complete task?")) },
        text = { Text(localizedText("It still has open subtasks.")) },
        dismissButton = { TextButton(onClick = onTaskOnly) { Text(localizedText("Task only")) } },
        confirmButton = { Button(onClick = onWithSubtasks) { Text(localizedText("Task + subtasks")) } },
    )
}

/** Deleting one occurrence of a repeating todo: only this one, or this and the ones after it. */
@Composable
internal fun RecurringDeleteDialog(onDismiss: () -> Unit, onOnlyThis: () -> Unit, onThisAndFuture: () -> Unit) {
    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("Delete repeating task?")) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(localizedText("Past occurrences are not changed."))
                OutlinedButton(onClick = onOnlyThis, modifier = Modifier.fillMaxWidth()) { Text(localizedText("Only this occurrence")) }
                OutlinedButton(onClick = onThisAndFuture, modifier = Modifier.fillMaxWidth()) { Text(localizedText("This and future occurrences")) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(localizedText("Cancel")) } },
    )
}

internal fun deleteLabel(name: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "删除 $name"
    UiLanguage.ENGLISH -> "Delete $name"
}

private fun deleteQuestion(name: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "删除 $name？"
    UiLanguage.ENGLISH -> "Delete $name?"
}

private fun renameLabel(tag: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "重命名 #$tag"
    UiLanguage.ENGLISH -> "Rename #$tag"
}

private fun insideLabel(parent: String, language: UiLanguage): String = when (language) {
    UiLanguage.SIMPLIFIED_CHINESE -> "上级：$parent"
    UiLanguage.ENGLISH -> "Inside $parent"
}
