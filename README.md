# Life Tracker

Life Tracker is a private, offline-first Android app that combines Todo, Ledger, and Calendar in one adaptive interface. It is built for phones, tablets, unusual aspect ratios, and foldables such as the Samsung Galaxy Z Fold series.

## Highlights

- Nested Todo categories, subtasks, priorities, tags, search, reminders, attachments, recurring tasks, and completion momentum.
- Manual Income/Expense ledger entries, recurring entries, attachments, summaries, and close-fit trend charts.
- Month, week, day, and agenda calendar views with daily net amounts and completed/incomplete Todo counts.
- Encrypted local Vault for credentials and private notes, protected by Android system authentication.
- Password-encrypted `.tlb` backup and full-replacement restore with validation and preview.
- Responsive Today Todo widget, including wide horizontal layouts.
- Six selectable accent palettes with light, dark, and system themes.
- No accounts, cloud sync, analytics, advertising, or `INTERNET` permission.

## Identity and compatibility

- App label: `Life Tracker`
- Package/application ID: `com.ced2711.lifetracker`
- Minimum Android version: Android 8.0 / API 26
- Target and compile SDK: API 36
- Local database: Room schema 5, with migrations from schemas 1 through 5

The package changed from the earlier private `com.taskledger.app` build. Android therefore treats Life Tracker as a new app; encrypted `.tlb` restore is the supported data migration bridge. Future Life Tracker updates preserve data when they retain the current package ID, release certificate, and compatible Room migrations.

## Build

Use JDK 17 and the included Gradle wrapper.

```powershell
.\gradlew.bat testStandardDebugUnitTest lintStandardRelease assembleStandardRelease
```

There are two distribution flavors:

- `standard`: normal install/update build; version code 6 for the 1.3.0 release.
- `personal`: one-off migration build; version code 5 so the standard APK can update it afterward.

The personal flavor intentionally requires a local `app/src/personal/assets/personal-backup.tlb`. That encrypted user backup, its password, APK outputs, release keystore, and signing credentials are excluded from Git and must never be committed, even to a private repository.

## Verification baseline

- 316 standard debug JVM tests pass.
- Standard and personal Release Lint: 0 errors.
- Standard Android instrumentation sources compile after the package migration.
- Signed personal-to-standard `adb install -r` verification preserved every app-data file hash and the original install timestamp.
- Both release APKs use the same release certificate.

## Privacy

All application data stays on the device unless the user explicitly exports an encrypted backup or attachment through Android's system file picker. Android automatic backup is disabled. Vault keys remain device-bound in Android Keystore and are never exported.
