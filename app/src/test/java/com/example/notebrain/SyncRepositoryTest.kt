package com.example.notebrain

import com.example.notebrain.data.local.Note
import com.example.notebrain.data.local.NoteDao
import com.example.notebrain.data.preferences.PreferencesManager
import com.example.notebrain.data.remote.GitHubApiClient
import com.example.notebrain.data.remote.RemoteFileHeader
import com.example.notebrain.data.remote.RemoteNote
import com.example.notebrain.repository.NoteRepository
import com.example.notebrain.repository.SyncRepository
import com.example.notebrain.utils.DateUtils
import com.example.notebrain.utils.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeNoteDao : NoteDao {
    val notesMap = mutableMapOf<String, Note>()

    override fun getAllActiveNotes(): Flow<List<Note>> = flowOf(notesMap.values.filter { !it.isDeleted })
    override suspend fun getNoteById(id: String): Note? = notesMap[id]
    override fun getNoteByIdFlow(id: String): Flow<Note?> = flowOf(notesMap[id])
    override suspend fun getPendingNotes(): List<Note> = notesMap.values.filter { it.syncState != Note.SYNC_STATE_SYNCED }.toList()
    override suspend fun getAllNotesIncludingDeleted(): List<Note> = notesMap.values.toList()
    override suspend fun insertNote(note: Note) { notesMap[note.id] = note }
    override suspend fun insertNotes(notes: List<Note>) { notes.forEach { notesMap[it.id] = it } }
    override suspend fun updateNote(note: Note) { notesMap[note.id] = note }
    override suspend fun deleteNotePermanently(id: String) { notesMap.remove(id) }
}

class FakePreferencesManager : PreferencesManager(null) {
    override fun isGitHubConfigured(): Boolean = true
    override fun getGitHubOwner(): String = "testowner"
    override fun getGitHubRepo(): String = "testrepo"
    override fun getGitHubToken(): String = "testtoken"
    override fun getDeviceId(): String = "testdevice123"
    override fun getLastSyncTime(): Long = 0L
    override fun saveLastSyncTime(timestamp: Long) {}
}

class FakeGitHubApiClient : GitHubApiClient() {
    var listDirectoryResult: Result<List<RemoteFileHeader>> = Result.success(emptyList())
    var getNoteFileResultMap = mutableMapOf<String, Pair<RemoteNote, String>>()
    var putNoteFileResult: Result<String> = Result.success("default_sha")
    var deleteNoteFileResult: Result<Unit> = Result.success(Unit)

    val putCalls = mutableListOf<RemoteNote>()
    val deletedNoteIds = mutableListOf<String>()

    override fun listNotesDirectory(owner: String, repo: String, token: String): Result<List<RemoteFileHeader>> {
        return listDirectoryResult
    }

    override fun getNoteFile(owner: String, repo: String, token: String, noteId: String): Result<Pair<RemoteNote, String>> {
        val result = getNoteFileResultMap[noteId]
        return if (result != null) Result.success(result) else Result.failure(Exception("Not found"))
    }

    override fun putNoteFile(
        owner: String, repo: String, token: String, noteId: String,
        remoteNote: RemoteNote, sha: String?, commitMessage: String
    ): Result<String> {
        putCalls.add(remoteNote)
        return putNoteFileResult
    }

    override fun deleteNoteFile(
        owner: String, repo: String, token: String, noteId: String,
        sha: String, commitMessage: String
    ): Result<Unit> {
        deletedNoteIds.add(noteId)
        return deleteNoteFileResult
    }
}

class SyncRepositoryTest {

    private lateinit var noteDao: FakeNoteDao
    private lateinit var preferencesManager: FakePreferencesManager
    private lateinit var gitHubApiClient: FakeGitHubApiClient
    private lateinit var noteRepository: NoteRepository
    private lateinit var syncRepository: SyncRepository

    @Before
    fun setUp() {
        noteDao = FakeNoteDao()
        preferencesManager = FakePreferencesManager()
        gitHubApiClient = FakeGitHubApiClient()

        noteRepository = NoteRepository(noteDao, preferencesManager)
        syncRepository = SyncRepository(noteDao, preferencesManager, gitHubApiClient)
    }

    @Test
    fun testOfflineNoteSavedLocallyAndPendingSync() = runBlocking {
        val note = noteRepository.createNoteFromShare("Offline Title", "Offline Content")
        assertNotNull(note.id)
        assertEquals("Offline Title", note.title)
        assertEquals("Offline Content", note.content)
        assertEquals(Note.SYNC_STATE_PENDING, note.syncState)

        val retrieved = noteDao.getNoteById(note.id)
        assertNotNull(retrieved)
        assertEquals("Offline Title", retrieved!!.title)
    }

    @Test
    fun testNewLocalNoteUploadsToGitHub() = runBlocking {
        val now = DateUtils.currentUtcIsoString()
        val pendingNote = Note(
            id = "note-1",
            title = "Upload Test",
            content = "Content",
            createdAt = now,
            updatedAt = now,
            syncState = Note.SYNC_STATE_PENDING
        )
        noteDao.insertNote(pendingNote)

        gitHubApiClient.listDirectoryResult = Result.success(emptyList())
        gitHubApiClient.putNoteFileResult = Result.success("sha12345")

        val syncResult = syncRepository.performSync()
        assertTrue(syncResult.isSuccess)
        assertEquals(SyncStatus.SYNCED, syncResult.getOrNull())

        val updated = noteDao.getNoteById("note-1")
        assertNotNull(updated)
        assertEquals("sha12345", updated!!.lastSyncedSha)
        assertEquals(Note.SYNC_STATE_SYNCED, updated.syncState)
    }

    @Test
    fun testRemoteNoteImportedToRoom() = runBlocking {
        val remoteNote = RemoteNote(
            id = "remote-123",
            title = "Remote Title",
            content = "Remote Content",
            createdAt = DateUtils.currentUtcIsoString(),
            updatedAt = DateUtils.currentUtcIsoString()
        )

        val header = RemoteFileHeader("remote-123.json", ".justnote/notes/remote-123.json", "remotesha1", "file")
        gitHubApiClient.listDirectoryResult = Result.success(listOf(header))
        gitHubApiClient.getNoteFileResultMap["remote-123"] = Pair(remoteNote, "remotesha1")

        val syncResult = syncRepository.performSync()
        assertTrue(syncResult.isSuccess)

        val imported = noteDao.getNoteById("remote-123")
        assertNotNull(imported)
        assertEquals("Remote Title", imported!!.title)
        assertEquals("Remote Content", imported.content)
        assertEquals(Note.SYNC_STATE_SYNCED, imported.syncState)
        assertEquals("remotesha1", imported.lastSyncedSha)
    }

    @Test
    fun testConflictPreservesBothVersions() = runBlocking {
        val now = DateUtils.currentUtcIsoString()
        val localNote = Note(
            id = "conflict-note-1",
            title = "Local Edit",
            content = "Local content",
            createdAt = now,
            updatedAt = now,
            syncState = Note.SYNC_STATE_PENDING,
            lastSyncedSha = "oldsha",
            lastSyncedUpdatedAt = "old-timestamp"
        )
        noteDao.insertNote(localNote)

        val remoteNote = RemoteNote(
            id = "conflict-note-1",
            title = "Remote Edit",
            content = "Remote content modified",
            createdAt = now,
            updatedAt = "new-remote-timestamp"
        )

        val header = RemoteFileHeader("conflict-note-1.json", ".justnote/notes/conflict-note-1.json", "newsha", "file")
        gitHubApiClient.listDirectoryResult = Result.success(listOf(header))
        gitHubApiClient.getNoteFileResultMap["conflict-note-1"] = Pair(remoteNote, "newsha")
        gitHubApiClient.putNoteFileResult = Result.success("pushedsha")

        val syncResult = syncRepository.performSync()
        assertTrue(syncResult.isSuccess)

        val allNotes = noteDao.getAllNotesIncludingDeleted()
        assertEquals(2, allNotes.size)

        val conflictCopy = allNotes.find { it.id != "conflict-note-1" }
        assertNotNull(conflictCopy)
        assertTrue(conflictCopy!!.title.contains("[Conflict]"))
        assertEquals("Remote content modified", conflictCopy.content)
    }

    @Test
    fun testLocalDeletionPropagatesTombstone() = runBlocking {
        val now = DateUtils.currentUtcIsoString()
        val noteToDelete = Note(
            id = "del-note-1",
            title = "To Delete",
            content = "Content",
            createdAt = now,
            updatedAt = now,
            deletedAt = now,
            isDeleted = true,
            syncState = Note.SYNC_STATE_PENDING,
            lastSyncedSha = "sha-to-del"
        )
        noteDao.insertNote(noteToDelete)

        val header = RemoteFileHeader("del-note-1.json", ".justnote/notes/del-note-1.json", "sha-to-del", "file")
        gitHubApiClient.listDirectoryResult = Result.success(listOf(header))
        gitHubApiClient.deleteNoteFileResult = Result.success(Unit)

        val syncResult = syncRepository.performSync()
        assertTrue(syncResult.isSuccess)

        val deletedNoteInDb = noteDao.getNoteById("del-note-1")
        assertEquals(null, deletedNoteInDb)
        assertTrue(gitHubApiClient.deletedNoteIds.contains("del-note-1"))
    }
}
