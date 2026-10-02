package com.ced2711.lifetracker.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.ui.design.Dot
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.theme.LifeTheme

/** Page padding on the desktop: generous, the same on every page. */
internal val PagePadding = 32.dp

/** A side column of filters or folders: a quiet background and a list of [FilterEntry]s. */
@Composable
internal fun FilterColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 12.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        content = content,
    )
}

/** A small heading inside a [FilterColumn], with an optional action such as "+". */
@Composable
internal fun FilterHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, top = 20.dp, bottom = 4.dp).height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            desktopText(title),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/**
 * One filter or folder: label, optional colour dot and count. Rename and delete appear on hover,
 * and are also in the entry's right-click menu so they never depend on hovering.
 */
@Composable
internal fun FilterEntry(
    label: String,
    count: Int?,
    selected: Boolean,
    indent: Int = 0,
    emphasize: Boolean = false,
    dot: Color? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> LifeTheme.colors.accentSoft
            hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> Color.Transparent
        },
        tween(120),
        label = "filter",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 10.dp + (indent * 14).dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Dot(dot, Modifier.padding(end = 9.dp))
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (hovered && (onRename != null || onDelete != null)) {
            if (onRename != null) {
                IconButton(onClick = onRename, modifier = Modifier.size(26.dp)) { Icon(Icons.Rounded.Edit, desktopText("Rename"), Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) { Icon(Icons.Rounded.DeleteOutline, desktopText("Delete"), Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else if (count != null && count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = if (emphasize) LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

/** A small label above an editor field. */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(
            desktopText(text),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * The right-hand editor of a page: title with close button, scrolling fields, and a footer for
 * the actions. Esc closes and Ctrl+S saves (see [editorKeys]).
 */
@Composable
internal fun EditorPane(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            headerActions()
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, desktopText("Close (Esc)"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            content = content,
        )
        if (footer != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(LifeTheme.colors.divider))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = footer,
            )
        }
    }
}

/** A text button that opens a menu of choices, showing the current one ("Sort: Deadline"). */
@Composable
internal fun <T> ChoiceMenu(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(label + ": " + optionLabel(selected), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = { if (option == selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) else Spacer(Modifier.width(18.dp)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** A thin vertical line between page columns. */
@Composable
internal fun ColumnDivider() {
    Box(Modifier.fillMaxHeight().width(1.dp).background(LifeTheme.colors.divider))
}

/** Fixed-width spacer helper for rows of fields. */
@Composable
internal fun HSpace(width: Dp) = Spacer(Modifier.width(width))

/** Where a page starts when it is opened next: which Settings topic, which Ledger tab. */
internal object DesktopStartHints {
    @Volatile var settingsSection: String? = null
    @Volatile var ledgerTab: String? = null
}
