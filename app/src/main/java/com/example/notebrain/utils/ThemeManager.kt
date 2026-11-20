package com.example.notebrain.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.notebrain.data.preferences.PreferencesManager

object ThemeManager {

    fun initialize(context: Context) {
        apply(PreferencesManager(context.applicationContext).getThemeMode())
    }

    fun apply(mode: ThemeMode) {
        val nightMode = when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }
}
