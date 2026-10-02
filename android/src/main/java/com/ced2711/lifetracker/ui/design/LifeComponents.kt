package com.ced2711.lifetracker.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.ui.theme.LifeTheme

/*
 * Building blocks shared by the Android and desktop apps. Screens are made of these so both
 * apps look and behave alike: page titles, sections, rows, round check marks, segmented
 * controls, chips, money, stats and empty states. See docs/DESIGN.md.
 */

/** Spacing steps (4dp grid). */
object Space {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
}

/** A page's big title with an optional line under it and actions on the right. */
@Composable
fun PageTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** A small label above a group, with an optional count and action. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 32.dp).padding(top = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.semantics { heading() },
        )
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = Space.sm),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** A quiet rounded panel that groups rows; no shadow, just a step in surface colour. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = color) {
        Column(Modifier.padding(padding), content = content)
    }
}

/**
 * One line in a list: optional leading element, a title with an optional second line, and an
 * optional trailing element. The whole row is the touch target.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    struck: Boolean = false,
    selected: Boolean = false,
    maxTitleLines: Int = 2,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val background by animateColorAsState(
        if (selected) LifeTheme.colors.accentSoft else Color.Transparent,
        animationSpec = tween(150),
        label = "row",
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = Space.md, vertical = Space.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.padding(end = Space.md), contentAlignment = Alignment.Center) { leading() }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = titleStyle,
                color = if (struck) titleColor.copy(alpha = 0.5f) else titleColor,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
                maxLines = maxTitleLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!supporting.isNullOrBlank()) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            extra?.invoke(this)
        }
        if (trailing != null) {
            Row(
                Modifier.padding(start = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                content = trailing,
            )
        }
    }
}

/**
 * A round check mark, the app's way to finish things: an empty ring that fills with the given
 * colour (the accent, or the priority colour) and shows a tick when done.
 */
@Composable
fun CheckCircle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 22.dp,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val fill by animateColorAsState(if (checked) color else Color.Transparent, tween(160), label = "check-fill")
    val tick by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "check-tick")
    val ring = if (checked) color else color.copy(alpha = if (enabled) 0.75f else 0.35f)
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, radius = size),
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .size(maxOf(size + 18.dp, 40.dp))
            .then(toggle)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(fill)
                .border(BorderStroke(1.75.dp, ring), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(size * 0.72f).scale(tick).alpha(tick),
            )
        }
    }
}

/** The colour a priority is shown in; null for no priority. */
@Composable
fun priorityColor(priority: TodoPriority): Color? = when (priority) {
    TodoPriority.URGENT -> LifeTheme.colors.priorityUrgent
    TodoPriority.HIGH -> LifeTheme.colors.priorityHigh
    TodoPriority.MEDIUM -> LifeTheme.colors.priorityMedium
    TodoPriority.LOW -> LifeTheme.colors.priorityLow
    TodoPriority.NONE -> null
}

/** A small filled dot, for priority and for "something here" marks in calendars. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Money with the right sign and colour; [cents] is always positive. */
@Composable
fun MoneyText(
    cents: Long,
    type: LedgerType?,
    formatted: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    showSign: Boolean = true,
) {
    val color = when (type) {
        LedgerType.INCOME -> LifeTheme.colors.income
        LedgerType.EXPENSE -> LifeTheme.colors.expense
        null -> if (cents < 0) LifeTheme.colors.expense else MaterialTheme.colorScheme.onSurface
    }
    val sign = when {
        !showSign -> ""
        type == LedgerType.INCOME -> "+"
        type == LedgerType.EXPENSE -> "−"
        cents < 0 -> "−"
        else -> ""
    }
    Text(
        sign + formatted,
        modifier = modifier,
        style = style.copy(fontFeatureSettings = "tnum", fontWeight = style.fontWeight ?: FontWeight.Medium),
        color = color,
        maxLines = 1,
    )
}

/**
 * A row of equal choices where exactly one is on (a segmented control). Each choice is a radio
 * button for screen readers.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.surfaceBright else Color.Transparent,
                tween(150),
                label = "segment",
            )
            Box(
                Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .clip(RoundedCornerShape(9.dp))
                    .background(background)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .defaultMinSize(minHeight = 34.dp)
                    .padding(horizontal = Space.md, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A pill to switch something on or off (filters, options). Selected pills use the accent; they
 * are check boxes (or radio buttons when [exclusive]) for screen readers.
 */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    count: Int? = null,
    exclusive: Boolean = false,
    enabled: Boolean = true,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tween(150),
        label = "pill",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .then(
                if (exclusive) {
                    Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                } else {
                    Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
                },
            )
            .defaultMinSize(minHeight = 34.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            leading?.invoke()
            Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (count != null) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.7f))
            }
        }
    }
}

/** A tiny rounded label, such as "Overdue" or "Stopped". */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, background: Color = color.copy(alpha = 0.14f)) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
    )
}

/** A number that matters, with what it is underneath. */
@Composable
fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    caption: String? = null,
    valueStyle: TextStyle = MaterialTheme.typography.headlineSmall,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = valueStyle.copy(fontFeatureSettings = "tnum"), color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** What a place looks like when there is nothing in it yet, with the one thing to do next. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Space.xxxl, horizontal = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(Space.xs))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Space.xs))
            action()
        }
    }
}

/** A thin line between rows inside a panel, indented to the text. */
@Composable
fun RowDivider(modifier: Modifier = Modifier, inset: Dp = Space.md) {
    Box(modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(LifeTheme.colors.divider))
}

/** An icon in a soft round tile, for list rows that lead with a symbol. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 36.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A slim progress bar with rounded ends. */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 6.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(animated).height(height).clip(CircleShape).background(color))
    }
}

/** Centres a column and limits its width, so pages stay readable on wide screens. */
@Composable
fun ReadableWidth(modifier: Modifier = Modifier, maxWidth: Dp = 760.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth(), content = content)
    }
}

/** Equal-width spacer used between side-by-side panels. */
@Composable
fun RowScope.Gap(width: Dp = Space.md) = Spacer(Modifier.width(width))

/**
 * The app's text field: a soft rounded box without underline or outline, with an optional icon,
 * prefix (such as "$") and placeholder. Single line unless [minLines] is above one.
 */
@Composable
fun LifeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    prefix: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) Int.MAX_VALUE else 1,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(
        when {
            isError -> LifeTheme.colors.danger
            focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else -> Color.Transparent
        },
        tween(120),
        label = "field-border",
    )
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = minLines <= 1 && maxLines <= 1,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .background(background)
                    .border(BorderStroke(1.dp, border), MaterialTheme.shapes.medium)
                    .heightIn(min = 44.dp)
                    .padding(horizontal = Space.md, vertical = if (minLines > 1) Space.md else Space.sm),
                verticalAlignment = if (minLines > 1) Alignment.Top else Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Space.sm).size(20.dp))
                }
                if (prefix != null) {
                    Text(prefix, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 2.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}
