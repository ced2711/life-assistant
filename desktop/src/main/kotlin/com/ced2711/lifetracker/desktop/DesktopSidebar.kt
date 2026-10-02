package com.ced2711.lifetracker.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.domain.model.AppIdentity
import com.ced2711.lifetracker.ui.theme.LifeTheme

data class SidebarItem(val label: String, val icon: ImageVector, val badge: String? = null, val badgeAlert: Boolean = false)

/**
 * The desktop navigation: app name, the modules with their counts and Ctrl+number shortcuts, and
 * at the bottom the sync status, Vault and Settings. [expanded] false folds it to a rail of icons.
 */
@Composable
fun DesktopSidebar(
    expanded: Boolean,
    destinations: List<SidebarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    vaultSelected: Boolean,
    settingsSelected: Boolean,
    onVault: () -> Unit,
    onSettings: () -> Unit,
    syncStatus: @Composable () -> Unit,
    onToggle: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxHeight().width(if (expanded) 232.dp else 72.dp),
    ) {
        Column(
            Modifier.fillMaxHeight().padding(horizontal = if (expanded) 12.dp else 10.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(40.dp).padding(start = if (expanded) 6.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
            ) {
                if (expanded) {
                    AppMark()
                    Text(
                        desktopText(AppIdentity.NAME),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onToggle != null) {
                        IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.MenuOpen, desktopText("Fold menu (Ctrl+B)"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                } else if (onToggle != null) {
                    IconButton(onClick = onToggle) {
                        Icon(Icons.Rounded.Menu, desktopText("Unfold menu (Ctrl+B)"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    AppMark()
                }
            }
            Spacer(Modifier.height(14.dp))
            destinations.forEachIndexed { index, item ->
                SidebarEntry(item, selected = index == selectedIndex, expanded = expanded, shortcut = "Ctrl+${index + 1}".takeIf { index < 9 }) { onSelect(index) }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center) { syncStatus() }
            SidebarEntry(SidebarItem("Vault", Icons.Rounded.Lock), vaultSelected, expanded, shortcut = null, onClick = onVault)
            SidebarEntry(SidebarItem("Settings", Icons.Rounded.Settings), settingsSelected, expanded, shortcut = "Ctrl+,", onClick = onSettings)
        }
    }
}

@Composable
private fun AppMark() {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.TaskAlt, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarEntry(item: SidebarItem, selected: Boolean, expanded: Boolean, shortcut: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val container by animateColorAsState(
        when {
            selected -> LifeTheme.colors.accentSoft
            hovered -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> Color.Transparent
        },
        tween(120),
        label = "sidebar",
    )
    val content = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val label = desktopText(item.label)
    val row: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(container)
                .hoverable(interaction)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .semantics { contentDescription = listOfNotNull(label, item.badge, shortcut).joinToString(", ") }
                .padding(horizontal = if (expanded) 10.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        ) {
            Box {
                Icon(item.icon, null, tint = content, modifier = Modifier.size(20.dp))
                if (!expanded && item.badge != null) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(start = 14.dp).size(7.dp).clip(RoundedCornerShape(50))
                            .background(if (item.badgeAlert) LifeTheme.colors.danger else MaterialTheme.colorScheme.primary),
                    )
                }
            }
            if (expanded) {
                Text(
                    label,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    hovered && shortcut != null -> Text(shortcut, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    item.badge != null -> Text(
                        item.badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.badgeAlert) LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (expanded) {
        row()
    } else {
        // The folded rail names each icon on hover.
        TooltipArea(
            tooltip = {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                    Text(
                        listOfNotNull(label, item.badge?.let { "· $it" }, shortcut?.let { "  $it" }).joinToString(" "),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            },
            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.CenterEnd, alignment = Alignment.CenterEnd, offset = DpOffset(8.dp, 0.dp)),
        ) { row() }
    }
}
