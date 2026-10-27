package com.example.notebrain.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

open class PreferencesManager(context: Context? = null) {
    private val prefs: SharedPreferences? = context?.let { createEncryptedSharedPreferences(it) }

    companion object {
        private const val PREFS_NAME = "justnote_secure_prefs"
        private const val KEY_DEVICE_ID = "key_device_id"
        private const val KEY_GITHUB_OWNER = "key_github_owner"
        private const val KEY_GITHUB_REPO = "key_github_repo"
        private const val KEY_GITHUB_TOKEN = "key_github_token"
        private const val KEY_LAST_SYNC_TIME = "key_last_sync_time"
    }

    private fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    open fun getDeviceId(): String {
        var deviceId = prefs?.getString(KEY_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
            prefs?.edit()?.putString(KEY_DEVICE_ID, deviceId)?.apply()
        }
        return deviceId
    }

    open fun getGitHubOwner(): String = prefs?.getString(KEY_GITHUB_OWNER, "") ?: ""

    open fun getGitHubRepo(): String = prefs?.getString(KEY_GITHUB_REPO, "") ?: ""

    open fun getGitHubToken(): String = prefs?.getString(KEY_GITHUB_TOKEN, "") ?: ""

    open fun saveGitHubConfig(owner: String, repo: String, token: String) {
        prefs?.edit()
            ?.putString(KEY_GITHUB_OWNER, owner.trim())
            ?.putString(KEY_GITHUB_REPO, repo.trim())
            ?.putString(KEY_GITHUB_TOKEN, token.trim())
            ?.apply()
    }

    open fun isGitHubConfigured(): Boolean {
        return getGitHubOwner().isNotBlank() &&
                getGitHubRepo().isNotBlank() &&
                getGitHubToken().isNotBlank()
    }

    open fun getLastSyncTime(): Long = prefs?.getLong(KEY_LAST_SYNC_TIME, 0L) ?: 0L

    open fun saveLastSyncTime(timestamp: Long = System.currentTimeMillis()) {
        prefs?.edit()?.putLong(KEY_LAST_SYNC_TIME, timestamp)?.apply()
    }

    open fun clearGitHubConfig() {
        prefs?.edit()
            ?.remove(KEY_GITHUB_OWNER)
            ?.remove(KEY_GITHUB_REPO)
            ?.remove(KEY_GITHUB_TOKEN)
            ?.apply()
    }
}
