package com.example.notebrain

import android.app.Application
import com.example.notebrain.sync.SyncScheduler
import com.example.notebrain.utils.ThemeManager

class JustNoteApplication : Application() {
    override fun onCreate() {
        ThemeManager.initialize(this)
        super.onCreate()
        SyncScheduler.schedulePeriodicSync(this)
    }
}
