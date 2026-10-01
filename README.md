# Life Assistant

Life Assistant is an open-source, offline-first personal organizer for Android, Windows and Linux. It combines Todo, Ledger, Calendar, and Notes, with encrypted backup and optional one-click GitHub synchronization. The Android interface uses device-independent sizing for phones, tablets and foldables, including narrow clamshell layouts such as the Samsung Galaxy Z Flip series. The Windows and Linux apps are native desktop applications with their own bundled Java runtime; no browser or separate Java installation is required.

By **ced2711** · 中文名：**生活助手** · [Source](https://github.com/ced2711/life-assistant)

## Download · 下载

| Platform · 平台 | Download · 下载 | Notes · 说明 |
| --- | --- | --- |
| **Android** 8.0+ | [LifeAssistant-android.apk](https://github.com/ced2711/life-assistant/releases/latest/download/LifeAssistant-android.apk) | Open the file on your phone and allow installing from this source. · 在手机上打开，允许安装即可。 |
| **Windows** 10/11 | [LifeAssistant-windows-setup.exe](https://github.com/ced2711/life-assistant/releases/latest/download/LifeAssistant-windows-setup.exe) | Run the installer; no admin rights needed. · 双击安装，不需要管理员权限。 |
| **Linux** (Debian/Ubuntu) | [LifeAssistant-linux-amd64.deb](https://github.com/ced2711/life-assistant/releases/latest/download/LifeAssistant-linux-amd64.deb) | `sudo apt install ./LifeAssistant-linux-amd64.deb` |
| **Linux** (other) | [LifeAssistant-linux-x64.tar.gz](https://github.com/ced2711/life-assistant/releases/latest/download/LifeAssistant-linux-x64.tar.gz) | Unpack and run `bin/life-assistant`. · 解压后运行 `bin/life-assistant`。 |

All versions and release notes: [Releases](https://github.com/ced2711/life-assistant/releases). Install the same version on every device before syncing.
所有版本和更新说明见 [Releases](https://github.com/ced2711/life-assistant/releases)。同步前请把每台设备都更新到同一个版本。

## Highlights

- Nested Todo categories, subtasks, priorities, tags, search, reminders, attachments, recurring tasks, and completion momentum.
- Manual Income/Expense ledger entries, recurring entries, attachments, summaries, and close-fit trend charts.
- Month, week, day, and agenda calendar views with daily net amounts and completed/incomplete Todo counts.
- Long-term Notes with collapsible search and nested-folder controls, pinned notes, and multiple private file/image attachments.
- Diary with one page per day, saved automatically; days with a page show a small green dot in the calendar.
- Confessional: write something down, then burn it for good or seal it on this device only. Sealed words are encrypted with a device key (Android Keystore or Windows DPAPI) and are never backed up or synced; screenshots are blocked on Android.
- Optional app lock, off by default: Android asks for fingerprint, face or screen lock, Windows for the data password, after the app has been in the background for a chosen time.
- Choose which modules appear in the navigation menu. Diary and Confessional start hidden; turn them on in Settings. Hidden modules keep their data.
- Encrypted local Vault for credentials and private notes, protected by Android system authentication.
- Password-encrypted `.tlb` backup and full-replacement restore with validation and preview.
- Responsive Today Todo widget, including wide horizontal layouts.
- Six selectable accent palettes with light, dark, and system themes.
- Fullscreen Android status-bar policy, camera-cutout and keyboard avoidance, and tabletop-pane handling without brand-specific device lists.
- Optional encrypted snapshot sync through Google Drive or a private GitHub repository, with explicit conflict resolution; the 10 newest cloud versions are kept.
- Offline use without accounts; no analytics or advertising. Google authorization is required only when enabling Drive sync.

## Desktop (Windows and Linux) and cloud sync

The desktop app has the phone's features in a layout made for large screens: a labelled sidebar that folds to icons (Ctrl+B); Todo with a filter column, date groups and an editor beside the list (subtasks, due time, reminders, repeat, Urgent, attachments, Undo); Ledger with week/month/year views, statistics, a chart, repeating entries and a Recurring tab; a Calendar whose month shows each day's todos with the day's details beside it, plus week and agenda views; Notes with folders, list and an autosaving editor side by side; Diary, Confessional and a Vault with copy buttons. Reminders appear as system notifications, optionally from the tray. Everything works from the keyboard: Ctrl+N new, Ctrl+F search, Ctrl+1–9 modules, Ctrl+R sync, Ctrl+B fold the menu, Ctrl+S save, Esc close, arrows in lists and the calendar. It uses the same backup codec, encryption, merge rules, recurrence engine, themes and English/Simplified Chinese text as Android. Home-screen widgets remain Android features. New desktop installations are offline by default; connect cloud sync explicitly in Settings.

**Cloud sync is one click with GitHub.** Choose **Connect GitHub**, paste the code that is already copied, and approve; Life Assistant creates a private `life-assistant-data` repository in your account and starts syncing. See [GitHub sync setup](docs/GITHUB_SYNC_SETUP.md). Google Drive sync is shown as coming soon until its Google Cloud project is published; the developer steps are in [Google Drive setup](docs/GOOGLE_DRIVE_SETUP.md) and the Chinese [one-time cloud setup](docs/CLOUD_SETUP_ZH.md). The GitHub sign-in Client ID (`lifeassistant.github.clientId`, public) is set in `gradle.properties`; the Google desktop client (`lifeassistant.google.desktopClientId`, `lifeassistant.google.desktopClientSecret`) comes only from the developer's personal Gradle properties and is never committed.

**Phone and computer can sync at the same time.** When both changed since the last sync, Life Assistant merges them record by record instead of asking: a change made on one device is kept, a deletion applies unless the other device edited that item, and when both edited the same field the newer edit wins. If both edited the same note, diary page or description, both versions are kept, separated by a marker, so nothing is lost. Items both devices created with the same internal number are renumbered automatically. Each device keeps an encrypted copy of the last synced version to tell edits from deletions. Only a cloud version encrypted with a different password still asks which version to keep. Each upload creates a new encrypted version and the 10 most recent are kept. Android Vault authentication is still required for snapshots containing Vault entries, so unattended sync waits for unlock when necessary. Desktop sync runs while the app is open and unlocked.

## Identity and compatibility

- App label: `Life Assistant`
- Package/application ID: `com.ced2711.lifetracker`
- Minimum Android version: Android 8.0 / API 26
- Target SDK: API 36; compile SDK: API 37
- Local database: Room schema 7, with migrations from schemas 1 through 7
- Backup snapshot format 5 adds diary pages. Older app versions cannot open backups or Drive revisions written in this format, so update Android and Windows together before syncing. Older backups remain readable.

Life Assistant 1.7.0 is a display-name change from Life Tracker, not a new Android application. Install it over Life Tracker without uninstalling: the application ID, release certificate, launcher aliases, database, settings, Vault keys, and sync protocol remain unchanged. Windows retains the installer upgrade UUID and the `%APPDATA%/Life Tracker` data directory so existing encrypted data and remembered credentials remain available. The old name in internal paths is intentional compatibility, not unfinished branding.

Only the much older private `com.taskledger.app` (TaskLedger) package requires migration through an encrypted `.tlb` export and restore. Existing `.tlb` files and Google Drive revisions remain compatible; only the suggested filename of new manual exports changes to `LifeAssistant-backup...tlb`.

## Project layout

```
android/            Android app (Jetpack Compose)
desktop/            Desktop app shared by Windows and Linux (Compose Desktop)
  windows/          Windows-only code and packaging notes
  linux/            Linux-only code and packaging notes
shared/cloudsync/   Encrypted cloud sync used by every app
docs/               Setup guides, licensing notes, release notes (docs/releases/)
.github/workflows/  Linux build (packages can only be built on Linux)
```

## Build

Use JDK 17 and the included Gradle wrapper.

```powershell
.\gradlew.bat :android:testStandardDebugUnitTest :android:lintStandardRelease :android:assembleStandardRelease
.\gradlew.bat :cloudsync:test :desktop:test :desktop:packageReleaseExe :desktop:packageReleaseMsi
```

There are two Android distribution flavors:

- `standard`: normal Android install/update build.
- `personal`: one-off migration build; version code 5 so the standard APK can update it afterward.

The personal flavor intentionally requires a local `android/src/personal/assets/personal-backup.tlb`. That encrypted user backup, its password, APK outputs, release keystore, and signing credentials are excluded from Git and must never be committed, even to a private repository.

Windows installers are written to `desktop/build/compose/binaries/main-release/exe` and `msi` (ProGuard-shrunk). Use `:desktop:createReleaseDistributable` for a portable app folder. Building Windows installers requires Windows; Gradle obtains the WiX packaging tools as needed. Linux packages (`.deb` and a portable `.tar.gz`) are built on Linux by the `Linux build` GitHub Actions workflow, which also attaches them to published releases.

Release Android signing is configured locally, never through committed credentials. `android/tools/setup-release-signing.ps1` creates a release key in a `life-assistant-signing` folder next to the repository folder and stores its settings in your personal Gradle properties (`$GRADLE_USER_HOME/gradle.properties`, default `~/.gradle/gradle.properties`).

## Verification

The project includes JVM, Android instrumentation, sync transport/branch-conflict, Windows encrypted-store/attachment, and Windows DPAPI tests. See the [release notes](docs/releases/): [1.9.0](docs/releases/RELEASE_1.9.0.md), [1.8.0](docs/releases/RELEASE_1.8.0.md), [1.7.1 fullscreen and compatibility report](docs/releases/RELEASE_1.7.1.md), [1.7.0](docs/releases/RELEASE_1.7.0.md) and the historical [1.6.0 verification report](docs/releases/RELEASE_1.6.0.md). The cloud tests use a local HTTP fixture; a real OAuth end-to-end check additionally requires the developer's configured Google Cloud project and interactive consent. Unit tests alone do not establish that live Google authorization is configured.

Android support remains **Android 8.0 (API 26) and newer**, for phone/tablet app environments. There is no Samsung-only restriction. Tests on Android 8 and Android 16 emulators and resized/folded layouts do not prove compatibility with every OEM, keyboard, or future OS release. Camera hardware cannot be removed; system-owned authentication, permission or external-app screens control their own bars. Flip cover-screen launch access is controlled by Samsung/One UI (and may require its supported launcher/settings); adapting the app's small-window layout does not bypass those restrictions.

## Privacy

Application data stays on the device unless the user exports it or enables Google Drive sync. Optional sync sends encrypted `.tlb` snapshots to Google's app-specific Drive storage; revision metadata, file sizes and access times are visible to Google. The scope cannot read unrelated Drive files. Android automatic system backup is disabled. Android Vault keys remain device-bound and are never exported; unlocked Vault values are carried only inside the password-encrypted portable backup. Windows keeps its encrypted data under `%APPDATA%/Life Tracker` and uses DPAPI for remembered credentials. Working attachment files are extracted locally for use by the Windows app. See [Security policy](SECURITY.md).

## License

Copyright 2026 ced2711.

Starting with 1.7.0, first-party Life Assistant code is licensed under **GNU Affero General Public License, version 3 only (AGPL-3.0-only)**, with a narrow [Google Play services linking permission](ADDITIONAL_PERMISSIONS.md). Read the full [LICENSE](LICENSE) and [licensing notes](docs/LICENSING.md).

You may use the app, including commercially. Distributing covered binaries requires making their Corresponding Source available under the applicable license terms. If you modify a covered program and let people interact with it remotely over a network, AGPL section 13 requires offering those users its Corresponding Source. Ordinary use does not require publishing personal notes, financial records, files, credentials, or unrelated software. Purely private local modifications do not by themselves require publication.

Earlier Apache-2.0 releases retain their original licensing; those grants are not retroactively revoked. Third-party components retain their own terms and notices. This software comes without warranty.

Source for each binary release is identified by its matching version tag, for example [`v1.7.1`](https://github.com/ced2711/life-assistant/tree/v1.7.1). Both apps provide an offline license viewer and a source-code link in Settings. Build instructions above apply to the corresponding source; supply your own signing key for a fork rather than requesting the author's private release key.
