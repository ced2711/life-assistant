package com.ced2711.lifetracker.cloudsync

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncDecisionTest {
    private val fingerprintA = "a".repeat(64)
    private val fingerprintB = "b".repeat(64)

    @Test
    fun `empty new device downloads an existing cloud revision`() {
        assertEquals(
            SyncDecision.Download,
            decideSyncAction(fingerprintA, true, LocalCloudSyncState("device"), revision("remote")),
        )
    }

    @Test
    fun `non-empty new device reports conflict instead of overwriting`() {
        assertEquals(
            SyncDecision.Conflict,
            decideSyncAction(fingerprintA, false, LocalCloudSyncState("device"), revision("remote")),
        )
    }

    @Test
    fun `local-only change uploads`() {
        val state = LocalCloudSyncState("device", "base", fingerprintA)
        assertEquals(SyncDecision.Upload, decideSyncAction(fingerprintB, false, state, revision("base")))
    }

    @Test
    fun `remote-only change downloads`() {
        val state = LocalCloudSyncState("device", "base", fingerprintA)
        assertEquals(SyncDecision.Download, decideSyncAction(fingerprintA, false, state, revision("remote")))
    }

    @Test
    fun `two-sided change reports conflict`() {
        val state = LocalCloudSyncState("device", "base", fingerprintA)
        assertEquals(SyncDecision.Conflict, decideSyncAction(fingerprintB, false, state, revision("remote")))
    }

    @Test
    fun `matching local and remote state is up to date`() {
        val state = LocalCloudSyncState("device", "base", fingerprintA)
        assertEquals(SyncDecision.UpToDate, decideSyncAction(fingerprintA, false, state, revision("base")))
    }

    private fun revision(id: String) = CloudRevision(
        fileId = id,
        fileName = "$id.tlb",
        createdAt = 1,
        deviceId = "other",
        baseRevisionId = null,
        contentFingerprint = fingerprintA,
        driveVersion = 1,
        modifiedTime = "",
        sizeBytes = 1,
    )

    @Test
    fun `unchanged device refuses to discard a competing cloud branch`() {
        val base = revision("base")
        val a = revision("a").copy(baseRevisionId = "base")
        val b = revision("b").copy(baseRevisionId = "base")
        assertEquals(SyncDecision.Conflict, decideSyncAction(fingerprintA, false,
            LocalCloudSyncState("device", "a", fingerprintA), listOf(b, a, base)))
    }

    @Test
    fun `multi-generation descendant can download automatically`() {
        val rows = listOf(revision("new").copy(baseRevisionId = "middle"),
            revision("middle").copy(baseRevisionId = "base"), revision("base"))
        assertEquals(SyncDecision.Download, decideSyncAction(fingerprintA, false,
            LocalCloudSyncState("device", "base", fingerprintA), rows))
    }

    @Test
    fun `explicit resolution joins both branches`() {
        val rows = listOf(revision("resolved").copy(baseRevisionId = "b", mergedRevisionIds = listOf("a", "b")),
            revision("a").copy(baseRevisionId = "base"), revision("b").copy(baseRevisionId = "base"), revision("base"))
        assertEquals(listOf("resolved"), cloudRevisionHeads(rows).map { it.fileId })
        assertEquals(SyncDecision.Download, decideSyncAction(fingerprintA, false,
            LocalCloudSyncState("device", "a", fingerprintA), rows))
    }

    @Test
    fun `unknown ancestry is a conflict even when local data did not change`() {
        assertEquals(SyncDecision.Conflict, decideSyncAction(fingerprintA, false,
            LocalCloudSyncState("device", "missing", fingerprintA), listOf(revision("remote"))))
    }

    @Test(expected = CloudTransportException::class)
    fun `missing cloud history does not silently upload into an unrecognized account`() {
        decideSyncAction(fingerprintA, false, LocalCloudSyncState("device", "base", fingerprintA), emptyList())
    }

    @Test fun `retention keeps recent ancestors and refuses to prune a fork`() {
        val base = revision("base")
        val middle = revision("middle").copy(baseRevisionId = "base")
        val newest = revision("new").copy(baseRevisionId = "middle")
        assertEquals(listOf(base), prunableCloudRevisions(listOf(newest, middle, base), 2))
        assertEquals(emptyList<CloudRevision>(), prunableCloudRevisions(
            listOf(newest, revision("fork").copy(baseRevisionId = "base"), middle, base), 2))
    }

    @Test fun `head snapshot identifies every competing immutable branch`() {
        val base = revision("base")
        val first = revision("first").copy(baseRevisionId = "base")
        val second = revision("second").copy(baseRevisionId = "base")

        assertEquals(setOf("first", "second"), cloudRevisionHeadIds(listOf(first, second, base)))
        assertEquals(emptySet<String>(), cloudRevisionHeadIds(emptyList()))
    }
}
