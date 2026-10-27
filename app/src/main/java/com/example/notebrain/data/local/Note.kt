package com.example.notebrain.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey
    val id: String,
    val title: String = "",
    val content: String = "",
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val isDeleted: Boolean = false,
    val syncState: String = SYNC_STATE_PENDING,
    val lastSyncedSha: String? = null,
    val lastSyncedUpdatedAt: String? = null,
    val modifiedByDeviceId: String = ""
) {
    companion object {
        const val SYNC_STATE_PENDING = "PENDING"
        const val SYNC_STATE_SYNCED = "SYNCED"
        const val SYNC_STATE_CONFLICT = "CONFLICT"
    }
}
