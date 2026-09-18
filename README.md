# Life Tracker

Life Tracker is an open-source, offline-first personal organizer for Android and Windows. It combines Todo, Ledger, Calendar, and Notes, with encrypted backup and optional Google Drive synchronization. The Android interface adapts to phones, tablets and foldables such as the Samsung Galaxy Z Fold series. The Windows app is a native desktop application with its own bundled Java runtime; no browser or separate Java installation is required.

## Highlights

- Nested Todo categories, subtasks, priorities, tags, search, reminders, attachments, recurring tasks, and completion momentum.
- Manual Income/Expense ledger entries, recurring entries, attachments, summaries, and close-fit trend charts.
- Month, week, day, and agenda calendar views with daily net amounts and completed/incomplete Todo counts.
- Long-term Notes with collapsible search and nested-folder controls, pinned notes, and multiple private file/image attachments.
- Encrypted local Vault for credentials and private notes, protected by Android system authentication.
- Password-encrypted `.tlb` backup and full-replacement restore with validation and preview.
- Responsive Today Todo widget, including wide horizontal layouts.
- Six selectable accent palettes with light, dark, and system themes.
- Optional Google Drive authorization and automatic encrypted snapshot sync, with explicit conflict resolution and recent cloud history.
- Offline use without accounts; no analytics or advertising. Google authorization is required only when enabling Drive sync.

## Windows and Google Drive

The Windows app supports local Todo, Ledger, Calendar, Notes, folders, attachments, Vault, and manual `.tlb` import/export. It uses the same backup codec and encryption as Android. The initial Windows release has English UI; Android retains English and Chinese. Advanced Android recurrence creation, reminder scheduling and widgets remain Android features; imported recurrence metadata is preserved when editing existing occurrences on Windows.

**Google Cloud setup is required before live sign-in works.** This repository does not bundle production OAuth credentials. Register the Android package/signing certificate and a Desktop OAuth client in the same Google Cloud project, then authorize the same Google account on both devices. Follow [Google Drive setup](docs/GOOGLE_DRIVE_SETUP.md). Until configured, local use and encrypted file transfer work independently.

Sync transfers complete encrypted datasets. If both sides changed, choose which version to use; it is not a record-by-record merge. Competing cloud branches are preserved until explicitly resolved. Password mismatches are rejected rather than treated as content conflicts. Android Vault authentication is still required for complete snapshots containing Vault entries, so unattended sync waits for unlock when necessary. Windows sync runs while the app is open and unlocked.

## Identity and compatibility

- App label: `Life Tracker`
- Package/application ID: `com.ced2711.lifetracker`
- Minimum Android version: Android 8.0 / API 26
- Target and compile SDK: API 36
- Local database: Room schema 6, with migrations from schemas 1 through 6

The package changed from the earlier private `com.taskledger.app` build. Android therefore treats Life Tracker as a new app; encrypted `.tlb` restore is the supported data migration bridge. Future Life Tracker updates preserve data when they retain the current package ID, release certificate, and compatible Room migrations.

## Build

Use JDK 17 and the included Gradle wrapper.

```powershell
.\gradlew.bat testStandardDebugUnitTest lintStandardRelease assembleStandardRelease
.\gradlew.bat :cloudsync:test :desktopApp:test :desktopApp:packageExe :desktopApp:packageMsi
```

There are two distribution flavors:

- `standard`: normal Android install/update build; version code 12 for the 1.6.0 release.
- `personal`: one-off migration build; version code 5 so the standard APK can update it afterward.

The personal flavor intentionally requires a local `app/src/personal/assets/personal-backup.tlb`. That encrypted user backup, its password, APK outputs, release keystore, and signing credentials are excluded from Git and must never be committed, even to a private repository.

Windows distributions are written to `desktopApp/build/compose/binaries/main/exe` and `msi`. Use `:desktopApp:createDistributable` to create a portable app folder. Building installers requires Windows and JDK 17; Gradle obtains the WiX packaging tools as needed. Release Android signing is configured locally through the existing signing environment variables, never through committed credentials.

## Verification

The project includes JVM, Android instrumentation, sync transport/branch-conflict, Windows encrypted-store/attachment, and Windows DPAPI tests. See the [1.6.0 verification report](docs/RELEASE_1.6.0.md) for completed checks and remaining limitations. The cloud tests use a local HTTP fixture; a real OAuth end-to-end check additionally requires the developer's configured Google Cloud project and interactive consent. Unit tests alone do not establish that live Google authorization is configured.

## Privacy

Application data stays on the device unless the user exports it or enables Google Drive sync. Optional sync sends encrypted `.tlb` snapshots to Google's app-specific Drive storage; revision metadata, file sizes and access times are visible to Google. The scope cannot read unrelated Drive files. Android automatic system backup is disabled. Android Vault keys remain device-bound and are never exported; unlocked Vault values are carried only inside the password-encrypted portable backup. Windows keeps its encrypted data under `%APPDATA%/Life Tracker` and uses DPAPI for remembered credentials. Working attachment files are extracted locally for use by the Windows app. See [Security policy](SECURITY.md).

## License

Copyright 2026 ced2711.

Life Tracker is licensed under the [Apache License 2.0](LICENSE). It may be used, modified, and distributed under the terms of that license, including its patent grant and notice requirements.
