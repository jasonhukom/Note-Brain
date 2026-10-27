package com.example.notebrain.data.remote

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class GitHubAuthException(message: String) : Exception(message)
class GitHubConflictException(message: String) : Exception(message)

open class GitHubApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    open fun validateRepository(owner: String, repo: String, token: String): Result<Boolean> {
        val url = "https://api.github.com/repos/$owner/$repo"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "JustNote-Android")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string() ?: return Result.failure(Exception("Empty response body"))
                        val repoResponse = gson.fromJson(body, GitHubRepoResponse::class.java)
                        if (!repoResponse.isPrivate) {
                            Result.failure(Exception("Repository is not private. JustNote requires a private repository."))
                        } else {
                            Result.success(true)
                        }
                    }
                    401, 403 -> Result.failure(GitHubAuthException("Authentication failed. Please check your GitHub token and permissions."))
                    404 -> Result.failure(Exception("Repository '$owner/$repo' not found or token has no access."))
                    else -> Result.failure(Exception("GitHub API error: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open fun listNotesDirectory(owner: String, repo: String, token: String): Result<List<RemoteFileHeader>> {
        val url = "https://api.github.com/repos/$owner/$repo/contents/.justnote/notes"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "JustNote-Android")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string() ?: return Result.success(emptyList())
                        val listType = object : TypeToken<List<RemoteFileHeader>>() {}.type
                        val headers: List<RemoteFileHeader> = gson.fromJson(body, listType)
                        val jsonFiles = headers.filter { it.type == "file" && it.name.endsWith(".json") }
                        Result.success(jsonFiles)
                    }
                    404 -> Result.success(emptyList())
                    401, 403 -> Result.failure(GitHubAuthException("Authentication failed"))
                    else -> Result.failure(Exception("Failed to list notes: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open fun getNoteFile(owner: String, repo: String, token: String, noteId: String): Result<Pair<RemoteNote, String>> {
        val url = "https://api.github.com/repos/$owner/$repo/contents/.justnote/notes/$noteId.json"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "JustNote-Android")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string() ?: return Result.failure(Exception("Empty body"))
                        val contentResponse = gson.fromJson(body, GitHubFileContentResponse::class.java)
                        val rawContent = contentResponse.content ?: ""
                        val cleanBase64 = rawContent.replace("\n", "").replace("\r", "")
                        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                        val jsonString = String(decodedBytes, StandardCharsets.UTF_8)
                        val remoteNote = gson.fromJson(jsonString, RemoteNote::class.java)
                        Result.success(Pair(remoteNote, contentResponse.sha))
                    }
                    404 -> Result.failure(Exception("Note file not found"))
                    401, 403 -> Result.failure(GitHubAuthException("Authentication failed"))
                    else -> Result.failure(Exception("Failed to fetch note: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open fun putNoteFile(
        owner: String,
        repo: String,
        token: String,
        noteId: String,
        remoteNote: RemoteNote,
        sha: String?,
        commitMessage: String
    ): Result<String> {
        val url = "https://api.github.com/repos/$owner/$repo/contents/.justnote/notes/$noteId.json"
        val jsonString = gson.toJson(remoteNote)
        val base64Content = Base64.encodeToString(jsonString.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

        val bodyJsonObject = JsonObject().apply {
            addProperty("message", commitMessage)
            addProperty("content", base64Content)
            if (!sha.isNullOrEmpty()) {
                addProperty("sha", sha)
            }
        }

        val requestBody = bodyJsonObject.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "JustNote-Android")
            .put(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 201 -> {
                        val responseBody = response.body?.string() ?: ""
                        val json = gson.fromJson(responseBody, JsonObject::class.java)
                        val contentObj = json.getAsJsonObject("content")
                        val newSha = contentObj.get("sha").asString
                        Result.success(newSha)
                    }
                    409 -> Result.failure(GitHubConflictException("Conflict updating remote file for note $noteId"))
                    401, 403 -> Result.failure(GitHubAuthException("Authentication failed"))
                    else -> Result.failure(Exception("Failed to put note file: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open fun deleteNoteFile(
        owner: String,
        repo: String,
        token: String,
        noteId: String,
        sha: String,
        commitMessage: String
    ): Result<Unit> {
        val url = "https://api.github.com/repos/$owner/$repo/contents/.justnote/notes/$noteId.json"
        val bodyJsonObject = JsonObject().apply {
            addProperty("message", commitMessage)
            addProperty("sha", sha)
        }

        val requestBody = bodyJsonObject.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "JustNote-Android")
            .delete(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 204 -> Result.success(Unit)
                    404 -> Result.success(Unit)
                    401, 403 -> Result.failure(GitHubAuthException("Authentication failed"))
                    else -> Result.failure(Exception("Failed to delete remote file: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
