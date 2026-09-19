package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.cloudsync.CloudBackupStore
import com.ced2711.lifetracker.cloudsync.CloudRevision
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.cloudsync.NewCloudRevision
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCloudSyncControllerTest {
    @Test
    fun firstUploadStopsWhenCloudBecomesNonEmptyDuringSnapshotCreation() = runBlocking {
        withController { controller, dataStore, configStore, cloud ->
            assertTrue(dataStore.addCategory("Local"))
            cloud.onList = { call, revisions ->
                if (call == 2) revisions.add(0, revision("raced-remote", "b".repeat(64)))
            }

            controller.synchronize()

            assertEquals(0, cloud.uploadCount)
            assertNotNull(controller.state.value.conflict)
            assertNull(configStore.read().syncState.lastRevisionId)
            assertEquals(0, cloud.deleteCount)
        }
    }

    @Test
    fun simultaneousUploadKeepsBothBranchesAndDoesNotRecordFalseBaseline() = runBlocking {
        withController { controller, dataStore, configStore, cloud ->
            assertTrue(dataStore.addCategory("Local"))
            cloud.onList = { call, revisions ->
                if (call == 3) revisions.add(0, revision("simultaneous-device", "c".repeat(64)))
            }

            controller.synchronize()

            assertEquals(1, cloud.uploadCount)
            assertNotNull(controller.state.value.conflict)
            assertNull(configStore.read().syncState.lastRevisionId)
            assertEquals(0, cloud.deleteCount)
        }
    }

    @Test
    fun conflictResolutionStopsWhenHeadsChangeBeforeUpload() = runBlocking {
        withController { controller, dataStore, configStore, cloud ->
            assertTrue(dataStore.addCategory("Local"))
            val validEncrypted = dataStore.createUploadSnapshot()
            cloud.downloadBytes = validEncrypted.file.readBytes()
            validEncrypted.file.delete()
            cloud.revisions.add(revision("remote", "a".repeat(64)))

            controller.synchronize()
            assertNotNull(controller.state.value.conflict)
            cloud.onList = { call, revisions ->
                if (call == 3) revisions.add(0, revision("new-conflict-head", "b".repeat(64)))
            }

            controller.synchronize(ConflictResolution.KEEP_LOCAL)

            assertEquals(0, cloud.uploadCount)
            assertEquals(1, cloud.downloadCount)
            assertNotNull(controller.state.value.conflict)
            assertNull(configStore.read().syncState.lastRevisionId)
        }
    }

    @Test
    fun normalLineageUploadDoesNotDownloadOrDeletePreviousSnapshot() = runBlocking {
        withController { controller, dataStore, configStore, cloud ->
            assertTrue(dataStore.addCategory("Previously synced"))
            val previousFingerprint = dataStore.localFingerprint()
            configStore.recordSync("base", previousFingerprint, 1)
            cloud.revisions.add(revision("base", previousFingerprint))
            assertTrue(dataStore.addCategory("New local change"))

            controller.synchronize()

            assertEquals(1, cloud.uploadCount)
            assertEquals(0, cloud.downloadCount)
            assertEquals(0, cloud.deleteCount)
            assertEquals("uploaded-1", configStore.read().syncState.lastRevisionId)
            assertNull(controller.state.value.conflict)
        }
    }

    @Test
    fun useCloudStopsBeforeUploadWhenCloudChangesDuringDownload() = runBlocking {
        withController { controller, dataStore, configStore, cloud ->
            val originalLocalFingerprint = prepareUseCloudConflict(dataStore, cloud)
            cloud.onDownload = {
                cloud.revisions.add(revision("download-race", "c".repeat(64)))
            }

            controller.synchronize()
            assertNotNull(controller.state.value.conflict)
            controller.synchronize(ConflictResolution.USE_CLOUD)

            assertEquals(2, cloud.downloadCount)
            assertEquals(0, cloud.uploadCount)
            assertEquals(originalLocalFingerprint, dataStore.localFingerprint())
            assertNull(configStore.read().syncState.lastRevisionId)
        }
    }

    @Test
    fun useCloudKeepsLocalAndBaselineWhenCloudChangesDuringUpload() = runBlocking {
        withController { controller, dataStore, configStore, cloud ->
            val originalLocalFingerprint = prepareUseCloudConflict(dataStore, cloud)
            cloud.onUpload = {
                cloud.revisions.add(revision("upload-race", "d".repeat(64)))
            }

            controller.synchronize()
            controller.synchronize(ConflictResolution.USE_CLOUD)

            assertEquals(2, cloud.downloadCount)
            assertEquals(1, cloud.uploadCount)
            assertEquals(originalLocalFingerprint, dataStore.localFingerprint())
            assertNull(configStore.read().syncState.lastRevisionId)
            assertNotNull(controller.state.value.conflict)
        }
    }

    @Test
    fun useCloudReplacesLocalOnlyAfterPostflightAndRecordsNewBaseline() = runBlocking {
        withController { controller, dataStore, configStore, cloud ->
            prepareUseCloudConflict(dataStore, cloud)
            val originalLocalBytes = dataStore.encryptedFile.readBytes()

            controller.synchronize()
            controller.synchronize(ConflictResolution.USE_CLOUD)

            assertEquals(2, cloud.downloadCount)
            assertEquals(1, cloud.uploadCount)
            assertTrue(dataStore.isUserDataEmpty())
            assertEquals("uploaded-1", configStore.read().syncState.lastRevisionId)
            assertEquals(dataStore.localFingerprint(), configStore.read().syncState.lastContentFingerprint)
            assertEquals("a".repeat(64), cloud.revisions.first().contentFingerprint)
            assertNull(controller.state.value.conflict)
            val recoveryFiles = dataStore.cloudRecoveryDirectory.listFiles().orEmpty()
            assertEquals(1, recoveryFiles.size)
            assertArrayEquals(originalLocalBytes, recoveryFiles.single().readBytes())
            controller.synchronize()
            assertEquals(1, cloud.uploadCount)
            assertEquals(2, cloud.downloadCount)
            assertNull(controller.state.value.conflict)
        }
    }

    @Test
    fun useCloudDoesNotReplaceLocalWhenItChangesBeforeConditionalApply() = runBlocking {
        withController { controller, dataStore, configStore, cloud ->
            val originalLocalFingerprint = prepareUseCloudConflict(dataStore, cloud)
            cloud.onUpload = {
                assertTrue(dataStore.addCategory("Changed during sync"))
            }

            controller.synchronize()
            controller.synchronize(ConflictResolution.USE_CLOUD)

            assertEquals(1, cloud.uploadCount)
            assertTrue(dataStore.localFingerprint() != originalLocalFingerprint)
            assertTrue(!dataStore.isUserDataEmpty())
            assertNull(configStore.read().syncState.lastRevisionId)
            assertNotNull(controller.state.value.conflict)
        }
    }

    @Test
    fun cancelledSynchronizeClearsBusyState() = runBlocking {
        withController { controller, _, _, cloud ->
            cloud.onListSuspending = { _, _ -> throw CancellationException("test cancellation") }

            try {
                controller.synchronize()
                throw AssertionError("Expected cancellation")
            } catch (_: CancellationException) {
                // expected
            }
            assertFalse(controller.state.value.syncing)
        }
    }

    private suspend fun withController(
        block: suspend (
            DesktopCloudSyncController,
            DesktopDataStore,
            DesktopConfigStore,
            FakeCloudStore,
        ) -> Unit,
    ) {
        val root = Files.createTempDirectory("life-tracker-desktop-cloud-test").toFile()
        val dataStore = DesktopDataStore(root.resolve("app"))
        try {
            assertTrue(dataStore.open(testPassword()))
            val configStore = DesktopConfigStore(root.resolve("desktop.properties"))
            val credentials = WindowsCredentialStore(root.resolve("credentials"))
            val oauth = DesktopGoogleOAuth(configStore, credentials)
            val cloud = FakeCloudStore()
            val controller = DesktopCloudSyncController(
                dataStore = dataStore,
                configStore = configStore,
                oauth = oauth,
                now = { 123L },
                cloudStore = cloud,
                connectionStatus = { true },
            )
            block(controller, dataStore, configStore, cloud)
        } finally {
            dataStore.close()
            root.deleteRecursively()
        }
    }

    private fun revision(id: String, fingerprint: String) = CloudRevision(
        fileId = id,
        fileName = "$id.tlb",
        createdAt = 1,
        deviceId = "other-device",
        baseRevisionId = null,
        contentFingerprint = fingerprint,
        driveVersion = 1,
        modifiedTime = "2026-09-19T00:00:00Z",
        sizeBytes = 1,
    )

    private suspend fun prepareUseCloudConflict(
        dataStore: DesktopDataStore,
        cloud: FakeCloudStore,
    ): String {
        val remote = dataStore.createUploadSnapshot()
        cloud.downloadBytes = remote.file.readBytes()
        // Cloud contentFingerprint is logical dataset metadata, not the SHA-256 of encrypted bytes.
        val remoteFingerprint = "a".repeat(64)
        remote.file.delete()
        assertTrue(dataStore.addCategory("Local change"))
        cloud.revisions.add(revision("remote", remoteFingerprint))
        return dataStore.localFingerprint()
    }

    private class FakeCloudStore : CloudBackupStore {
        val revisions = mutableListOf<CloudRevision>()
        var onList: (Int, MutableList<CloudRevision>) -> Unit = { _, _ -> }
        var onListSuspending: suspend (Int, MutableList<CloudRevision>) -> Unit = { _, _ -> }
        var downloadBytes: ByteArray? = null
        var onDownload: (CloudRevision) -> Unit = {}
        var onUpload: suspend () -> Unit = {}
        var uploadCount = 0
            private set
        var downloadCount = 0
            private set
        var deleteCount = 0
            private set
        private var listCount = 0

        override suspend fun listRevisions(limit: Int): List<CloudRevision> {
            listCount += 1
            onList(listCount, revisions)
            onListSuspending(listCount, revisions)
            return revisions.toList()
        }

        override suspend fun uploadRevision(source: File, revision: NewCloudRevision): CloudRevision {
            assertTrue(source.isFile && source.length() > 0)
            uploadCount += 1
            val uploaded = CloudRevision(
                fileId = "uploaded-$uploadCount",
                fileName = revision.fileName,
                createdAt = revision.createdAt,
                deviceId = revision.deviceId,
                baseRevisionId = revision.baseRevisionId,
                contentFingerprint = revision.contentFingerprint,
                driveVersion = uploadCount.toLong(),
                modifiedTime = "2026-09-19T00:00:00Z",
                sizeBytes = source.length(),
                mergedRevisionIds = revision.mergedRevisionIds,
            )
            revisions.add(0, uploaded)
            onUpload()
            return uploaded
        }

        override suspend fun downloadRevision(revision: CloudRevision, destination: File) {
            downloadCount += 1
            onDownload(revision)
            destination.writeBytes(downloadBytes ?: error("This test must not download a cloud revision."))
        }

        override suspend fun deleteRevision(fileId: String) {
            deleteCount += 1
        }
    }

    private fun testPassword(): CharArray = charArrayOf(
        'd', 'e', 's', 'k', 't', 'o', 'p', '-', 'c', 'l', 'o', 'u', 'd', '-', '2', '6', '!',
    )
}
