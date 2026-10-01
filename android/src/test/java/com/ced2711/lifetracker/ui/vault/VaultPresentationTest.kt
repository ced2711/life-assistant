package com.ced2711.lifetracker.ui.vault

import com.ced2711.lifetracker.domain.model.VaultEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPresentationTest {
    @Test
    fun displayLabelUsesOnlyNonSecretFallbacks() {
        assertEquals("Mail", entry(label = " Mail ").displayLabel())
        assertEquals(
            "Example.com",
            entry(
                account = "user@example.net",
                website = "https://www.Example.com/login",
                notes = "Account notes",
            ).displayLabel(),
        )
        assertEquals("例子.测试", entry(website = "例子.测试/登录").displayLabel())
        assertEquals("用户@example.com", entry(account = " 用户@example.com ").displayLabel())
        assertEquals("School portal", entry(notes = "\n School portal \nRecovery details").displayLabel())
        assertEquals("Untitled", entry(password = "must-never-be-a-label").displayLabel())
    }

    @Test
    fun derivedLabelIsPersistedAndRemainsEditable() {
        val derived = VaultEditorState(
            website = "https://www.example.com/login",
            account = "person@example.net",
        ).toDraft().withDerivedLabel()

        assertEquals("example.com", derived.label)
        assertEquals("Custom", derived.copy(label = "Custom").withDerivedLabel().label)
    }

    @Test
    fun notesDerivedLabelIsBoundedWithoutSplittingEmoji() {
        val notes = "🔐".repeat(81)
        val label = deriveVaultLabel(website = "", account = "", notes = notes)

        assertEquals(80, label.codePointCount(0, label.length))
        assertEquals(160, label.length)
    }

    @Test
    fun searchNeverMatchesPassword() {
        val secret = entry(label = "Mail", password = "do-not-index-this")

        assertTrue(filteredVaultEntries(listOf(secret), "Mail").isNotEmpty())
        assertTrue(filteredVaultEntries(listOf(secret), "do-not-index-this").isEmpty())
    }

    @Test
    fun editorRequiresAtLeastOneNonBlankField() {
        assertFalse(VaultEditorState(notes = "  ").isValid)
        assertTrue(VaultEditorState(password = "密碼").isValid)
    }

    private fun entry(
        label: String = "",
        account: String = "",
        password: String = "",
        website: String = "",
        notes: String = "",
    ) = VaultEntry(
        id = "6ab976b4-707f-4c1e-ae1d-8dc9cb672993",
        label = label,
        account = account,
        password = password,
        website = website,
        notes = notes,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
