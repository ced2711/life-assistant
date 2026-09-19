# Life Assistant

Life Assistant is an open-source, offline-first personal organizer for Android and Windows. It combines Todo, Ledger, Calendar, and Notes, with encrypted backup and optional Google Drive synchronization. The Android interface uses device-independent sizing for phones, tablets and foldables, including narrow clamshell layouts such as the Samsung Galaxy Z Flip series. The Windows app is a native desktop application with its own bundled Java runtime; no browser or separate Java installation is required.

By **ced2711** · 中文名：**生活助手** · [Source](https://github.com/ced2711/life-assistant)

## Highlights

- Nested Todo categories, subtasks, priorities, tags, search, reminders, attachments, recurring tasks, and completion momentum.
- Manual Income/Expense ledger entries, recurring entries, attachments, summaries, and close-fit trend charts.
- Month, week, day, and agenda calendar views with daily net amounts and completed/incomplete Todo counts.
- Long-term Notes with collapsible search and nested-folder controls, pinned notes, and multiple private file/image attachments.
- Encrypted local Vault for credentials and private notes, protected by Android system authentication.
- Password-encrypted `.tlb` backup and full-replacement restore with validation and preview.
- Responsive Today Todo widget, including wide horizontal layouts.
- Six selectable accent palettes with light, dark, and system themes.
- Fullscreen Android status-bar policy, camera-cutout and keyboard avoidance, and tabletop-pane handling without brand-specific device lists.
- Optional Google Drive authorization and automatic encrypted snapshot sync, with explicit conflict resolution and retained cloud history.
- Offline use without accounts; no analytics or advertising. Google authorization is required only when enabling Drive sync.

## Windows and Google Drive

The Windows app supports local Todo, Ledger, Calendar, Notes, folders, attachments, Vault, and manual `.tlb` import/export. It uses the same backup codec, encryption, theme palettes and formatting utilities as Android. Both offer English and Simplified Chinese UI, light/dark/system themes and six accent colors. Windows also supports multi-category Todo filtering and collapsible Notes folders/search. New Windows installations are offline by default, with automatic sync off; connect Drive explicitly in Settings. Advanced Android recurrence creation, reminder scheduling and widgets remain Android features; imported recurrence metadata is preserved when editing existing occurrences on Windows.

**Google Cloud setup is required before live sign-in works.** This repository does not bundle production OAuth credentials. Register the Android package/signing certificate and a Desktop OAuth client in the same Google Cloud project, then authorize the same Google account on both devices. Follow [Google Drive setup](docs/GOOGLE_DRIVE_SETUP.md). Until configured, local use and encrypted file transfer work independently.

Sync transfers complete encrypted datasets. If both sides changed, choose which version to use; it is not a record-by-record merge. Each upload creates a new encrypted file; older versions and competing branches are not automatically deleted. Cloud heads are checked before and after upload. Drive has no atomic cross-file compare-and-create, so simultaneous uploads can produce branches; both are preserved and sync asks for review. Password mismatches are rejected rather than treated as content conflicts. Android Vault authentication is still required for complete snapshots containing Vault entries, so unattended sync waits for unlock when necessary. Windows sync runs while the app is open and unlocked.

## Identity and compatibility

- App label: `Life Assistant`
- Package/application ID: `com.ced2711.lifetracker`
- Minimum Android version: Android 8.0 / API 26
- Target and compile SDK: API 36
- Local database: Room schema 6, with migrations from schemas 1 through 6

Life Assistant 1.7.0 is a display-name change from Life Tracker, not a new Android application. Install it over Life Tracker without uninstalling: the application ID, release certificate, launcher aliases, database, settings, Vault keys, and sync protocol remain unchanged. Windows retains the installer upgrade UUID and the `%APPDATA%/Life Tracker` data directory so existing encrypted data and remembered credentials remain available. The old name in internal paths is intentional compatibility, not unfinished branding.

Only the much older private `com.taskledger.app` (TaskLedger) package requires migration through an encrypted `.tlb` export and restore. Existing `.tlb` files and Google Drive revisions remain compatible; only the suggested filename of new manual exports changes to `LifeAssistant-backup...tlb`.

## Build

Use JDK 17 and the included Gradle wrapper.

```powershell
.\gradlew.bat testStandardDebugUnitTest lintStandardRelease assembleStandardRelease
.\gradlew.bat :cloudsync:test :desktopApp:test :desktopApp:packageExe :desktopApp:packageMsi
```

There are two distribution flavors:

- `standard`: normal Android install/update build; version code 15 for the 1.7.1 release.
- `personal`: one-off migration build; version code 5 so the standard APK can update it afterward.

The personal flavor intentionally requires a local `app/src/personal/assets/personal-backup.tlb`. That encrypted user backup, its password, APK outputs, release keystore, and signing credentials are excluded from Git and must never be committed, even to a private repository.

Windows distributions are written to `desktopApp/build/compose/binaries/main/exe` and `msi`. Use `:desktopApp:createDistributable` to create a portable app folder. Building installers requires Windows and JDK 17; Gradle obtains the WiX packaging tools as needed. Release Android signing is configured locally through the existing signing environment variables, never through committed credentials.

## Verification

The project includes JVM, Android instrumentation, sync transport/branch-conflict, Windows encrypted-store/attachment, and Windows DPAPI tests. See the [1.7.1 fullscreen and compatibility report](docs/RELEASE_1.7.1.md), [1.7.0 release notes](docs/RELEASE_1.7.0.md) and historical [1.6.0 verification report](docs/RELEASE_1.6.0.md). The cloud tests use a local HTTP fixture; a real OAuth end-to-end check additionally requires the developer's configured Google Cloud project and interactive consent. Unit tests alone do not establish that live Google authorization is configured.

Android support remains **Android 8.0 (API 26) and newer**, for phone/tablet app environments. There is no Samsung-only restriction. Tests on Android 8 and Android 16 emulators and resized/folded layouts do not prove compatibility with every OEM, keyboard, or future OS release. Camera hardware cannot be removed; system-owned authentication, permission or external-app screens control their own bars. Flip cover-screen launch access is controlled by Samsung/One UI (and may require its supported launcher/settings); adapting the app's small-window layout does not bypass those restrictions.

## Privacy

Application data stays on the device unless the user exports it or enables Google Drive sync. Optional sync sends encrypted `.tlb` snapshots to Google's app-specific Drive storage; revision metadata, file sizes and access times are visible to Google. The scope cannot read unrelated Drive files. Android automatic system backup is disabled. Android Vault keys remain device-bound and are never exported; unlocked Vault values are carried only inside the password-encrypted portable backup. Windows keeps its encrypted data under `%APPDATA%/Life Tracker` and uses DPAPI for remembered credentials. Working attachment files are extracted locally for use by the Windows app. See [Security policy](SECURITY.md).

## License

Copyright 2026 ced2711.

Starting with 1.7.0, first-party Life Assistant code is licensed under **GNU Affero General Public License, version 3 only (AGPL-3.0-only)**, with a narrow [Google Play services linking permission](ADDITIONAL_PERMISSIONS.md). Read the full [LICENSE](LICENSE) and [licensing notes](docs/LICENSING.md).

You may use the app, including commercially. Distributing covered binaries requires making their Corresponding Source available under the applicable license terms. If you modify a covered program and let people interact with it remotely over a network, AGPL section 13 requires offering those users its Corresponding Source. Ordinary use does not require publishing personal notes, financial records, files, credentials, or unrelated software. Purely private local modifications do not by themselves require publication.

Earlier Apache-2.0 releases retain their original licensing; those grants are not retroactively revoked. Third-party components retain their own terms and notices. This software comes without warranty.

Source for each binary release is identified by its matching version tag, for example [`v1.7.1`](https://github.com/ced2711/life-assistant/tree/v1.7.1). Both apps provide an offline license viewer and a source-code link in Settings. Build instructions above apply to the corresponding source; supply your own signing key for a fork rather than requesting the author's private release key.
