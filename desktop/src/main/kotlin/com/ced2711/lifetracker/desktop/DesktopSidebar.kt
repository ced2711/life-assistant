package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.domain.model.AppIdentity

data class SidebarItem(val label: String, val icon: ImageVector, val badge: String? = null)

/**
 * The desktop navigation column: app name and sync status on top, the modules in the user's
 * order with their Ctrl+number shortcuts, and Vault and Settings at the bottom.
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
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxHeight().width(if (expanded) 236.dp else 76.dp)) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 10.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = if (expanded) 8.dp else 0.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.TaskAlt, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                    }
                }
                if (expanded) {
                    Text(
                        desktopText(AppIdentity.NAME),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onToggle != null) IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.MenuOpen, desktopText("Fold menu (Ctrl+B)"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!expanded && onToggle != null) {
                Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onToggle) { Icon(Icons.Default.Menu, desktopText("Unfold menu (Ctrl+B)"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            destinations.forEachIndexed { index, item ->
                SidebarEntry(item, selected = index == selectedIndex, expanded = expanded, shortcut = "Ctrl+${index + 1}".takeIf { index < 9 }) { onSelect(index) }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center) { syncStatus() }
            SidebarEntry(SidebarItem("Vault", Icons.Default.Lock), vaultSelected, expanded, shortcut = null, onClick = onVault)
            SidebarEntry(SidebarItem("Settings", Icons.Default.Settings), settingsSelected, expanded, shortcut = "Ctrl+,", onClick = onSettings)
        }
    }
}

@Composable
private fun SidebarEntry(item: SidebarItem, selected: Boolean, expanded: Boolean, shortcut: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val container = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        hovered -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        modifier = Modifier.fillMaxWidth().height(if (expanded) 42.dp else 52.dp).hoverable(interaction).clickable(onClick = onClick),
    ) {
        if (expanded) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(item.icon, null, tint = content, modifier = Modifier.size(20.dp))
                Text(
                    desktopText(item.label),
                    color = if (selected) content else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 14.dp).weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    hovered && shortcut != null -> Text(shortcut, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    item.badge != null -> Text(item.badge, style = MaterialTheme.typography.labelMedium, color = content)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(item.icon, desktopText(item.label), tint = content, modifier = Modifier.size(22.dp))
                Text(desktopText(item.label), style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
