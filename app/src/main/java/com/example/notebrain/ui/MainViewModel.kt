package com.example.notebrain.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notebrain.data.local.Note
import com.example.notebrain.repository.NoteRepository
import com.example.notebrain.repository.SyncRepository
import com.example.notebrain.utils.SyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val noteRepository = NoteRepository(application)
    private val syncRepository = SyncRepository(application)

    val activeNotes: StateFlow<List<Note>> = noteRepository.activeNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncStatus: StateFlow<SyncStatus> = syncRepository.syncStatus

    fun saveNoteFromShare(title: String, content: String, onSaved: (Note) -> Unit) {
        viewModelScope.launch {
            val note = noteRepository.createNoteFromShare(title, content)
            onSaved(note)
        }
    }

    fun deleteNoteLocally(id: String) {
        viewModelScope.launch {
            noteRepository.deleteNoteLocally(id)
        }
    }

    fun undoDeleteNote(note: Note) {
        viewModelScope.launch {
            noteRepository.undoDeleteNote(note)
        }
    }

    fun undoShareNote(noteId: String) {
        viewModelScope.launch {
            noteRepository.undoShareNote(noteId)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncRepository.performSync()
        }
    }
}
