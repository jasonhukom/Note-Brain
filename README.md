# JustNote 📝

**JustNote** is a minimalist, local-first Android note-taking application inspired by Google Keep. It features instant one-tap text/URL sharing from any Android app and automatic, background **bidirectional synchronization** with a private GitHub repository.

---

## 🌟 Core Philosophy

- **Minimalist Design**: Clean UI with a signature yellow accent (`#F9AB00`).
- **Plain-Text Notes**: Plain-text notes with optional titles.
- **Local-First Storage**: Instant local saving using Room database. Never waits for network.
- **Durable Remote Storage**: Keeps notes synchronized with ONE configured private GitHub repository under `.justnote/notes/{noteId}.json`.
- **Zero Distractions**: No folders, no tags, no backlinks, no graph views, no unnecessary bloat.

---

## ✨ Features

### 1. Share Any Text Directly to JustNote
- **Sharesheet Integration**: Select **JustNote** from Android's native Sharesheet when sharing text or URLs from Chrome, Twitter, Reddit, or any other app.
- **Instant Save**: Automatically saves shared text into Room without requiring a second "Save" button press.
- **Smart Title Extraction**: Intelligently derives note titles from shared subjects or the first line of text.
- **Undo Action**: Shows a unobtrusive `"Saved to JustNote"` Snackbar with an **Undo** button.
- **Deduplication**: Handled once per delivery to prevent duplicate notes on activity recreation or screen rotation.
- **Offline Support**: Saves locally when offline and automatically enqueues synchronization when connectivity returns.

### 2. Automatic Bidirectional GitHub Sync
- **Two-Way Synchronization**:
  - **Local → GitHub**: Pushes newly created, updated, or deleted notes.
  - **GitHub → Local**: Imports new notes, applies remote updates, and processes remote tombstone deletions.
- **Conflict Handling (No Data Loss)**: If a note is edited both locally and remotely, JustNote preserves both versions by creating a recoverable `[Conflict]` copy.
- **Managed Repository Directory**: Only reads and writes inside `.justnote/notes/` as JSON files. Never touches or overwrites unrelated repository files.
- **Automated Triggers**:
  - Application startup and return to foreground.
  - After every local note mutation (create, edit, delete, share).
  - Network connectivity restoration (via `ConnectivityManager.NetworkCallback`).
  - Persistent background sync via `WorkManager` with network constraints and exponential backoff retry.
  - 15-minute periodic safety-net background sync.
- **Encrypted Credentials**: Stores GitHub tokens securely using Android Keystore-backed `EncryptedSharedPreferences`. Never logs or exposes credentials.

---

## 🛠️ Architecture & Tech Stack

- **Language**: 100% Idiomatic Kotlin
- **UI Framework**: Material Components (Toolbar, Cards, FloatingActionButton, Staggered Grid)
- **Local Database**: Android Jetpack **Room**
- **Async & Reactive Data**: Kotlin **Coroutines** & **StateFlow**
- **Networking**: **OkHttp** & **Gson** for GitHub REST API v3
- **Background Tasks**: Jetpack **WorkManager**
- **Security**: AndroidX **Security Crypto** (`EncryptedSharedPreferences`)

---

## 📱 Installation & Setup Guide

### 1. Download APK
Download [JustNote-v1.0.0.apk](JustNote-v1.0.0.apk) directly or from the [GitHub Releases](https://github.com/jasonhukom/Note-Brain/releases) page and install it on your Android device (Android 10+ / API 29+).

### 2. GitHub Token Configuration
1. Create a private repository on GitHub (e.g., `my-notes`).
2. Go to **GitHub Settings → Developer Settings → Personal Access Tokens → Fine-grained tokens**.
3. Generate a token scoped to your single private repository with **Contents: Read & write** permissions.
4. Open **JustNote** → tap the **Settings** icon in the top right bar.
5. Enter:
   - **Repository Owner** (e.g. `your-username`)
   - **Repository Name** (e.g. `my-notes`)
   - **Personal Access Token**
6. Tap **Test Connection** to verify settings, then tap **Save Settings**.

Your notes will now automatically synchronize across all your devices connected to the repository!

---

## 🧪 Testing & Verification

JustNote includes comprehensive unit tests covering Share intent extraction, Room persistence, GitHub API payload handling, tombstone deletion, and bidirectional conflict resolution:

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug & release APKs
./gradlew assembleDebug assembleRelease
```

---

## 📄 License

This project is licensed under the MIT License.
