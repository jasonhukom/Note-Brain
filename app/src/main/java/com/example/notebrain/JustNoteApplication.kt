package com.example.notebrain

import android.app.Application
import com.example.notebrain.sync.SyncScheduler

class JustNoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedulePeriodicSync(this)
    }
}
