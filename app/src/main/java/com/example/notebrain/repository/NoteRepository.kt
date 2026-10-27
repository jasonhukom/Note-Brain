package com.example.notebrain.repository

import android.content.Context
import com.example.notebrain.data.local.AppDatabase
import com.example.notebrain.data.local.Note
import com.example.notebrain.data.local.NoteDao
import com.example.notebrain.data.preferences.PreferencesManager
import com.example.notebrain.sync.SyncScheduler
import com.example.notebrain.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NoteRepository(
    private val noteDao: NoteDao,
    private val preferencesManager: PreferencesManager,
    private val onNoteMutated: (() -> Unit)? = null
) {
    constructor(context: Context) : this(
        noteDao = AppDatabase.getInstance(context).noteDao(),
        preferencesManager = PreferencesManager(context),
        onNoteMutated = { SyncScheduler.scheduleImmediateSync(context) }
    )

    val activeNotes: Flow<List<Note>> = noteDao.getAllActiveNotes()

    suspend fun getNoteById(id: String): Note? = noteDao.getNoteById(id)

    fun getNoteByIdFlow(id: String): Flow<Note?> = noteDao.getNoteByIdFlow(id)

    suspend fun saveNote(title: String, content: String, existingId: String? = null): Note {
        val now = DateUtils.currentUtcIsoString()
        val deviceId = preferencesManager.getDeviceId()

        val note = if (!existingId.isNullOrBlank()) {
            val existing = noteDao.getNoteById(existingId)
            if (existing != null) {
                existing.copy(
                    title = title.trim(),
                    content = content.trim(),
                    updatedAt = now,
                    syncState = Note.SYNC_STATE_PENDING,
                    modifiedByDeviceId = deviceId
                )
            } else {
                Note(
                    id = existingId,
                    title = title.trim(),
                    content = content.trim(),
                    createdAt = now,
                    updatedAt = now,
                    syncState = Note.SYNC_STATE_PENDING,
                    modifiedByDeviceId = deviceId
                )
            }
        } else {
            Note(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                content = content.trim(),
                createdAt = now,
                updatedAt = now,
                syncState = Note.SYNC_STATE_PENDING,
                modifiedByDeviceId = deviceId
            )
        }

        noteDao.insertNote(note)
        onNoteMutated?.invoke()
        return note
    }

    suspend fun createNoteFromShare(title: String, content: String): Note {
        val now = DateUtils.currentUtcIsoString()
        val deviceId = preferencesManager.getDeviceId()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            content = content.trim(),
            createdAt = now,
            updatedAt = now,
            syncState = Note.SYNC_STATE_PENDING,
            modifiedByDeviceId = deviceId
        )
        noteDao.insertNote(note)
        onNoteMutated?.invoke()
        return note
    }

    suspend fun deleteNoteLocally(id: String) {
        val existing = noteDao.getNoteById(id) ?: return
        val now = DateUtils.currentUtcIsoString()
        val updatedNote = existing.copy(
            isDeleted = true,
            deletedAt = now,
            updatedAt = now,
            syncState = Note.SYNC_STATE_PENDING
        )
        noteDao.insertNote(updatedNote)
        onNoteMutated?.invoke()
    }

    suspend fun undoDeleteNote(note: Note) {
        val restoredNote = note.copy(
            isDeleted = false,
            deletedAt = null,
            updatedAt = DateUtils.currentUtcIsoString(),
            syncState = Note.SYNC_STATE_PENDING
        )
        noteDao.insertNote(restoredNote)
        onNoteMutated?.invoke()
    }

    suspend fun undoShareNote(noteId: String) {
        noteDao.deleteNotePermanently(noteId)
        onNoteMutated?.invoke()
    }
}
