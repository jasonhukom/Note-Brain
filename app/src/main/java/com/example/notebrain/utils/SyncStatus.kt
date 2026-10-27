package com.example.notebrain.utils

enum class SyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    WAITING_FOR_NETWORK,
    AUTH_REQUIRED,
    NOT_CONFIGURED,
    CONFLICT,
    ERROR
}
