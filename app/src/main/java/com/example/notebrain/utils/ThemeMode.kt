package com.example.notebrain.utils

enum class ThemeMode(val preferenceValue: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun from(value: String?): ThemeMode = entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}
