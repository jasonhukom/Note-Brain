# Keep Room entities, DAOs, and Database
-keep class com.example.notebrain.data.local.** { *; }
-keep interface com.example.notebrain.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase

# Keep Gson models
-keep class com.example.notebrain.data.remote.** { *; }
-keepclassmembers class com.example.notebrain.data.remote.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep WorkManager Worker
-keep class com.example.notebrain.sync.SyncWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep Security Crypto & Preferences
-keep class com.example.notebrain.data.preferences.** { *; }

# Keep App UI & ViewModels
-keep class com.example.notebrain.ui.** { *; }
