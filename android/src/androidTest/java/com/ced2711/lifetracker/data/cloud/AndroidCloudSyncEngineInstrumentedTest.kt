package com.ced2711.lifetracker.data.cloud

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.cloudsync.CloudBackupStore
import com.ced2711.lifetracker.cloudsync.CloudRevision
import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.cloudsync.NewCloudRevision
import com.ced2711.lifetracker.data.backup.BackupRepository
import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.settings.SettingsRepository
import com.ced2711.lifetracker.data.vault.VaultKeyManager
import com.ced2711.lifetracker.data.vault.VaultRepository
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidCloudSyncEngineInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: TaskLedgerDatabase
    private lateinit var preferences: CloudSyncPreferences
    private lateinit var secretStore: CloudSyncSecretStore
    private lateinit var backupRepository: BackupRepository
    private lateinit var attachmentDirectory: File
    private lateinit var workDirectory: File
    private lateinit var recoveryDirectory: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        recoveryDirectory = File(context.filesDir, "cloud-recovery").apply { deleteRecursively() }
        context.getSharedPreferences("life_tracker_cloud_sync", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("life_tracker_cloud_sync_secret", Context.MODE_PRIVATE)
            .edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, TaskLedgerDatabase::class.java).build()
        preferences = CloudSyncPreferences(context).also {
            it.setEnabled(true)
            it.setAutomaticSync(false)
        }
        secretStore = CloudSyncSecretStore(context).also {
            it.clear()
            it.save(TEST_PASSWORD.toCharArray())
        }
        attachmentDirectory = File(context.cacheDir, "cloud-sync-test-attachments").apply { mkdirs() }
        workDirectory = File(context.cacheDir, "cloud-sync-test-work").apply { mkdirs() }
        backupRepository = BackupRepository(
            backupDao = database.backupDao(),
            settingsRepository = SettingsRepository(context),
            vaultRepository = VaultRepository(database, VaultKeyManager(context)),
            attachmentDirectory = attachmentDirectory,
            workDirectory = workDirectory,
        )
    }

    @After
    fun tearDown() {
        secretStore.clear()
        context.getSharedPreferences("life_tracker_cloud_sync", Context.MODE_PRIVATE)
            .edit().clear().commit()
        database.close()
        attachmentDirectory.deleteRecursively()
        workDirectory.deleteRecursively()
        recoveryDirectory.deleteRecursively()
    }

    @Test
    fun firstConnectionWithLocalAndCloudDataReturnsConflictWithoutReplacingEither() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "Local")))
        val store = FakeCloudStore(
            mutableListOf(remoteRevision(contentFingerprint = "a".repeat(64))),
        )
        val engine = engine(store)

        val result = engine.synchronize()

        assertTrue(result is AndroidCloudSyncResult.Conflict)
        assertEquals(listOf(42L), database.backupDao().backupCategories().map { it.id })
        assertEquals(0, store.uploadCount)
        assertEquals(0, store.downloadCount)
    }

    @Test
    fun overlappingManualAndWorkerRequestsAreSerialized() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "Local")))
        val store = FakeCloudStore(mutableListOf(), listDelayMillis = 200)
        val engine = engine(store)

        val first = async { engine.synchronize() }
        val second = async { engine.synchronize() }
        val results = listOf(first.await(), second.await())

        assertEquals(1, store.maximumConcurrentLists.get())
        assertEquals(1, results.count { it is AndroidCloudSyncResult.Uploaded })
        assertEquals(1, results.count { it is AndroidCloudSyncResult.UpToDate })
    }

    @Test
    fun keepLocalCannotCreateARevisionWithTheWrongPassword() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "Local")))
        val encryptedRemote = ByteArrayOutputStream().use { output ->
            backupRepository.export(output, CORRECT_REMOTE_PASSWORD.toCharArray())
            output.toByteArray()
        }
        secretStore.save(WRONG_REMOTE_PASSWORD.toCharArray())
        val store = FakeCloudStore(
            mutableListOf(remoteRevision(contentFingerprint = "a".repeat(64))),
            downloadBytes = encryptedRemote,
        )
        val engine = engine(store)

        val result = engine.synchronize(
            conflictResolution = ConflictResolution.KEEP_LOCAL,
            expectedRemoteRevisionId = "remote-revision",
        )

        assertTrue(result is AndroidCloudSyncResult.Failed)
        assertEquals(0, store.uploadCount)
        assertEquals(1, store.downloadCount)
        assertEquals(listOf(42L), database.backupDao().backupCategories().map { it.id })
    }

    @Test
    fun staleConflictApprovalDoesNotActOnANewerRemoteRevision() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "Local")))
        val store = FakeCloudStore(
            mutableListOf(remoteRevision(contentFingerprint = "a".repeat(64))),
        )
        val engine = engine(store)

        val result = engine.synchronize(
            conflictResolution = ConflictResolution.KEEP_LOCAL,
            expectedRemoteRevisionId = "revision-shown-in-old-dialog",
        )

        assertTrue(result is AndroidCloudSyncResult.Conflict)
        assertEquals(0, store.uploadCount)
        assertEquals(0, store.downloadCount)
    }

    @Test
    fun establishedLineageUploadDoesNotRedownloadThePreviousSnapshot() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(41, "Previously synced")))
        val previousFingerprint = backupRepository.syncFingerprint()
        preferences.recordSuccessfulSync("remote-revision", previousFingerprint, 1)
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "New local change")))
        val store = FakeCloudStore(
            mutableListOf(remoteRevision(contentFingerprint = previousFingerprint)),
        )

        val result = engine(store).synchronize()

        assertTrue(result is AndroidCloudSyncResult.Uploaded)
        assertEquals(1, store.uploadCount)
        assertEquals(0, store.downloadCount)
    }

    @Test
    fun firstUploadStopsWhenCloudBecomesNonEmptyDuringSnapshotCreation() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "Local")))
        val store = FakeCloudStore(mutableListOf()) { call, revisions ->
            if (call == 2) {
                revisions.add(0, remoteRevision("b".repeat(64)).copy(fileId = "raced-remote"))
            }
        }

        val result = engine(store).synchronize()

        assertTrue(result is AndroidCloudSyncResult.Conflict)
        assertEquals(0, store.uploadCount)
        assertEquals(null, preferences.read().state.lastRevisionId)
        assertEquals(0, store.deleteCount)
    }

    @Test
    fun conflictResolutionStopsWhenHeadsChangeBeforeUpload() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "Local")))
        val encryptedRemote = ByteArrayOutputStream().use { output ->
            backupRepository.export(output, TEST_PASSWORD.toCharArray())
            output.toByteArray()
        }
        val store = FakeCloudStore(
            revisions = mutableListOf(remoteRevision("a".repeat(64))),
            downloadBytes = encryptedRemote,
            onList = { call, revisions ->
                if (call == 2) {
                    revisions.add(0, remoteRevision("b".repeat(64)).copy(fileId = "new-conflict-head"))
                }
            },
        )

        val result = engine(store).synchronize(
            conflictResolution = ConflictResolution.KEEP_LOCAL,
            expectedRemoteRevisionId = "remote-revision",
        )

        assertTrue(result is AndroidCloudSyncResult.Conflict)
        assertEquals(0, store.uploadCount)
        assertEquals(null, preferences.read().state.lastRevisionId)
    }

    @Test
    fun simultaneousUploadKeepsBothBranchesAndDoesNotRecordFalseBaseline() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "Local")))
        val store = FakeCloudStore(
            revisions = mutableListOf(),
            onList = { call, revisions ->
                if (call == 3) {
                    revisions.add(0, remoteRevision("c".repeat(64)).copy(fileId = "simultaneous-device"))
                }
            },
        )

        val result = engine(store).synchronize()

        assertTrue(result is AndroidCloudSyncResult.Conflict)
        assertEquals(1, store.uploadCount)
        assertEquals(null, preferences.read().state.lastRevisionId)
        assertEquals(0, store.deleteCount)
    }

    @Test
    fun useCloudHeadAppearingDuringDownloadLeavesLocalDataAndBaselineUntouched() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "Local")))
        val encryptedRemote = ByteArrayOutputStream().use { output ->
            backupRepository.export(output, TEST_PASSWORD.toCharArray())
            output.toByteArray()
        }
        val store = FakeCloudStore(
            revisions = mutableListOf(remoteRevision("a".repeat(64))),
            downloadBytes = encryptedRemote,
            onDownload = { revisions ->
                revisions.add(0, remoteRevision("b".repeat(64)).copy(fileId = "download-race"))
            },
        )
        val engine = engine(store)

        val result = engine.synchronize(
            conflictResolution = ConflictResolution.USE_CLOUD,
            expectedRemoteRevisionId = "remote-revision",
        )

        assertTrue(result is AndroidCloudSyncResult.Conflict)
        assertEquals(listOf(42L), database.backupDao().backupCategories().map { it.id })
        assertEquals(0, store.uploadCount)
        assertEquals(null, preferences.read().state.lastRevisionId)
    }

    @Test
    fun useCloudHeadAppearingDuringUploadLeavesLocalDataAndBaselineUntouched() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "Local")))
        val encryptedRemote = ByteArrayOutputStream().use { output ->
            backupRepository.export(output, TEST_PASSWORD.toCharArray())
            output.toByteArray()
        }
        val store = FakeCloudStore(
            revisions = mutableListOf(remoteRevision("a".repeat(64))),
            downloadBytes = encryptedRemote,
            onUpload = { revisions ->
                revisions.add(0, remoteRevision("b".repeat(64)).copy(fileId = "upload-race"))
            },
        )
        val engine = engine(store)

        val result = engine.synchronize(
            conflictResolution = ConflictResolution.USE_CLOUD,
            expectedRemoteRevisionId = "remote-revision",
        )

        assertTrue(result is AndroidCloudSyncResult.Conflict)
        assertEquals(listOf(42L), database.backupDao().backupCategories().map { it.id })
        assertEquals(1, store.uploadCount)
        assertEquals(null, preferences.read().state.lastRevisionId)
    }

    @Test
    fun useCloudAcceptsDesktopEncryptedFingerprintAndRecordsAndroidBaselineAfterRestore() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "Local")))
        val androidFingerprint = backupRepository.syncFingerprint()
        val desktopEncryptedFingerprint = "d".repeat(64)
        val encryptedRemote = ByteArrayOutputStream().use { output ->
            backupRepository.export(output, TEST_PASSWORD.toCharArray())
            output.toByteArray()
        }
        val store = FakeCloudStore(
            revisions = mutableListOf(remoteRevision(desktopEncryptedFingerprint)),
            downloadBytes = encryptedRemote,
        )
        val engine = engine(store)

        val result = engine.synchronize(
            conflictResolution = ConflictResolution.USE_CLOUD,
            expectedRemoteRevisionId = "remote-revision",
        )

        assertTrue(result is AndroidCloudSyncResult.Downloaded)
        assertEquals(1, store.uploadCount)
        assertEquals(1, store.downloadCount)
        assertArrayEquals(encryptedRemote, requireNotNull(store.uploadedBytes))
        assertEquals("uploaded-1", preferences.read().state.lastRevisionId)
        assertEquals(androidFingerprint, preferences.read().state.lastContentFingerprint)
        assertEquals(desktopEncryptedFingerprint, store.uploadedRevision?.contentFingerprint)
        assertEquals(listOf("remote-revision"), store.uploadedRevision?.mergedRevisionIds)

        val recovery = requireNotNull(engine.recoveryFiles().singleOrNull())
        assertTrue(recovery.isFile)
        assertTrue(recovery.name.endsWith(".tlb"))
        val preparedRecovery = recovery.inputStream().buffered().use { input ->
            backupRepository.prepareRestore(input, TEST_PASSWORD.toCharArray())
        }
        backupRepository.restore(preparedRecovery)
        assertEquals(listOf(42L), database.backupDao().backupCategories().map { it.id })
    }

    @Test
    fun useCloudLocalEditDuringUploadLeavesEditedDataAndBaselineUntouched() = runBlocking {
        database.backupDao().insertCategories(listOf(CategoryEntity(42, "Local")))
        val encryptedRemote = ByteArrayOutputStream().use { output ->
            backupRepository.export(output, TEST_PASSWORD.toCharArray())
            output.toByteArray()
        }
        val store = FakeCloudStore(
            revisions = mutableListOf(remoteRevision("d".repeat(64))),
            downloadBytes = encryptedRemote,
            onUpload = {
                database.backupDao().insertCategories(listOf(CategoryEntity(43, "Edited during upload")))
            },
        )
        val engine = engine(store)

        val result = engine.synchronize(
            conflictResolution = ConflictResolution.USE_CLOUD,
            expectedRemoteRevisionId = "remote-revision",
        )

        assertTrue(result is AndroidCloudSyncResult.Conflict)
        assertEquals(listOf(42L, 43L), database.backupDao().backupCategories().map { it.id })
        assertEquals(1, store.uploadCount)
        assertEquals(null, preferences.read().state.lastRevisionId)
    }

    @Test
    fun freshConnectionResetPreservesOptionsAndDeviceButClearsOldLineage() {
        val initial = preferences.read()
        preferences.setAutomaticSync(false)
        preferences.recordSuccessfulSync("old-revision", "a".repeat(64), 123)
        preferences.setAttention(CloudSyncAttention.CONFLICT)

        preferences.resetSyncLineage()

        val reset = preferences.read()
        assertTrue(reset.enabled)
        assertEquals(false, reset.automaticSync)
        assertEquals(initial.state.deviceId, reset.state.deviceId)
        assertEquals(null, reset.state.lastRevisionId)
        assertEquals(null, reset.state.lastContentFingerprint)
        assertEquals(null, reset.state.lastSyncAt)
        assertEquals(null, reset.attention)
    }

    private fun engine(store: CloudBackupStore) = AndroidCloudSyncEngine(
        context = context,
        backupRepository = backupRepository,
        preferences = preferences,
        secretStore = secretStore,
        authorization = GoogleDriveAuthorization(context),
        afterRestore = {},
        cloudStore = store,
    )

    private fun remoteRevision(contentFingerprint: String) = CloudRevision(
        fileId = "remote-revision",
        fileName = "life-tracker-sync-1-remote.tlb",
        createdAt = 1,
        deviceId = "remote-device",
        baseRevisionId = null,
        contentFingerprint = contentFingerprint,
        driveVersion = 1,
        modifiedTime = "2026-09-18T00:00:00Z",
        sizeBytes = 1,
    )

    private class FakeCloudStore(
        private val revisions: MutableList<CloudRevision>,
        private val listDelayMillis: Long = 0,
        private val downloadBytes: ByteArray? = null,
        private val onDownload: (MutableList<CloudRevision>) -> Unit = {},
        private val onUpload: suspend (MutableList<CloudRevision>) -> Unit = {},
        private val onList: (Int, MutableList<CloudRevision>) -> Unit = { _, _ -> },
    ) : CloudBackupStore {
        val maximumConcurrentLists = AtomicInteger(0)
        private val activeLists = AtomicInteger(0)
        var uploadCount = 0
            private set
        var downloadCount = 0
            private set
        var deleteCount = 0
            private set
        var uploadedBytes: ByteArray? = null
            private set
        var uploadedRevision: CloudRevision? = null
            private set
        private var listCount = 0

        override suspend fun listRevisions(limit: Int): List<CloudRevision> {
            val active = activeLists.incrementAndGet()
            maximumConcurrentLists.updateAndGet { maxOf(it, active) }
            return try {
                if (listDelayMillis > 0) delay(listDelayMillis)
                listCount += 1
                onList(listCount, revisions)
                revisions.toList()
            } finally {
                activeLists.decrementAndGet()
            }
        }

        override suspend fun uploadRevision(
            source: File,
            revision: NewCloudRevision,
        ): CloudRevision {
            assertTrue(source.isFile && source.length() > 0)
            uploadCount += 1
            uploadedBytes = source.readBytes()
            onUpload(revisions)
            return CloudRevision(
                fileId = "uploaded-$uploadCount",
                fileName = revision.fileName,
                createdAt = revision.createdAt,
                deviceId = revision.deviceId,
                baseRevisionId = revision.baseRevisionId,
                contentFingerprint = revision.contentFingerprint,
                driveVersion = uploadCount.toLong(),
                modifiedTime = "2026-09-18T00:00:00Z",
                sizeBytes = source.length(),
                mergedRevisionIds = revision.mergedRevisionIds,
            ).also {
                uploadedRevision = it
                revisions.add(0, it)
            }
        }

        override suspend fun downloadRevision(revision: CloudRevision, destination: File) {
            downloadCount += 1
            onDownload(revisions)
            val bytes = downloadBytes ?: error("This test must not download a cloud revision.")
            destination.writeBytes(bytes)
        }

        override suspend fun deleteRevision(fileId: String) {
            deleteCount += 1
        }
    }

    private companion object {
        const val TEST_PASSWORD = "test-only-sync-password"
        const val CORRECT_REMOTE_PASSWORD = "correct-remote-password"
        const val WRONG_REMOTE_PASSWORD = "wrong-remote-password"
    }
}
