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

## Status when the session paused (2026-10-02, usage limit)

- Android screen rewrites were running in three git worktrees (Ledger, Calendar, Notes + Diary + Confessional).
  Each was told to commit its work in progress on its own branch; list them with `git worktree list` and `git branch`.
  They are not merged and may not compile yet. Finish each against `docs/ANDROID_REDESIGN_BRIEF.md`, then merge.
- Android Todo screen: old code read, design decided, nothing written yet. Plan: split `ui/todo/TodoScreen.kt` into
  `TodoUiOperationsViewModel.kt` (unchanged logic), a thin `TodoScreen`, a stateless `TodoContent` (momentum line,
  quick add, view pills All/Today/Next 7 days/Overdue/No date, search and a filter sheet, rows grouped by deadline,
  swipe to delete with Undo, completed section, FAB "New task"), and an editor form used in `EditorSheet` on phones
  and inline as the right pane when `isWide`. Update the two instrumented tests that look for "New task" and "Description *".
- Not started: Vault, Settings + Backup & sync (with the daily backups list) + app lock screen.
- `ScreenRenderTest.tabletLandscape` (1280x800) was added: it is the only render device where `isWide` is true.
- After that: review passes on every device, instrumented tests on a headless emulator, then step 8 (version 2.0.0, builds).

## Status 2026-10-02 (later)

- All Android screens are rewritten and merged: Today, Todo, Ledger, Calendar, Notes, Diary, Confessional, Vault,
  Settings, Backup & sync, app lock.
- Foldables: a flexible screen that is half folded uses the whole window (tablet layout). Only a hinge that hides
  part of the window (two screens) splits the app into a navigation pane and a content pane.
- Two-pane layouts start at 700dp of content width.
- `ScreenRenderTest` draws dialog windows too; devices: phone, smallPhone, landscapePhone, tablet, tabletLandscape,
  dualScreen, foldBook, foldTabletop, largeFont.
- Version is 2.0.0 (versionCode 18). Release notes: `docs/releases/RELEASE_2.0.0.md`.
- Left: instrumented tests on the emulator, release builds, the user's own test, then publishing.

## Status 2026-10-02 (builds ready)

- Instrumented tests on the emulator: 132 of 133 pass; `RebrandUpgradeSnapshotInstrumentedTest` needs a device
  that already holds user data and does not apply to a clean emulator.
- Fixed while testing on the emulator: daily backup failed when the Vault had entries; an open editor was lost
  when the screen was rotated or folded after switching pages; quick add lost the keyboard after each todo.
- `build/release-2.0.0/` holds the signed APK and the Windows installer; the packaged self-test passes, including
  the bridge for AI assistants.
- Left: the user's own test, then push, pull request, tag v2.0.0 and the release (the Linux packages are built by
  GitHub Actions).
