package com.example.notebrain.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.notebrain.R
import com.example.notebrain.data.preferences.PreferencesManager
import com.example.notebrain.data.remote.GitHubApiClient
import com.example.notebrain.repository.SyncRepository
import com.example.notebrain.sync.SyncScheduler
import com.example.notebrain.utils.DateUtils
import com.example.notebrain.utils.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var etOwner: EditText
    private lateinit var etRepo: EditText
    private lateinit var etToken: EditText
    private lateinit var btnTest: Button
    private lateinit var btnSave: Button
    private lateinit var btnSyncNow: Button
    private lateinit var tvSyncStatus: TextView
    private lateinit var tvLastSynced: TextView

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var syncRepository: SyncRepository
    private val gitHubApiClient = GitHubApiClient()

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preferencesManager = PreferencesManager(this)
        syncRepository = SyncRepository(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        etOwner = findViewById(R.id.etOwner)
        etRepo = findViewById(R.id.etRepo)
        etToken = findViewById(R.id.etToken)
        btnTest = findViewById(R.id.btnTest)
        btnSave = findViewById(R.id.btnSave)
        btnSyncNow = findViewById(R.id.btnSyncNow)
        tvSyncStatus = findViewById(R.id.tvSyncStatus)
        tvLastSynced = findViewById(R.id.tvLastSynced)

        etOwner.setText(preferencesManager.getGitHubOwner())
        etRepo.setText(preferencesManager.getGitHubRepo())
        etToken.setText(preferencesManager.getGitHubToken())

        updateSyncInfo()

        btnTest.setOnClickListener { testConnection() }
        btnSave.setOnClickListener { saveSettings() }
        btnSyncNow.setOnClickListener {
            SyncScheduler.scheduleImmediateSync(this)
            lifecycleScope.launch {
                syncRepository.performSync()
                updateSyncInfo()
            }
        }

        lifecycleScope.launch {
            syncRepository.syncStatus.collect { status ->
                renderSyncStatus(status)
            }
        }
    }

    private fun testConnection() {
        val owner = etOwner.text.toString().trim()
        val repo = etRepo.text.toString().trim()
        val token = etToken.text.toString().trim()

        if (owner.isEmpty() || repo.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Please fill in all GitHub fields", Toast.LENGTH_SHORT).show()
            return
        }

        btnTest.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val result = gitHubApiClient.validateRepository(owner, repo, token)
            withContext(Dispatchers.Main) {
                btnTest.isEnabled = true
                if (result.isSuccess) {
                    Toast.makeText(this@SettingsActivity, "Connection successful! Private repository validated.", Toast.LENGTH_LONG).show()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Validation failed"
                    Toast.makeText(this@SettingsActivity, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveSettings() {
        val owner = etOwner.text.toString().trim()
        val repo = etRepo.text.toString().trim()
        val token = etToken.text.toString().trim()

        preferencesManager.saveGitHubConfig(owner, repo, token)
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        SyncScheduler.scheduleImmediateSync(this)
        lifecycleScope.launch {
            syncRepository.performSync()
            updateSyncInfo()
        }
    }

    private fun updateSyncInfo() {
        val lastSyncMillis = preferencesManager.getLastSyncTime()
        if (lastSyncMillis > 0) {
            tvLastSynced.text = "Last synced: ${DateUtils.formatDisplayDate(java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(lastSyncMillis)))}"
        } else {
            tvLastSynced.text = "Last synced: Never"
        }
        renderSyncStatus(syncRepository.syncStatus.value)
    }

    private fun renderSyncStatus(status: SyncStatus) {
        tvSyncStatus.text = when (status) {
            SyncStatus.IDLE -> "Idle"
            SyncStatus.SYNCING -> "Syncing..."
            SyncStatus.SYNCED -> "Synced"
            SyncStatus.WAITING_FOR_NETWORK -> "Waiting for network"
            SyncStatus.AUTH_REQUIRED -> "Authentication required"
            SyncStatus.NOT_CONFIGURED -> "GitHub not configured"
            SyncStatus.CONFLICT -> "Sync conflict"
            SyncStatus.ERROR -> "Sync failed, retrying"
        }
    }
}
