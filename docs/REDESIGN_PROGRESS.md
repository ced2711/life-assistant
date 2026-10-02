# 2.0 redesign: progress and how to continue

Branch `feat/2.0-redesign`. Nothing is published until the user has tried the builds and says so.

## Goals (from the user)

1. GitHub sync must not drop overnight. **Done** (commit 77f3edf); both devices reconnect once after installing.
2. Both apps work alone and offline; the desktop opens without asking for a password. **Done** (`DesktopLocalKey`).
3. Daily backup of the previous day, removed on its third day. **Done** on both (desktop UI in Settings → Sync & backup; Android UI still to add to Backup & sync).
4. AI agents on the PC can read and change data. **Done**: `Life Assistant.exe --mcp` (MCP), Settings → AI assistants.
5. Complete UI rewrite of both apps; simple, clear, all features kept (`docs/DESIGN.md`, feature list below).
6. Self-review until it is as good as it can be: render every screen (both languages, light and dark, narrow and wide, folded sidebar, foldables), fix, repeat.

## State

- Desktop: every page rewritten on the new design (Today, Todo, Ledger, Calendar, Notes, Diary, Confessional, Vault, Settings). Remaining: review passes at several window sizes, light theme, Chinese.
- Android: only a first Today screen. Remaining: shell (bottom navigation, top bar), Todo, Ledger, Calendar, Notes, Diary, Confessional, Vault, Settings, Backup & sync (with daily backups), app lock screen, widget colours.
- Then: full tests (`:cloudsync:test :desktop:test :android:testStandardDebugUnitTest :android:lintStandardRelease`), instrumented tests on the headless emulator, version 2.0.0, release notes, README, packaged self-test, installers into `build/release-2.0.0/`.

## How to review screens without touching the user's screen

- Desktop: `./gradlew :desktop:prepareSmokeData -PsmokeDataDir=<dir>` once, then
  `./gradlew :desktop:renderScreens -PsmokeDataDir=<dir> -PscreensDir=<out> -PscreenWidth=1440 -PscreenHeight=900 -PscreenLanguage=zh -PscreenTheme=light`.
- Android: `./gradlew :android:testStandardDebugUnitTest --tests '*ScreenRenderTest*' -Dscreens.dir=<out>` (Robolectric, no emulator).

## Rules

- Never drop a feature: check against the inventory kept by the assistant (Android and desktop feature lists) before replacing a screen.
- Every new English UI string needs a Chinese translation in `ui/localization/UiLocalization.kt`; `DesktopTranslationCoverageTest` enforces it for desktop.
- No Claude attribution in commits; commit as the user.
