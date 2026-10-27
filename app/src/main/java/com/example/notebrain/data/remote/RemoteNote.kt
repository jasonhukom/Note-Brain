package com.example.notebrain.data.remote

import com.google.gson.annotations.SerializedName

data class RemoteNote(
    @SerializedName("schemaVersion")
    val schemaVersion: Int = 1,
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String = "",
    @SerializedName("content")
    val content: String = "",
    @SerializedName("createdAt")
    val createdAt: String,
    @SerializedName("updatedAt")
    val updatedAt: String,
    @SerializedName("deletedAt")
    val deletedAt: String? = null,
    @SerializedName("modifiedByDeviceId")
    val modifiedByDeviceId: String? = null
)
