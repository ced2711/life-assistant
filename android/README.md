# Android app

Jetpack Compose app for phones, tablets and foldables (Android 8.0 / API 26 and newer).

- `src/main` — the app; `src/personal` — the one-off migration flavor (see the root README).
- `schemas/` — Room database schemas used for migrations.
- `tools/setup-release-signing.ps1` — creates the release signing key outside the repository.

Build: `.\gradlew.bat :android:testStandardDebugUnitTest :android:assembleStandardRelease`
(output in `android/build/outputs/apk/standard/release/`).
