package com.ced2711.lifetracker.domain.model

data class VaultEntry(
    val id: String,
    val label: String,
    val account: String,
    val password: String,
    val website: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class VaultEntryDraft(
    val id: String? = null,
    val label: String = "",
    val account: String = "",
    val password: String = "",
    val website: String = "",
    val notes: String = "",
)
