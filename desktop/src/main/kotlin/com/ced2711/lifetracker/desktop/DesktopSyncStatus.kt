package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class DesktopSyncIndicator { OFF, SYNCING, NEEDS_ATTENTION, PENDING, UP_TO_DATE }

fun desktopSyncIndicator(state: DesktopCloudUiState): DesktopSyncIndicator = when {
    !state.connected -> DesktopSyncIndicator.OFF
    state.syncing -> DesktopSyncIndicator.SYNCING
    state.needsSignIn || state.conflict != null || state.lastSyncFailed -> DesktopSyncIndicator.NEEDS_ATTENTION
    state.pendingChanges || state.lastSyncAt == null -> DesktopSyncIndicator.PENDING
    else -> DesktopSyncIndicator.UP_TO_DATE
}

/**
 * Sync at the bottom of the sidebar. Clicking syncs both ways; when sync needs attention (or is
 * not set up) it opens the sync settings instead. [expanded] shows the state in words too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopSyncStatusButton(
    state: DesktopCloudUiState,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit,
    expanded: Boolean = false,
) {
    val indicator = desktopSyncIndicator(state)
    val summary = desktopText(
        when (indicator) {
            DesktopSyncIndicator.OFF -> "Sync is off"
            DesktopSyncIndicator.SYNCING -> "Syncing…"
            DesktopSyncIndicator.NEEDS_ATTENTION -> if (state.needsSignIn) "Sign in to GitHub again" else "Sync needs attention"
            DesktopSyncIndicator.PENDING -> "Changes not synced yet"
            DesktopSyncIndicator.UP_TO_DATE -> "Up to date"
        },
    )
    val detail = when (indicator) {
        DesktopSyncIndicator.OFF -> desktopText("Set up in Settings")
        else -> state.lastSyncAt?.let { "${desktopText("Last synced")} ${formatSyncTime(it)}" } ?: desktopText("Never synced")
    }
    val opensSettings = indicator == DesktopSyncIndicator.NEEDS_ATTENTION || indicator == DesktopSyncIndicator.OFF
    val hint = desktopText(if (opensSettings) "Click to open sync settings" else "Click to sync both ways now")
    val tint = when (indicator) {
        DesktopSyncIndicator.NEEDS_ATTENTION -> LifeTheme.colors.danger
        DesktopSyncIndicator.UP_TO_DATE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon: @Composable () -> Unit = {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (indicator) {
                DesktopSyncIndicator.SYNCING -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                DesktopSyncIndicator.NEEDS_ATTENTION -> Icon(Icons.Rounded.SyncProblem, null, tint = tint, modifier = Modifier.size(20.dp))
                DesktopSyncIndicator.PENDING -> Icon(Icons.Rounded.CloudUpload, null, tint = tint, modifier = Modifier.size(20.dp))
                DesktopSyncIndicator.UP_TO_DATE -> Icon(Icons.Rounded.CloudDone, null, tint = tint, modifier = Modifier.size(20.dp))
                DesktopSyncIndicator.OFF -> Icon(Icons.Rounded.CloudOff, null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
    }
    val clickable = Modifier
        .clip(RoundedCornerShape(10.dp))
        .clickable(enabled = indicator != DesktopSyncIndicator.SYNCING, role = Role.Button, onClick = if (opensSettings) onOpenSettings else onSync)
        .semantics { contentDescription = "$summary. $detail. $hint" }
    if (expanded) {
        Row(
            clickable.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon()
            Column(Modifier.weight(1f)) {
                Text(summary, style = MaterialTheme.typography.labelLarge, color = if (indicator == DesktopSyncIndicator.NEEDS_ATTENTION) tint else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        TooltipArea(
            tooltip = {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                    Text("$summary\n$detail\n$hint", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelMedium)
                }
            },
        ) {
            Box(clickable.size(40.dp), contentAlignment = Alignment.Center) { icon() }
        }
    }
}

private fun formatSyncTime(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val pattern = if (time.toLocalDate() == LocalDate.now()) "HH:mm" else "MM-dd HH:mm"
    return time.format(DateTimeFormatter.ofPattern(pattern))
}
