package com.ced2711.lifetracker.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class DesktopSyncIndicator { SYNCING, NEEDS_ATTENTION, PENDING, UP_TO_DATE }

/** Null hides the indicator: cloud sync is not connected. */
fun desktopSyncIndicator(state: DesktopCloudUiState): DesktopSyncIndicator? = when {
    !state.connected -> null
    state.syncing -> DesktopSyncIndicator.SYNCING
    state.conflict != null || state.lastSyncFailed -> DesktopSyncIndicator.NEEDS_ATTENTION
    state.pendingChanges || state.lastSyncAt == null -> DesktopSyncIndicator.PENDING
    else -> DesktopSyncIndicator.UP_TO_DATE
}

/** Small header status; clicking syncs both ways, or opens the sync settings when it needs attention. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopSyncStatusButton(
    state: DesktopCloudUiState,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val indicator = desktopSyncIndicator(state) ?: return
    val summary = desktopText(
        when (indicator) {
            DesktopSyncIndicator.SYNCING -> "Syncing…"
            DesktopSyncIndicator.NEEDS_ATTENTION -> "Sync needs attention"
            DesktopSyncIndicator.PENDING -> "Changes not synced yet"
            DesktopSyncIndicator.UP_TO_DATE -> "Up to date"
        },
    )
    val lastSync = state.lastSyncAt
        ?.let { "${desktopText("Last synced")} ${formatSyncTime(it)}" }
        ?: desktopText("Never synced")
    val hint = desktopText(
        if (indicator == DesktopSyncIndicator.NEEDS_ATTENTION) "Tap to open sync settings" else "Tap to sync both ways now",
    )
    TooltipArea(
        tooltip = {
            Surface(tonalElevation = 4.dp, shape = MaterialTheme.shapes.small) {
                Text("$summary\n$lastSync\n$hint", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            }
        },
    ) {
        IconButton(
            onClick = if (indicator == DesktopSyncIndicator.NEEDS_ATTENTION) onOpenSettings else onSync,
            enabled = indicator != DesktopSyncIndicator.SYNCING,
            modifier = Modifier.semantics { contentDescription = "$summary. $lastSync. $hint" },
        ) {
            when (indicator) {
                DesktopSyncIndicator.SYNCING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                DesktopSyncIndicator.NEEDS_ATTENTION ->
                    Icon(Icons.Default.SyncProblem, null, tint = MaterialTheme.colorScheme.error)
                DesktopSyncIndicator.PENDING ->
                    Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                DesktopSyncIndicator.UP_TO_DATE ->
                    Icon(Icons.Default.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun formatSyncTime(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val pattern = if (time.toLocalDate() == LocalDate.now()) "HH:mm" else "MM-dd HH:mm"
    return time.format(DateTimeFormatter.ofPattern(pattern))
}
