package com.example.notebrain.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.notebrain.R
import com.example.notebrain.repository.NoteRepository
import kotlinx.coroutines.launch

class EditNoteActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etContent: EditText
    private lateinit var btnDelete: ImageButton
    private lateinit var repository: NoteRepository

    private var noteId: String? = null
    private var isDeleted: Boolean = false

    companion object {
        private const val EXTRA_NOTE_ID = "extra_note_id"

        fun createIntent(context: Context, noteId: String? = null): Intent {
            return Intent(context, EditNoteActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_note)

        repository = NoteRepository(this)
        noteId = intent.getStringExtra(EXTRA_NOTE_ID)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        toolbar.setNavigationOnClickListener {
            saveAndFinish()
        }

        etTitle = findViewById(R.id.etTitle)
        etContent = findViewById(R.id.etContent)
        btnDelete = findViewById(R.id.btnDelete)

        btnDelete.setOnClickListener {
            deleteAndFinish()
        }

        if (noteId != null) {
            lifecycleScope.launch {
                val note = repository.getNoteById(noteId!!)
                if (note != null) {
                    etTitle.setText(note.title)
                    etContent.setText(note.content)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        saveAndFinish()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun saveAndFinish() {
        if (isDeleted) return

        val title = etTitle.text.toString()
        val content = etContent.text.toString()

        if (title.isBlank() && content.isBlank()) {
            if (noteId != null) {
                lifecycleScope.launch {
                    repository.deleteNoteLocally(noteId!!)
                }
            }
            finish()
            return
        }

        lifecycleScope.launch {
            repository.saveNote(title, content, noteId)
            finish()
        }
    }

    private fun deleteAndFinish() {
        isDeleted = true
        if (noteId != null) {
            lifecycleScope.launch {
                repository.deleteNoteLocally(noteId!!)
                finish()
            }
        } else {
            finish()
        }
    }
}
