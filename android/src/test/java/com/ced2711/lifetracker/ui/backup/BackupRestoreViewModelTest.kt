package com.ced2711.lifetracker.ui.backup

import com.ced2711.lifetracker.data.backup.BackupAuthenticationException
import com.ced2711.lifetracker.data.backup.BackupExportResult
import com.ced2711.lifetracker.data.backup.BackupPreview
import com.ced2711.lifetracker.data.backup.BackupRestoreResult
import com.ced2711.lifetracker.data.vault.VaultSession
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupRestoreViewModelTest {
    @Test
    fun noVaultExportsDirectlyAndClosesStreamAndPassword() {
        val gateway = FakeGateway()
        val documents = FakeDocuments()
        val password = "long-password".toCharArray()
        val viewModel = viewModel(gateway, documents, hasVault = false)

        viewModel.exportBackup(BackupDocumentReference("export"), password)

        assertEquals(1, gateway.exportCount)
        assertNull(gateway.exportSession)
        assertTrue(documents.output.closed)
        assertTrue(password.all { it == '\u0000' })
        assertTrue(documents.deleted.isEmpty())
        assertNull(viewModel.authenticationRequest.value)
        assertEquals(BackupRestoreNotice.EXPORT_COMPLETE, viewModel.uiState.value.notice)
    }

    @Test
    fun failedExportDeletesPartialDocumentWithoutChangingNotice() {
        val gateway = FakeGateway(exportFailure = IOException("provider failed"))
        val documents = FakeDocuments()
        val viewModel = viewModel(gateway, documents, hasVault = false)

        viewModel.exportBackup(
            BackupDocumentReference("partial-export"),
            "long-password".toCharArray(),
        )

        assertEquals(BackupRestoreNotice.EXPORT_FAILED, viewModel.uiState.value.notice)
        assertEquals(
            listOf(BackupDocumentReference("partial-export")),
            documents.deleted,
        )
    }

    @Test
    fun vaultExportRequestsFreshAuthenticationAndConsumesOneLease() {
        val gateway = FakeGateway()
        val documents = FakeDocuments()
        val viewModel = viewModel(gateway, documents, hasVault = true)
        val password = "long-password".toCharArray()

        viewModel.exportBackup(BackupDocumentReference("export"), password)
        val request = requireNotNull(viewModel.authenticationRequest.value)

        assertEquals(BackupAuthenticationPurpose.EXPORT, request.purpose)
        assertEquals(0, gateway.exportCount)
        assertTrue(viewModel.markAuthenticationDispatched(request.nonce))
        assertFalse(viewModel.markAuthenticationDispatched(request.nonce))
        viewModel.markAuthenticationInProgress(request.nonce)
        val lease = VaultSession(ByteArray(32) { 1 })
        viewModel.provideAuthenticatedLease(request.nonce, lease)

        assertEquals(1, gateway.exportCount)
        assertSame(lease, gateway.exportSession)
        assertThrows(IllegalStateException::class.java) { lease.useKey { it.encoded } }
        assertTrue(password.all { it == '\u0000' })

        val staleLease = VaultSession(ByteArray(32) { 2 })
        viewModel.provideAuthenticatedLease(request.nonce, staleLease)
        assertEquals(1, gateway.exportCount)
        assertThrows(IllegalStateException::class.java) { staleLease.useKey { it.encoded } }
    }

    @Test
    fun authenticationCancellationClearsPendingPassword() {
        val password = "long-password".toCharArray()
        val documents = FakeDocuments()
        val viewModel = viewModel(FakeGateway(), documents, hasVault = true)
        viewModel.exportBackup(BackupDocumentReference("export"), password)
        val request = requireNotNull(viewModel.authenticationRequest.value)

        viewModel.authenticationCancelled(request.nonce)

        assertTrue(password.all { it == '\u0000' })
        assertNull(viewModel.authenticationRequest.value)
        assertEquals(listOf(BackupDocumentReference("export")), documents.deleted)
        assertEquals(
            BackupRestoreNotice.VAULT_AUTHENTICATION_CANCELLED,
            viewModel.uiState.value.notice,
        )
    }

    @Test
    fun deleteFailureDoesNotReplaceAuthenticationCancellationNotice() {
        val documents = FakeDocuments().apply {
            deleteFailure = IOException("delete failed")
        }
        val viewModel = viewModel(FakeGateway(), documents, hasVault = true)
        viewModel.exportBackup(
            BackupDocumentReference("export"),
            "long-password".toCharArray(),
        )
        val request = requireNotNull(viewModel.authenticationRequest.value)

        viewModel.authenticationCancelled(request.nonce)

        assertEquals(
            BackupRestoreNotice.VAULT_AUTHENTICATION_CANCELLED,
            viewModel.uiState.value.notice,
        )
    }

    @Test
    fun preparedCleanupClearsReadyStateButLeavesActiveCommitToRepository() {
        val ready = FakePreparedRestore(requiresVaultAuthentication = false)
        assertTrue(closePreparedOnClear(ready))
        assertTrue(ready.closed)

        val committing = FakePreparedRestore(
            requiresVaultAuthentication = false,
            closeFailure = IllegalStateException("commit in progress"),
        )
        assertFalse(closePreparedOnClear(committing))
        assertFalse(committing.closed)
    }

    @Test
    fun preparedRestoreShowsPreviewWithoutWritingAndCancelClosesIt() {
        val prepared = FakePreparedRestore(requiresVaultAuthentication = false)
        val gateway = FakeGateway(prepared = prepared)
        val documents = FakeDocuments()
        val password = "long-password".toCharArray()
        val viewModel = viewModel(gateway, documents, hasVault = false)

        viewModel.prepareRestore(BackupDocumentReference("restore"), password)

        assertEquals(0, prepared.commitCount)
        assertFalse(prepared.closed)
        assertTrue(documents.input.closed)
        assertTrue(password.all { it == '\u0000' })
        assertEquals("Aug 20, 2026, 3:00 AM", viewModel.uiState.value.restorePreview?.createdAtLabel)

        viewModel.discardPreparedRestore()

        assertTrue(prepared.closed)
        assertNull(viewModel.uiState.value.restorePreview)
    }

    @Test
    fun includedPersonalBackupUsesPrivateAssetStreamAndClearsPassword() {
        val included = TrackingInputStream(byteArrayOf(9))
        val password = "long-password".toCharArray()
        val viewModel = viewModel(
            gateway = FakeGateway(),
            documents = FakeDocuments(),
            hasVault = false,
            openIncludedBackup = { included },
        )

        viewModel.prepareIncludedBackup(password)

        assertTrue(included.closed)
        assertTrue(password.all { it == '\u0000' })
        assertEquals(4, viewModel.uiState.value.restorePreview?.todoCount)
    }

    @Test
    fun restoreRequiringVaultWaitsForLeaseThenCommits() {
        val prepared = FakePreparedRestore(requiresVaultAuthentication = true)
        val viewModel = viewModel(
            FakeGateway(prepared = prepared),
            FakeDocuments(),
            hasVault = false,
        )
        viewModel.prepareRestore(
            BackupDocumentReference("restore"),
            "long-password".toCharArray(),
        )

        viewModel.commitPreparedRestore()
        val request = requireNotNull(viewModel.authenticationRequest.value)

        assertEquals(BackupAuthenticationPurpose.RESTORE, request.purpose)
        assertEquals(0, prepared.commitCount)
        viewModel.provideAuthenticatedLease(request.nonce, VaultSession(ByteArray(32) { 3 }))
        assertEquals(1, prepared.commitCount)
        assertEquals(BackupRestoreNotice.RESTORE_COMPLETE, viewModel.uiState.value.notice)
    }

    @Test
    fun prepareFailureUsesFixedNoticeAndDoesNotExposeException() {
        val gateway = FakeGateway(prepareFailure = BackupAuthenticationException())
        val viewModel = viewModel(gateway, FakeDocuments(), hasVault = false)

        viewModel.prepareRestore(
            BackupDocumentReference("restore"),
            "long-password".toCharArray(),
        )

        assertEquals(
            BackupRestoreNotice.WRONG_PASSWORD_OR_MODIFIED,
            viewModel.uiState.value.notice,
        )
        assertNull(viewModel.uiState.value.restorePreview)
    }

    @Test
    fun successfulRestoreRunsRebuildCallbacks() {
        val prepared = FakePreparedRestore(requiresVaultAuthentication = false)
        var rebuildCount = 0
        val viewModel = viewModel(
            gateway = FakeGateway(prepared = prepared),
            documents = FakeDocuments(),
            hasVault = false,
            rebuild = { rebuildCount++ },
        )
        viewModel.prepareRestore(
            BackupDocumentReference("restore"),
            "long-password".toCharArray(),
        )

        viewModel.commitPreparedRestore()

        assertEquals(1, prepared.commitCount)
        assertEquals(1, rebuildCount)
        assertTrue(prepared.closed)
        assertEquals(BackupRestoreNotice.RESTORE_COMPLETE, viewModel.uiState.value.notice)
    }

    private fun viewModel(
        gateway: FakeGateway,
        documents: FakeDocuments,
        hasVault: Boolean,
        rebuild: suspend () -> Unit = {},
        openIncludedBackup: (() -> InputStream?)? = null,
    ) = BackupRestoreViewModel(
        gateway = gateway,
        documents = documents,
        hasVault = { hasVault },
        previewLabel = { "Aug 20, 2026, 3:00 AM" },
        rebuildAfterRestore = rebuild,
        openIncludedBackup = openIncludedBackup,
        dispatcher = Dispatchers.Unconfined,
    )
}

private class FakeGateway(
    private val prepared: FakePreparedRestore = FakePreparedRestore(false),
    private val prepareFailure: Throwable? = null,
    private val exportFailure: Throwable? = null,
) : BackupGateway {
    var exportCount = 0
    var exportSession: VaultSession? = null

    override suspend fun export(
        destination: OutputStream,
        password: CharArray,
        vaultSession: VaultSession?,
    ): BackupExportResult {
        exportFailure?.let { throw it }
        exportCount++
        exportSession = vaultSession
        destination.write(7)
        return BackupExportResult(TEST_PREVIEW, 1)
    }

    override suspend fun prepareRestore(
        source: InputStream,
        password: CharArray,
    ): BackupPreparedRestore {
        prepareFailure?.let { throw it }
        source.read()
        return prepared
    }
}

private class FakePreparedRestore(
    override val requiresVaultAuthentication: Boolean,
    private val closeFailure: Throwable? = null,
) : BackupPreparedRestore {
    override val preview: BackupPreview = TEST_PREVIEW
    var commitCount = 0
    var closed = false

    override suspend fun commit(vaultSession: VaultSession?): BackupRestoreResult {
        commitCount++
        return BackupRestoreResult(preview, emptyList())
    }

    override fun close() {
        closeFailure?.let { throw it }
        closed = true
    }
}

private class FakeDocuments : BackupDocumentAccess {
    val output = TrackingOutputStream()
    val input = TrackingInputStream(byteArrayOf(1))
    val deleted = mutableListOf<BackupDocumentReference>()
    var deleteFailure: Throwable? = null

    override fun openOutput(reference: BackupDocumentReference): OutputStream = output
    override fun openInput(reference: BackupDocumentReference): InputStream = input
    override fun delete(reference: BackupDocumentReference): Boolean {
        deleteFailure?.let { throw it }
        deleted += reference
        return true
    }
}

private class TrackingOutputStream : ByteArrayOutputStream() {
    var closed = false
    override fun close() {
        closed = true
        super.close()
    }
}

private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    var closed = false
    override fun close() {
        closed = true
        super.close()
    }
}

private val TEST_PREVIEW = BackupPreview(
    createdAt = 1_777_000_000_000L,
    todoCount = 4,
    ledgerCount = 3,
    vaultCount = 2,
    attachmentCount = 1,
    attachmentBytes = 64,
    totalBytes = 128,
)
