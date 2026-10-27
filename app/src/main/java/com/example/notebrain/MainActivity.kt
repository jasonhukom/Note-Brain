package com.example.notebrain

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.notebrain.sync.NetworkMonitor
import com.example.notebrain.sync.SyncScheduler
import com.example.notebrain.ui.EditNoteActivity
import com.example.notebrain.ui.MainViewModel
import com.example.notebrain.ui.NotesAdapter
import com.example.notebrain.ui.SettingsActivity
import com.example.notebrain.ui.ShareHandler
import com.example.notebrain.ui.ShareResult
import com.example.notebrain.utils.SyncStatus
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: NotesAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var tvSyncBadge: TextView
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        networkMonitor = NetworkMonitor(this)
        networkMonitor.startMonitoring()
        SyncScheduler.schedulePeriodicSync(this)

        val rvNotes = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotes)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvSyncBadge = findViewById(R.id.tvSyncBadge)

        adapter = NotesAdapter { note ->
            val intent = EditNoteActivity.createIntent(this, note.id)
            startActivity(intent)
        }

        rvNotes.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        rvNotes.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(EditNoteActivity.createIntent(this))
        }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(SettingsActivity.createIntent(this))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.activeNotes.collect { notes ->
                        adapter.submitList(notes)
                        if (notes.isEmpty()) {
                            tvEmpty.visibility = View.VISIBLE
                        } else {
                            tvEmpty.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.syncStatus.collect { status ->
                        renderSyncBadge(status)
                    }
                }
            }
        }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        SyncScheduler.scheduleImmediateSync(this)
        viewModel.syncNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.stopMonitoring()
    }

    private fun handleShareIntent(intent: Intent?) {
        when (val result = ShareHandler.extractShareContent(intent)) {
            is ShareResult.Success -> {
                viewModel.saveNoteFromShare(result.title, result.content) { savedNote ->
                    val rootView = findViewById<View>(R.id.main)
                    Snackbar.make(rootView, R.string.saved_to_justnote, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo) {
                            viewModel.undoShareNote(savedNote.id)
                        }
                        .show()
                }
            }
            is ShareResult.EmptyPayload -> {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            }
            is ShareResult.Ignored -> {
                // Do nothing
            }
        }
    }

    private fun renderSyncBadge(status: SyncStatus) {
        when (status) {
            SyncStatus.IDLE, SyncStatus.NOT_CONFIGURED -> {
                tvSyncBadge.visibility = View.GONE
            }
            SyncStatus.SYNCING -> {
                tvSyncBadge.visibility = View.VISIBLE
                tvSyncBadge.text = "Syncing..."
            }
            SyncStatus.SYNCED -> {
                tvSyncBadge.visibility = View.VISIBLE
                tvSyncBadge.text = "Synced"
            }
            SyncStatus.WAITING_FOR_NETWORK -> {
                tvSyncBadge.visibility = View.VISIBLE
                tvSyncBadge.text = "Offline"
            }
            SyncStatus.AUTH_REQUIRED -> {
                tvSyncBadge.visibility = View.VISIBLE
                tvSyncBadge.text = "Auth Error"
            }
            SyncStatus.CONFLICT -> {
                tvSyncBadge.visibility = View.VISIBLE
                tvSyncBadge.text = "Conflict"
            }
            SyncStatus.ERROR -> {
                tvSyncBadge.visibility = View.VISIBLE
                tvSyncBadge.text = "Sync Error"
            }
        }
    }
}
