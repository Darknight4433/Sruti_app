# 🎵 Sruti — Premium Android Music Experience

[![Android](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3](https://img.shields.io/badge/Playback-Media3_ExoPlayer-FF5722?style=for-the-badge)](https://developer.android.com/media/media3)
[![License](https://img.shields.io/badge/License-MIT-A855F7?style=for-the-badge)](LICENSE)

**Sruti** is a modern, high-performance native Android music streaming and local audio player. Crafted with a glossy dark glassmorphism design system in **Radiant Neon Purple (`#A855F7`)**, Sruti combines local playback with online search, universal playlist importing, and Google Account authentication.

---

## ✨ Key Features

### 🎨 Glossy Dark Design System
- **Curated Neon Purple Theme**: Unified accent system (`#A855F7`) across controls, progress bars, active tabs, and navigation indicators.
- **Glassmorphic Cards**: Multi-layered dark glass panels with subtle luminous borders and blurred dynamic artwork backdrops.
- **Micro-Animations & Visuals**: Spinning vinyl record animations, rotating disc mini-player, and interactive bottom navigation sheets.

### 🔐 Google Account & Profile Sync
- **Universal Google Sign-In**: 1-tap Google account authentication using native Android Play Services and OAuth fallback.
- **Authentic Google Profile Picture**: Loads and renders the user's authentic Google account avatar (`photoUrl`) and display name across Home, Library, and Profile dialogs.
- **Offline & Guest Mode**: Fully functional offline without requiring an account; supports guest sessions and custom profile photos from device storage.

### 📥 Universal Playlist Importing & Management
- **Spotify Link Importer**: Paste any public Spotify playlist URL to parse track metadata and match with audio streams.
- **Playlist File Import**: Import `.m3u` and `.m3u8` playlist files directly from device storage.
- **1-Tap Device Audio Import**: Automatically compile all local device audio files into a newly created or existing playlist in one tap.
- **Multi-Select Song Picker**: Search, filter, and batch-select songs with a convenient "Select All" toggle inside `PlaylistDetailScreen`.

### 💖 Real-Time Liked Songs & Favorites
- **1-Tap Favoriting**: Favorite any track from Now Playing, Search, or Playlist detail screens with instant database persistence and visual feedback.
- **Home Quick-Access Card**: Dedicated Liked Songs quick-play banner on the Home screen showing live track counts and instant playback.
- **Library Shortcut**: Quick filter chip in the Library tab for instant access to loved tracks.

### 🎧 Media3 Audio Engine
- **Background Playback**: Continuous background playback backed by AndroidX `MediaSessionService`.
- **System Notification**: Lock screen and notification shade controls with artwork, seek bar, and playback actions.
- **Audio Output Selector**: Native system dialog to seamlessly switch between phone speaker, Bluetooth headphones, and Cast devices.

---

## 📱 Screenshots

| Home & Liked Songs | Account & Google Auth | Playlist Importer |
|:---:|:---:|:---:|
| Radiant theme & quick access | Authentic profile avatar | Spotify & Device audio import |

---

## 🛠 Tech Stack

- **Language**: Kotlin 2.0+
- **UI Toolkit**: Jetpack Compose & Material 3
- **Media Engine**: AndroidX Media3 ExoPlayer & MediaSession
- **Local Persistence**: Room Database (`SrutiDatabase` v5) & SharedPreferences
- **Networking**: OkHttp 4
- **Image Loading**: Coil Compose 2.7
- **Authentication**: Firebase Authentication & Google Play Services Auth
- **Palette Extraction**: AndroidX Palette for adaptive background colors

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- Android SDK 35 (compileSdk 35, minSdk 26)
- JDK 21

### Building from Source

```bash
# Clone the repository
git clone https://github.com/Darknight4433/Sruti_app.git
cd Sruti_app

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

The compiled APKs will be located in:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

---

## 📦 Releases

Pre-compiled APKs are available on the [GitHub Releases](https://github.com/Darknight4433/Sruti_app/releases) page for direct installation on any Android 8.0+ device.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
