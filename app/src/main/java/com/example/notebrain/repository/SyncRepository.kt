package com.example.notebrain.repository

import android.content.Context
import android.util.Log
import com.example.notebrain.data.local.AppDatabase
import com.example.notebrain.data.local.Note
import com.example.notebrain.data.local.NoteDao
import com.example.notebrain.data.preferences.PreferencesManager
import com.example.notebrain.data.remote.GitHubApiClient
import com.example.notebrain.data.remote.GitHubAuthException
import com.example.notebrain.data.remote.GitHubConflictException
import com.example.notebrain.data.remote.RemoteNote
import com.example.notebrain.utils.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class SyncRepository(
    private val noteDao: NoteDao,
    private val preferencesManager: PreferencesManager,
    private val gitHubApiClient: GitHubApiClient
) {
    constructor(context: Context) : this(
        noteDao = AppDatabase.getInstance(context).noteDao(),
        preferencesManager = PreferencesManager(context),
        gitHubApiClient = GitHubApiClient()
    )

    companion object {
        private const val TAG = "SyncRepository"
        private val syncMutex = Mutex()

        private fun logI(tag: String, msg: String) {
            try { Log.i(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
        }

        private fun logW(tag: String, msg: String) {
            try { Log.w(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
        }

        private fun logE(tag: String, msg: String, tr: Throwable? = null) {
            try { Log.e(tag, msg, tr) } catch (_: Throwable) { println("[$tag] $msg ${tr?.message ?: ""}") }
        }
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    suspend fun performSync(): Result<SyncStatus> {
        return syncMutex.withLock {
            if (!preferencesManager.isGitHubConfigured()) {
                _syncStatus.value = SyncStatus.NOT_CONFIGURED
                return Result.success(SyncStatus.NOT_CONFIGURED)
            }

            _syncStatus.value = SyncStatus.SYNCING

            val owner = preferencesManager.getGitHubOwner()
            val repo = preferencesManager.getGitHubRepo()
            val token = preferencesManager.getGitHubToken()
            val deviceId = preferencesManager.getDeviceId()

            try {
                // 1. Fetch remote notes directory listing
                val listResult = gitHubApiClient.listNotesDirectory(owner, repo, token)
                if (listResult.isFailure) {
                    val ex = listResult.exceptionOrNull()
                    if (ex is GitHubAuthException) {
                        _syncStatus.value = SyncStatus.AUTH_REQUIRED
                        return Result.failure(ex)
                    }
                    _syncStatus.value = SyncStatus.WAITING_FOR_NETWORK
                    return Result.failure(ex ?: Exception("Failed to connect to GitHub"))
                }

                val remoteHeaders = listResult.getOrDefault(emptyList())
                val remoteFileMap = remoteHeaders.associateBy { it.name.removeSuffix(".json") }

                // 2. Fetch all local notes from Room
                val localNotes = noteDao.getAllNotesIncludingDeleted().associateBy { it.id }

                // 3. Process Remote Files (Importing / Updating / Detecting Conflicts)
                for ((remoteNoteId, fileHeader) in remoteFileMap) {
                    val getResult = gitHubApiClient.getNoteFile(owner, repo, token, remoteNoteId)
                    if (getResult.isFailure) {
                        logW(TAG, "Skipping remote note $remoteNoteId due to fetch error: ${getResult.exceptionOrNull()}")
                        continue
                    }

                    val (remoteNote, remoteSha) = getResult.getOrThrow()
                    val localNote = localNotes[remoteNoteId]

                    if (localNote == null) {
                        if (remoteNote.deletedAt != null) {
                            val tombstone = Note(
                                id = remoteNote.id,
                                title = remoteNote.title,
                                content = remoteNote.content,
                                createdAt = remoteNote.createdAt,
                                updatedAt = remoteNote.updatedAt,
                                deletedAt = remoteNote.deletedAt,
                                isDeleted = true,
                                syncState = Note.SYNC_STATE_SYNCED,
                                lastSyncedSha = remoteSha,
                                lastSyncedUpdatedAt = remoteNote.updatedAt,
                                modifiedByDeviceId = remoteNote.modifiedByDeviceId ?: ""
                            )
                            noteDao.insertNote(tombstone)
                        } else {
                            val newNote = Note(
                                id = remoteNote.id,
                                title = remoteNote.title,
                                content = remoteNote.content,
                                createdAt = remoteNote.createdAt,
                                updatedAt = remoteNote.updatedAt,
                                deletedAt = null,
                                isDeleted = false,
                                syncState = Note.SYNC_STATE_SYNCED,
                                lastSyncedSha = remoteSha,
                                lastSyncedUpdatedAt = remoteNote.updatedAt,
                                modifiedByDeviceId = remoteNote.modifiedByDeviceId ?: ""
                            )
                            noteDao.insertNote(newNote)
                        }
                    } else {
                        if (localNote.syncState == Note.SYNC_STATE_SYNCED) {
                            if (remoteNote.deletedAt != null) {
                                val deletedNote = localNote.copy(
                                    isDeleted = true,
                                    deletedAt = remoteNote.deletedAt,
                                    syncState = Note.SYNC_STATE_SYNCED,
                                    lastSyncedSha = remoteSha,
                                    lastSyncedUpdatedAt = remoteNote.updatedAt
                                )
                                noteDao.insertNote(deletedNote)
                            } else if (remoteSha != localNote.lastSyncedSha || remoteNote.updatedAt != localNote.lastSyncedUpdatedAt) {
                                val updatedNote = localNote.copy(
                                    title = remoteNote.title,
                                    content = remoteNote.content,
                                    updatedAt = remoteNote.updatedAt,
                                    syncState = Note.SYNC_STATE_SYNCED,
                                    lastSyncedSha = remoteSha,
                                    lastSyncedUpdatedAt = remoteNote.updatedAt,
                                    modifiedByDeviceId = remoteNote.modifiedByDeviceId ?: localNote.modifiedByDeviceId
                                )
                                noteDao.insertNote(updatedNote)
                            }
                        } else {
                            val isRemoteModified = remoteSha != localNote.lastSyncedSha && remoteNote.updatedAt != localNote.lastSyncedUpdatedAt
                            if (isRemoteModified) {
                                logI(TAG, "Conflict detected for note ${localNote.id}")
                                if (remoteNote.deletedAt == null && remoteNote.content.isNotBlank()) {
                                    val conflictTitle = if (remoteNote.title.isNotBlank()) "[Conflict] ${remoteNote.title}" else "[Conflict] Remote Version"
                                    val conflictNote = Note(
                                        id = UUID.randomUUID().toString(),
                                        title = conflictTitle,
                                        content = remoteNote.content,
                                        createdAt = remoteNote.createdAt,
                                        updatedAt = remoteNote.updatedAt,
                                        isDeleted = false,
                                        syncState = Note.SYNC_STATE_SYNCED,
                                        lastSyncedSha = remoteSha,
                                        lastSyncedUpdatedAt = remoteNote.updatedAt,
                                        modifiedByDeviceId = remoteNote.modifiedByDeviceId ?: ""
                                    )
                                    noteDao.insertNote(conflictNote)
                                }
                                val updatedLocal = localNote.copy(
                                    lastSyncedSha = remoteSha,
                                    lastSyncedUpdatedAt = remoteNote.updatedAt
                                )
                                noteDao.insertNote(updatedLocal)
                            }
                        }
                    }
                }

                // 4. Process Local Pending Notes
                val pendingNotes = noteDao.getPendingNotes()
                for (pendingNote in pendingNotes) {
                    val fileHeader = remoteFileMap[pendingNote.id]
                    if (pendingNote.isDeleted) {
                        if (fileHeader != null) {
                            val delResult = gitHubApiClient.deleteNoteFile(
                                owner, repo, token, pendingNote.id, fileHeader.sha, "Delete note ${pendingNote.id}"
                            )
                            if (delResult.isSuccess) {
                                noteDao.deleteNotePermanently(pendingNote.id)
                            }
                        } else {
                            noteDao.deleteNotePermanently(pendingNote.id)
                        }
                    } else {
                        val remoteNoteToPush = RemoteNote(
                            schemaVersion = 1,
                            id = pendingNote.id,
                            title = pendingNote.title,
                            content = pendingNote.content,
                            createdAt = pendingNote.createdAt,
                            updatedAt = pendingNote.updatedAt,
                            deletedAt = pendingNote.deletedAt,
                            modifiedByDeviceId = deviceId
                        )

                        val shaToUse = fileHeader?.sha ?: pendingNote.lastSyncedSha
                        val putResult = gitHubApiClient.putNoteFile(
                            owner, repo, token, pendingNote.id, remoteNoteToPush, shaToUse, "Sync note ${pendingNote.id}"
                        )

                        if (putResult.isSuccess) {
                            val newSha = putResult.getOrThrow()
                            val syncedNote = pendingNote.copy(
                                syncState = Note.SYNC_STATE_SYNCED,
                                lastSyncedSha = newSha,
                                lastSyncedUpdatedAt = pendingNote.updatedAt
                            )
                            noteDao.insertNote(syncedNote)
                        } else {
                            val ex = putResult.exceptionOrNull()
                            if (ex is GitHubConflictException) {
                                logW(TAG, "Conflict response on PUT for note ${pendingNote.id}")
                            } else if (ex is GitHubAuthException) {
                                _syncStatus.value = SyncStatus.AUTH_REQUIRED
                                return Result.failure(ex)
                            }
                        }
                    }
                }

                preferencesManager.saveLastSyncTime()
                val remainingPending = noteDao.getPendingNotes()
                if (remainingPending.isEmpty()) {
                    _syncStatus.value = SyncStatus.SYNCED
                    Result.success(SyncStatus.SYNCED)
                } else {
                    _syncStatus.value = SyncStatus.ERROR
                    Result.success(SyncStatus.ERROR)
                }
            } catch (e: Exception) {
                logE(TAG, "Sync exception: ${e.message}", e)
                _syncStatus.value = SyncStatus.ERROR
                Result.failure(e)
            }
        }
    }
}
