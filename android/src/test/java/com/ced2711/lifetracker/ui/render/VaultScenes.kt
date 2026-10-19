package com.ced2711.lifetracker.ui.render

import androidx.compose.runtime.Composable
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.ui.vault.VaultAccessState
import com.ced2711.lifetracker.ui.vault.VaultActions
import com.ced2711.lifetracker.ui.vault.VaultConfirmation
import com.ced2711.lifetracker.ui.vault.VaultContent
import com.ced2711.lifetracker.ui.vault.VaultEditorState
import com.ced2711.lifetracker.ui.vault.VaultUiState

/** Made-up accounts for the pictures; nothing here is a real login. */
private val vaultSampleEntries: List<VaultEntry> = listOf(
    sampleEntry("1", "Bank", "cedric.m", "river-Stone-4821", "https://online.examplebank.com", "Card ends in 4471.\nSecurity question: first school."),
    sampleEntry("2", "Email", "cedric@example.com", "Maple!Harbor92", "https://mail.example.com", ""),
    sampleEntry("3", "GitHub", "ced2711", "paper-Lantern-77", "https://github.com", "Recovery codes are in the blue folder."),
    sampleEntry("4", "Home Wi-Fi", "LivingRoom-5G", "sunny-kitchen-table", "", ""),
    sampleEntry("5", "School portal", "s2024117", "Winter#Bridge5", "https://portal.example.edu", ""),
    sampleEntry("6", "", "", "", "https://www.streaming.example.com/login", ""),
    sampleEntry("7", "学校邮箱", "cedric@school.example.cn", "Lake-Paper-31", "", ""),
)

private fun sampleEntry(id: String, label: String, account: String, password: String, website: String, notes: String) =
    VaultEntry(id = id, label = label, account = account, password = password, website = website, notes = notes, createdAt = 1L, updatedAt = 1L)

private fun unlockedState(
    entries: List<VaultEntry> = vaultSampleEntries,
    viewingEntryId: String? = null,
    editor: VaultEditorState? = null,
    offers: Boolean = false,
    query: String = "",
    filtered: List<VaultEntry> = entries,
) = VaultUiState(
    access = VaultAccessState.Unlocked,
    hasVault = true,
    entries = entries,
    filteredEntries = filtered,
    query = query,
    editor = editor,
    viewingEntryId = viewingEntryId,
    offerFingerprintEnrollment = offers,
    offerModernUpgrade = offers,
)

@Composable
private fun Vault(state: VaultUiState, isWide: Boolean, deviceSecure: Boolean = true, confirmation: VaultConfirmation? = null) {
    VaultContent(uiState = state, deviceSecure = deviceSecure, isWide = isWide, actions = VaultActions(), initialConfirmation = confirmation)
}

/** Scenes of the Vault screens for ScreenRenderTest; see RenderScene. */
internal val vaultScenes: List<RenderScene> = listOf(
    RenderScene("vault-locked", auxiliaryTitle = "Password vault") { isWide ->
        Vault(VaultUiState(access = VaultAccessState.Locked, hasVault = true), isWide)
    },
    RenderScene("vault-create", auxiliaryTitle = "Password vault") { isWide ->
        Vault(VaultUiState(access = VaultAccessState.Locked, hasVault = false), isWide)
    },
    RenderScene("vault-no-screen-lock", auxiliaryTitle = "Password vault") { isWide ->
        Vault(VaultUiState(access = VaultAccessState.Locked, hasVault = true), isWide, deviceSecure = false)
    },
    RenderScene("vault-unlocking", auxiliaryTitle = "Password vault") { isWide ->
        Vault(VaultUiState(access = VaultAccessState.Unlocking, hasVault = true), isWide)
    },
    RenderScene("vault-error", auxiliaryTitle = "Password vault") { isWide ->
        Vault(VaultUiState(access = VaultAccessState.Error("Secure authentication is unavailable."), hasVault = true), isWide)
    },
    RenderScene("vault-entries", auxiliaryTitle = "Password vault") { isWide ->
        Vault(unlockedState(), isWide)
    },
    RenderScene("vault-offers", auxiliaryTitle = "Password vault") { isWide ->
        Vault(unlockedState(entries = vaultSampleEntries.take(3), offers = true), isWide)
    },
    RenderScene("vault-empty", auxiliaryTitle = "Password vault") { isWide ->
        Vault(unlockedState(entries = emptyList()), isWide)
    },
    RenderScene("vault-no-match", auxiliaryTitle = "Password vault") { isWide ->
        Vault(unlockedState(query = "paypal", filtered = emptyList()), isWide)
    },
    RenderScene("vault-reading", auxiliaryTitle = "Password vault") { isWide ->
        Vault(unlockedState(viewingEntryId = "1"), isWide)
    },
    RenderScene("vault-editor", auxiliaryTitle = "Password vault") { isWide ->
        val entry = vaultSampleEntries[0]
        Vault(
            unlockedState(
                viewingEntryId = entry.id,
                editor = VaultEditorState(entry.id, entry.label, entry.account, entry.password, entry.website, entry.notes),
            ),
            isWide,
        )
    },
    RenderScene("vault-editor-new", auxiliaryTitle = "Password vault") { isWide ->
        Vault(unlockedState(editor = VaultEditorState(label = "Library", password = "quiet-Shelf-19", passwordVisible = true)), isWide)
    },
    RenderScene("vault-delete-confirm", auxiliaryTitle = "Password vault") { isWide ->
        Vault(unlockedState(viewingEntryId = "1"), isWide, confirmation = VaultConfirmation.Delete("1"))
    },
)
