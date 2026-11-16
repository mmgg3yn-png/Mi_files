# Mi_files  (com.mimax.files)
Native Kotlin Android app: embedded Termux terminal, file manager, Media3 player, archive, APK and ROM tools.
License: GPL-3.0-or-later (terminal-emulator / terminal-view are GPLv3). Full text in `LICENSE`.

## Status (honest)
NOT YET BUILT OR RUN. Code was written without a compiler or Android SDK; expect compile errors to fix.
See docs/STATUS.md for the feature table and what is missing.

## Build
Push to GitHub, run the "Build APK" workflow, download the `Mi_files-debug-apk` artifact.
Local: `gradle :app:assembleDebug` (JDK 17, Android SDK 34, Gradle 8.9).
