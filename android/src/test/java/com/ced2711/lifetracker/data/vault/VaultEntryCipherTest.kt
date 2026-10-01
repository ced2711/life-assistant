package com.ced2711.lifetracker.data.vault

import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.domain.model.VaultEntryDraft
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import android.os.Build

class VaultEntryCipherTest {
    private val entry = VaultEntry(
        id = "6ab976b4-707f-4c1e-ae1d-8dc9cb672993",
        label = "Email",
        account = "person@example.com",
        password = "correct horse battery staple",
        website = "https://example.com",
        notes = "Recovery code: 1234",
        createdAt = 10L,
        updatedAt = 20L,
    )

    @Test
    fun encryptRoundTripProtectsEverySensitiveField() {
        VaultSession(ByteArray(32) { it.toByte() }).use { session ->
            val encrypted = VaultEntryCipher.encrypt(entry, session)

            assertFalse(encrypted.payloadCiphertext.decodeToString().contains(entry.password))
            assertEquals(entry, VaultEntryCipher.decrypt(encrypted, session))
        }
    }

    @Test
    fun aadBindsCiphertextToUuidAndFormatVersion() {
        VaultSession(ByteArray(32) { (it + 1).toByte() }).use { session ->
            val encrypted = VaultEntryCipher.encrypt(entry, session)

            assertThrows(VaultCorruptEntryException::class.java) {
                VaultEntryCipher.decrypt(
                    encrypted.copy(id = "bdd6d856-4dde-4e9c-b3dd-43ea4a18970e"),
                    session,
                )
            }
            assertThrows(VaultCorruptEntryException::class.java) {
                VaultEntryCipher.decrypt(encrypted.copy(formatVersion = 2), session)
            }
        }
    }

    @Test
    fun eachEncryptionUsesANew96BitIv() {
        VaultSession(ByteArray(32) { (31 - it).toByte() }).use { session ->
            val first = VaultEntryCipher.encrypt(entry, session)
            val second = VaultEntryCipher.encrypt(entry, session)

            assertEquals(12, first.payloadIv.size)
            assertEquals(12, second.payloadIv.size)
            assertFalse(first.payloadIv.contentEquals(second.payloadIv))
            assertArrayEquals(
                VaultEntryCipher.aad(entry.id, 1),
                VaultEntryCipher.aad(entry.id, 1),
            )
        }
    }

    @Test
    fun closingForkedSessionDoesNotCloseItsAuthenticatedSource() {
        VaultSession(ByteArray(32) { it.toByte() }).use { source ->
            val lease = source.fork()

            lease.close()

            assertEquals(0, source.useKey { it.encoded.first().toInt() })
            assertThrows(IllegalStateException::class.java) {
                lease.useKey { it.encoded.first() }
            }
        }
    }

    @Test
    fun wrongOrClosedSessionCannotValidateExistingVaultCiphertext() {
        VaultSession(ByteArray(32) { it.toByte() }).use { correct ->
            val encrypted = VaultEntryCipher.encrypt(entry, correct)
            VaultSession(ByteArray(32) { (it + 1).toByte() }).use { wrong ->
                assertThrows(VaultCorruptEntryException::class.java) {
                    VaultEntryCipher.decrypt(encrypted, wrong)
                }
            }
            val closed = correct.fork().also { it.close() }
            assertThrows(IllegalStateException::class.java) {
                VaultEntryCipher.decrypt(encrypted, closed)
            }
        }
    }

    @Test
    fun draftRequiresAtLeastOneNonBlankFieldButNotALabel() {
        assertThrows(IllegalArgumentException::class.java) {
            validateVaultDraft(VaultEntryDraft(notes = "   "))
        }

        validateVaultDraft(VaultEntryDraft(password = "secret"))
    }

    @Test
    fun osUpgradeCanUnlockExistingLegacyEnvelopeButCannotCreateANewOne() {
        validateLegacyPreparationSupported(envelopePresent = true, sdkInt = Build.VERSION_CODES.R)

        assertThrows(VaultUnsupportedDeviceException::class.java) {
            validateLegacyPreparationSupported(
                envelopePresent = false,
                sdkInt = Build.VERSION_CODES.R,
            )
        }
        validateLegacyPreparationSupported(
            envelopePresent = false,
            sdkInt = Build.VERSION_CODES.Q,
        )
    }

    @Test
    fun singleFieldLimitUsesUtf8BytesAndAcceptsExactBoundary() {
        validateVaultDraft(
            VaultEntryDraft(notes = "😀".repeat(VaultPayloadLimits.MAX_FIELD_UTF8_BYTES / 4)),
        )

        val error = assertThrows(VaultPayloadTooLargeException::class.java) {
            validateVaultDraft(
                VaultEntryDraft(
                    notes = "😀".repeat(VaultPayloadLimits.MAX_FIELD_UTF8_BYTES / 4 + 1),
                ),
            )
        }
        assertTrue(error.message.orEmpty().contains("Notes"))
        assertTrue(error.message.orEmpty().contains("64 KiB"))
    }

    @Test
    fun combinedLimitAcceptsExactBoundaryAndRejectsOneMoreByte() {
        val maximumField = "x".repeat(VaultPayloadLimits.MAX_FIELD_UTF8_BYTES)
        validateVaultDraft(
            VaultEntryDraft(
                label = maximumField,
                account = maximumField,
                password = maximumField,
            ),
        )

        val error = assertThrows(VaultPayloadTooLargeException::class.java) {
            validateVaultDraft(
                VaultEntryDraft(
                    label = maximumField,
                    account = maximumField,
                    password = maximumField,
                    notes = "x",
                ),
            )
        }
        assertTrue(error.message.orEmpty().contains("Combined"))
        assertTrue(error.message.orEmpty().contains("192 KiB"))
    }
}
