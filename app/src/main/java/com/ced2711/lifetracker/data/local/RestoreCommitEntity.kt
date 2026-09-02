package com.ced2711.lifetracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Internal restore coordination state. This table is intentionally excluded from backups. */
@Entity(tableName = "restore_commit_state")
data class RestoreCommitEntity(
    @PrimaryKey val singletonId: Int = SINGLETON_ID,
    val restoreToken: String,
) {
    init {
        require(singletonId == SINGLETON_ID) { "Only the restore commit singleton is supported." }
    }

    companion object {
        const val SINGLETON_ID = 1
    }
}
