package com.ced2711.lifetracker.data.vault

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.domain.model.VaultEntryDraft
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.local.VaultDao
import com.ced2711.lifetracker.data.local.VaultEntryEntity
import com.ced2711.lifetracker.data.local.VaultSaveOutcome
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultDaoIntegrationTest {
    private lateinit var database: TaskLedgerDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TaskLedgerDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun vaultRowsRemainOpaqueAndCanBeCleared() = runBlocking {
        val row = VaultEntryEntity(
            id = "6ab976b4-707f-4c1e-ae1d-8dc9cb672993",
            formatVersion = 1,
            payloadIv = ByteArray(12) { it.toByte() },
            payloadCiphertext = ByteArray(48) { (it + 1).toByte() },
            createdAt = 10L,
            updatedAt = 20L,
        )
        database.vaultDao().insert(row)

        val stored = database.vaultDao().observeEncrypted().first().single()
        assertEquals(row.id, stored.id)
        assertTrue(row.payloadIv.contentEquals(stored.payloadIv))
        assertTrue(row.payloadCiphertext.contentEquals(stored.payloadCiphertext))

        database.vaultDao().deleteAll()
        assertTrue(database.vaultDao().getEncrypted().isEmpty())
    }

    @Test
    fun vaultEditAdvancesPastPriorUpdateWhenWallClockRollsBack() = runBlocking {
        var wallClock = 2_000_000L
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = VaultRepository(
            dao = database.vaultDao(),
            keyManager = VaultKeyManager(context),
            now = { wallClock },
        )
        val session = VaultSession(ByteArray(32) { (it + 1).toByte() })
        try {
            val created = repository.save(VaultEntryDraft(label = "First"), session)
            wallClock = 1_000_000L

            val edited = repository.save(
                VaultEntryDraft(id = created.id, label = "Edited"),
                session,
            )

            assertEquals(created.createdAt, edited.createdAt)
            assertEquals(created.updatedAt + 1L, edited.updatedAt)
            assertEquals(edited, repository.loadEntries(session).single())
        } finally {
            session.close()
        }
    }

    @Test
    fun concurrentEditsSerializeTimestampCalculationWhenWallClockRollsBack() = runBlocking {
        var wallClock = 2_000_000L
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val session = VaultSession(ByteArray(32) { (it + 1).toByte() })
        var pausingDao: PausingVaultDao? = null
        try {
            val initialRepository = VaultRepository(
                dao = database.vaultDao(),
                keyManager = VaultKeyManager(context),
                now = { wallClock },
            )
            val created = initialRepository.save(VaultEntryDraft(label = "Initial"), session)
            wallClock = 1_000_000L

            val controlledDao = PausingVaultDao(database.vaultDao())
            pausingDao = controlledDao
            val repository = VaultRepository(
                dao = controlledDao,
                keyManager = VaultKeyManager(context),
                now = { wallClock },
                ioDispatcher = Dispatchers.Unconfined,
                cryptoDispatcher = Dispatchers.Unconfined,
            )
            val first = async(Dispatchers.Unconfined) {
                repository.save(
                    VaultEntryDraft(id = created.id, label = "First edit"),
                    session,
                )
            }
            controlledDao.firstSaveReached.await()

            val second = async(Dispatchers.Unconfined) {
                repository.save(
                    VaultEntryDraft(id = created.id, label = "Second edit"),
                    session,
                )
            }
            val staleSecondRead = controlledDao.secondReadReached.isCompleted
            val readWhileSavePending = repository.loadEntries(session).single()
            controlledDao.releaseFirstSave.complete(Unit)

            val edits = listOf(first, second).awaitAll()
            assertEquals(false, staleSecondRead)
            assertEquals(created, readWhileSavePending)
            assertEquals(created.updatedAt + 1L, edits[0].updatedAt)
            assertEquals(created.updatedAt + 2L, edits[1].updatedAt)
            assertEquals("Second edit", repository.loadEntries(session).single().label)
        } finally {
            pausingDao?.releaseFirstSave?.complete(Unit)
            session.close()
        }
    }

    @Test
    fun capacityCheckCountsReplacementBytesAndEntriesAtomically() = runBlocking {
        val dao = database.vaultDao()
        val first = encryptedRow(
            id = "11111111-1111-4111-8111-111111111111",
            ivSeed = 1,
            ciphertextBytes = 40,
        )
        val second = encryptedRow(
            id = "22222222-2222-4222-8222-222222222222",
            ivSeed = 2,
            ciphertextBytes = 40,
        )
        val maxBytes = 2L * (12L + 40L)

        assertEquals(
            VaultSaveOutcome.SAVED,
            dao.saveWithinCapacity(
                first,
                expectExisting = false,
                maxEntryCount = 2,
                maxEncryptedBytes = maxBytes,
            ),
        )
        assertEquals(
            VaultSaveOutcome.SAVED,
            dao.saveWithinCapacity(
                second,
                expectExisting = false,
                maxEntryCount = 2,
                maxEncryptedBytes = maxBytes,
            ),
        )
        assertEquals(
            VaultSaveOutcome.ENCRYPTED_BYTES_LIMIT_REACHED,
            dao.saveWithinCapacity(
                first.copy(payloadCiphertext = ByteArray(41) { 7 }),
                expectExisting = true,
                maxEntryCount = 2,
                maxEncryptedBytes = maxBytes,
            ),
        )
        assertEquals(
            VaultSaveOutcome.SAVED,
            dao.saveWithinCapacity(
                first.copy(payloadCiphertext = ByteArray(40) { 8 }),
                expectExisting = true,
                maxEntryCount = 2,
                maxEncryptedBytes = maxBytes,
            ),
        )
        assertEquals(
            VaultSaveOutcome.ENTRY_LIMIT_REACHED,
            dao.saveWithinCapacity(
                encryptedRow(
                    id = "33333333-3333-4333-8333-333333333333",
                    ivSeed = 3,
                    ciphertextBytes = 1,
                ),
                expectExisting = false,
                maxEntryCount = 2,
                maxEncryptedBytes = maxBytes,
            ),
        )
        assertEquals(2, dao.countEntries())
        assertEquals(maxBytes, dao.encryptedBytes())
    }

    @Test
    fun updateCannotResurrectAnEntryDeletedBeforeTheTransaction() = runBlocking {
        val outcome = database.vaultDao().saveWithinCapacity(
            entry = encryptedRow(
                id = "44444444-4444-4444-8444-444444444444",
                ivSeed = 4,
                ciphertextBytes = 40,
            ),
            expectExisting = true,
            maxEntryCount = 10,
            maxEncryptedBytes = 1_000L,
        )

        assertEquals(VaultSaveOutcome.ENTRY_NOT_FOUND, outcome)
        assertEquals(0, database.vaultDao().countEntries())
    }

    @Test
    fun concurrentSavesCannotBothPassTheSameByteBudget() = runBlocking {
        val dao = database.vaultDao()
        val oneRowBudget = 12L + 60L
        val rows = listOf(
            encryptedRow(
                id = "55555555-5555-4555-8555-555555555555",
                ivSeed = 5,
                ciphertextBytes = 60,
            ),
            encryptedRow(
                id = "66666666-6666-4666-8666-666666666666",
                ivSeed = 6,
                ciphertextBytes = 60,
            ),
        )

        val outcomes = coroutineScope {
            rows.map { row ->
                async(Dispatchers.Default) {
                    dao.saveWithinCapacity(
                        row,
                        expectExisting = false,
                        maxEntryCount = 10,
                        maxEncryptedBytes = oneRowBudget,
                    )
                }
            }.awaitAll()
        }

        assertEquals(1, outcomes.count { it == VaultSaveOutcome.SAVED })
        assertEquals(
            1,
            outcomes.count { it == VaultSaveOutcome.ENCRYPTED_BYTES_LIMIT_REACHED },
        )
        assertEquals(1, dao.countEntries())
        assertTrue(dao.encryptedBytes() <= oneRowBudget)
    }

    private fun encryptedRow(
        id: String,
        ivSeed: Int,
        ciphertextBytes: Int,
    ) = VaultEntryEntity(
        id = id,
        formatVersion = 1,
        payloadIv = ByteArray(12) { (ivSeed + it).toByte() },
        payloadCiphertext = ByteArray(ciphertextBytes) { (ivSeed * 3 + it).toByte() },
        createdAt = 10L,
        updatedAt = 20L,
    )

    private class PausingVaultDao(
        private val delegate: VaultDao,
    ) : VaultDao by delegate {
        val firstSaveReached = CompletableDeferred<Unit>()
        val secondReadReached = CompletableDeferred<Unit>()
        val releaseFirstSave = CompletableDeferred<Unit>()
        private val shouldPauseSave = AtomicBoolean(true)
        private val editReadCount = AtomicInteger(0)

        override suspend fun getById(id: String): VaultEntryEntity? {
            if (editReadCount.incrementAndGet() == 2) secondReadReached.complete(Unit)
            return delegate.getById(id)
        }

        override suspend fun saveWithinCapacity(
            entry: VaultEntryEntity,
            expectExisting: Boolean,
            maxEntryCount: Int,
            maxEncryptedBytes: Long,
        ): VaultSaveOutcome {
            if (shouldPauseSave.compareAndSet(true, false)) {
                firstSaveReached.complete(Unit)
                releaseFirstSave.await()
            }
            return delegate.saveWithinCapacity(
                entry = entry,
                expectExisting = expectExisting,
                maxEntryCount = maxEntryCount,
                maxEncryptedBytes = maxEncryptedBytes,
            )
        }
    }
}
