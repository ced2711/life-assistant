package com.ced2711.lifetracker.cloudsync

import kotlinx.serialization.Serializable

const val CLOUD_SYNC_PROTOCOL = "life-tracker-sync-v1"
const val CLOUD_BACKUP_FILE_PREFIX = "life-tracker-sync-"
const val CLOUD_BACKUP_MIME_TYPE = "application/vnd.ced2711.life-tracker-backup"

@Serializable
data class CloudRevision(
    val fileId: String,
    val fileName: String,
    val createdAt: Long,
    val deviceId: String,
    val baseRevisionId: String?,
    val contentFingerprint: String,
    val driveVersion: Long,
    val modifiedTime: String,
    val sizeBytes: Long,
    val mergedRevisionIds: List<String> = emptyList(),
)

data class NewCloudRevision(
    val createdAt: Long,
    val deviceId: String,
    val baseRevisionId: String?,
    val contentFingerprint: String,
    val mergedRevisionIds: List<String> = emptyList(),
) {
    init {
        require(createdAt >= 0)
        require(deviceId.isNotBlank())
        require(contentFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(mergedRevisionIds.size <= 80) { "Too many competing revisions to resolve in one sync." }
        require(mergedRevisionIds.all { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) })
    }

    val fileName: String
        get() = "$CLOUD_BACKUP_FILE_PREFIX$createdAt-${deviceId.take(12)}.tlb"
}

@Serializable
data class LocalCloudSyncState(
    val deviceId: String,
    val lastRevisionId: String? = null,
    val lastContentFingerprint: String? = null,
    val lastSyncAt: Long? = null,
)

sealed interface SyncDecision {
    data object UpToDate : SyncDecision
    data object Upload : SyncDecision
    data object Download : SyncDecision
    data object Conflict : SyncDecision
}

fun decideSyncAction(
    localFingerprint: String,
    localIsEmpty: Boolean,
    state: LocalCloudSyncState,
    remote: CloudRevision?,
): SyncDecision {
    val lastRevision = state.lastRevisionId
    val lastFingerprint = state.lastContentFingerprint
    val localChanged = lastFingerprint == null || localFingerprint != lastFingerprint

    if (remote == null) return if (localChanged || !localIsEmpty) SyncDecision.Upload else SyncDecision.UpToDate

    if (lastRevision == null) {
        return if (localIsEmpty) SyncDecision.Download else SyncDecision.Conflict
    }

    val remoteChanged = remote.fileId != lastRevision
    return when {
        !localChanged && !remoteChanged -> SyncDecision.UpToDate
        localChanged && !remoteChanged -> SyncDecision.Upload
        !localChanged && remoteChanged -> SyncDecision.Download
        else -> SyncDecision.Conflict
    }
}

/** Immutable files form a revision graph. Every competing tip must be explicitly resolved. */
fun cloudRevisionHeads(revisions: List<CloudRevision>): List<CloudRevision> {
    val parents = revisions.flatMap { listOfNotNull(it.baseRevisionId) + it.mergedRevisionIds }.toSet()
    return revisions.distinctBy { it.fileId }.filterNot { it.fileId in parents }
}

/** A stable snapshot used to detect a cloud change immediately before or after an upload. */
fun cloudRevisionHeadIds(revisions: List<CloudRevision>): Set<String> {
    val heads = cloudRevisionHeads(revisions)
    if (revisions.isNotEmpty() && heads.isEmpty()) {
        throw CloudTransportException("Cloud revision history is invalid.")
    }
    return heads.mapTo(linkedSetOf(), CloudRevision::fileId)
}

fun latestCloudRevision(revisions: List<CloudRevision>): CloudRevision? =
    cloudRevisionHeads(revisions).firstOrNull()

/** Keep recent history and never prune when another device has created a competing tip. */
fun prunableCloudRevisions(revisions: List<CloudRevision>, keepCount: Int = 30): List<CloudRevision> {
    require(keepCount >= 2)
    val heads = cloudRevisionHeads(revisions)
    if (heads.size != 1) return emptyList()
    val byId = revisions.associateBy { it.fileId }
    val ancestors = mutableSetOf<String>()
    val pending = ArrayDeque<String>()
    pending.add(heads.single().fileId)
    while (pending.isNotEmpty()) {
        val id = pending.removeFirst()
        if (!ancestors.add(id)) continue
        byId[id]?.let { row ->
            row.baseRevisionId?.let(pending::addLast)
            row.mergedRevisionIds.forEach(pending::addLast)
        }
    }
    val kept = revisions.take(keepCount).mapTo(mutableSetOf()) { it.fileId }
    kept += heads.single().fileId
    return revisions.filter { it.fileId in ancestors && it.fileId !in kept }
}

fun decideSyncAction(
    localFingerprint: String,
    localIsEmpty: Boolean,
    state: LocalCloudSyncState,
    revisions: List<CloudRevision>,
): SyncDecision {
    val heads = cloudRevisionHeads(revisions)
    cloudRevisionHeadIds(revisions)
    if (heads.size > 1) return SyncDecision.Conflict
    val remote = heads.firstOrNull()
    // A disappeared cloud history must not silently recreate a different dataset.
    if (remote == null && state.lastRevisionId != null) {
        throw CloudTransportException("The linked cloud backup is missing. Reconnect to initialize a new backup.")
    }
    val decision = decideSyncAction(localFingerprint, localIsEmpty, state, remote)
    if (remote != null && state.lastRevisionId != null && remote.fileId != state.lastRevisionId) {
        val byId = revisions.associateBy { it.fileId }
        val pending = ArrayDeque<String>()
        pending.add(remote.fileId)
        val visited = mutableSetOf<String>()
        var descendant = false
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (id == state.lastRevisionId) { descendant = true; break }
            if (!visited.add(id)) continue
            byId[id]?.let { row ->
                row.baseRevisionId?.let(pending::addLast)
                row.mergedRevisionIds.forEach(pending::addLast)
            }
        }
        if (!descendant) return SyncDecision.Conflict
    }
    return decision
}

enum class ConflictResolution {
    KEEP_LOCAL,
    USE_CLOUD,
}

open class CloudAuthorizationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class CloudTransportException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
