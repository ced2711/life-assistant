package com.ced2711.lifetracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.RowDivider
import com.ced2711.lifetracker.ui.design.SectionLabel
import com.ced2711.lifetracker.ui.design.Space

/*
 * The pieces Settings and Backup & sync are made of: a titled group of rows in a panel, and a row
 * with a title, a quiet line under it and one control. Texts passed in are already translated.
 */

/** A group of settings: a small label, an optional line of explanation, and the rows in a panel. */
@Composable
internal fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel(title, Modifier.padding(horizontal = Space.xs))
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.xs, end = Space.xs, bottom = Space.sm),
            )
        }
        Panel(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.xs), content = content)
    }
}

/** The line between two rows of a group. */
@Composable
internal fun SettingDivider() = RowDivider(inset = Space.lg)

/**
 * One setting: what it is, a quiet line of explanation, and its control at the end. When the
 * control does not fit next to the text (small phones, large fonts, wide controls) it moves to a
 * line of its own under the text instead of squeezing it. [stacked] always puts it there.
 */
@Composable
internal fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leading: (@Composable () -> Unit)? = null,
    stacked: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    control: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.lg, vertical = Space.md),
        // Next to a control under the text, the leading icon belongs to the text, not the middle.
        verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically,
    ) {
        if (leading != null) Box(Modifier.padding(end = Space.md)) { leading() }
        TextWithControl(stacked, Modifier.weight(1f)) {
            Column(Modifier.alpha(if (enabled) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (!supporting.isNullOrBlank()) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = supportingColor)
                }
            }
            Box { control?.invoke() }
        }
    }
}

/** A setting that is switched on or off; the whole row is the switch. */
@Composable
internal fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        supporting = supporting,
        enabled = enabled,
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A setting whose choices always get a line of their own (pills that wrap). */
@Composable
internal fun SettingBlock(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    content: @Composable () -> Unit,
) {
    SettingRow(title = title, supporting = supporting, stacked = true, modifier = modifier, control = content)
}

/** The arrow at the end of a row that opens another page. */
@Composable
internal fun RowChevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
    )
}

/**
 * Text first, control second: side by side when the control leaves the text enough room,
 * otherwise the control goes under the text.
 */
@Composable
private fun TextWithControl(stacked: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = Space.md.roundToPx()
        val control = measurables[1].measure(Constraints(maxWidth = width))
        if (control.width == 0) {
            val text = measurables[0].measure(Constraints(maxWidth = width))
            return@Layout layout(width, text.height) { text.place(0, 0) }
        }
        // The text asks for its own width, but never more than a share of the row.
        val textNeeds = minOf(measurables[0].maxIntrinsicWidth(Constraints.Infinity), (width * 0.42f).toInt())
        if (!stacked && control.width + gap + textNeeds <= width) {
            val text = measurables[0].measure(Constraints(maxWidth = width - control.width - gap))
            val height = maxOf(text.height, control.height)
            layout(width, height) {
                text.place(0, (height - text.height) / 2)
                control.place(width - control.width, (height - control.height) / 2)
            }
        } else {
            val text = measurables[0].measure(Constraints(maxWidth = width))
            val between = Space.sm.roundToPx() + 2
            layout(width, text.height + between + control.height) {
                text.place(0, 0)
                control.place(0, text.height + between)
            }
        }
    }
}

/** One column of groups, or two side by side on wide screens. */
@Composable
internal fun GroupColumns(
    twoColumns: Boolean,
    modifier: Modifier = Modifier,
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
) {
    if (twoColumns) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xxl)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.sm), content = first)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.sm), content = second)
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            first()
            second()
        }
    }
}
