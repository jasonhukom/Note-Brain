package com.example.notebrain.data.remote

import com.google.gson.annotations.SerializedName

data class RemoteFileHeader(
    @SerializedName("name")
    val name: String,
    @SerializedName("path")
    val path: String,
    @SerializedName("sha")
    val sha: String,
    @SerializedName("type")
    val type: String
)

data class GitHubFileContentResponse(
    @SerializedName("name")
    val name: String,
    @SerializedName("path")
    val path: String,
    @SerializedName("sha")
    val sha: String,
    @SerializedName("content")
    val content: String?,
    @SerializedName("encoding")
    val encoding: String?
)

data class GitHubRepoResponse(
    @SerializedName("name")
    val name: String,
    @SerializedName("private")
    val isPrivate: Boolean,
    @SerializedName("full_name")
    val fullName: String
)
