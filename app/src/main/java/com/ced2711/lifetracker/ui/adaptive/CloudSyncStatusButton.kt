package com.ced2711.lifetracker.ui.adaptive

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ced2711.lifetracker.data.cloud.CloudSyncIndicator
import com.ced2711.lifetracker.data.cloud.CloudSyncIndicatorState
import com.ced2711.lifetracker.ui.localization.localizedText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Cloud sync status for the top bar; tapping it syncs both ways, or opens sync settings when it needs attention. */
data class TopBarSyncStatus(
    val indicator: CloudSyncIndicator,
    val onSync: () -> Unit,
    val onOpenSettings: () -> Unit,
)

internal val LocalTopBarSyncStatus = compositionLocalOf<TopBarSyncStatus?> { null }

@Composable
internal fun CloudSyncStatusButton(status: TopBarSyncStatus) {
    val state = status.indicator.state
    val summary = localizedText(
        when (state) {
            CloudSyncIndicatorState.SYNCING -> "Syncing…"
            CloudSyncIndicatorState.NEEDS_ATTENTION -> "Sync needs attention"
            CloudSyncIndicatorState.PENDING -> "Changes not synced yet"
            CloudSyncIndicatorState.UP_TO_DATE -> "Up to date"
        },
    )
    val lastSync = status.indicator.lastSyncAt
        ?.let { "${localizedText("Last synced")} ${formatSyncTime(it)}" }
        ?: localizedText("Never synced")
    val hint = localizedText(
        if (state == CloudSyncIndicatorState.NEEDS_ATTENTION) "Tap to open sync settings" else "Tap to sync both ways now",
    )
    IconButton(
        onClick = if (state == CloudSyncIndicatorState.NEEDS_ATTENTION) status.onOpenSettings else status.onSync,
        enabled = state != CloudSyncIndicatorState.SYNCING,
        modifier = Modifier.semantics { contentDescription = "$summary. $lastSync. $hint" },
    ) {
        when (state) {
            CloudSyncIndicatorState.SYNCING -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            CloudSyncIndicatorState.NEEDS_ATTENTION -> Icon(
                Icons.Outlined.SyncProblem,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            CloudSyncIndicatorState.PENDING -> Icon(
                Icons.Outlined.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CloudSyncIndicatorState.UP_TO_DATE -> Icon(
                Icons.Outlined.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatSyncTime(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val pattern = if (time.toLocalDate() == LocalDate.now()) "HH:mm" else "MM-dd HH:mm"
    return time.format(DateTimeFormatter.ofPattern(pattern))
}
