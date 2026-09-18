# Life Tracker 1.6.0 verification

## Delivered scope

- Native Windows Compose Desktop application and EXE/MSI installers with a bundled Java runtime.
- Android standard release: version name `1.6.0`, version code `12`, existing application ID and release certificate retained, Room schema unchanged at version 6.
- Optional Android/Windows Google Drive encrypted snapshot synchronization, explicit conflict resolution, revision-graph ancestry checks, and safe recent-history retention.
- Windows Todo, Ledger, Calendar, Notes, folders, attachments, Vault, and encrypted backup import/export. Manual import can decrypt an older backup password and re-encrypt with the current Windows password.
- Android automatic sync requests a 15-minute WorkManager interval and startup check; Windows checks while running and unlocked.

## Checks completed

Verified on Windows with JDK 17 and an Android API 36 emulator:

| Check | Result |
| --- | --- |
| Android standard-release JVM tests | 333 passed |
| Shared cloud transport and revision-graph JVM tests | 16 passed |
| Windows encrypted-store, migration, attachment and DPAPI tests | 7 passed |
| Targeted Android sync and backup instrumentation | 12 passed |
| Android release lint | 0 errors; 52 warnings and 3 hints remain |
| Android signed release build | Passed |
| Windows EXE/MSI packaging and distributable creation | Passed |
| APK certificate comparison with 1.5.2 | Same release certificate |
| Signed 1.5.2 → 1.6.0 emulator upgrade | Synthetic Todo retained |
| Packaged Windows UI smoke test | Todo, Ledger, Calendar, calendar editing, Notes/folders/attachments and Settings inspected |

Tests and screenshots used synthetic data, not private user backups. The Windows distributable was launched in an isolated `APPDATA` fixture. Installer files were produced successfully; installer wizard interaction was not separately exercised.

The Android checks use a project-local Gradle cache to avoid a Windows lock affecting the global cache:

```powershell
.\gradlew.bat :app:testStandardReleaseUnitTest :app:lintStandardRelease :app:assembleStandardRelease --no-daemon -g .gradle-user
.\gradlew.bat :cloudsync:test :desktopApp:test :desktopApp:packageExe :desktopApp:packageMsi
```

Release signing credentials are local environment configuration and are not part of the repository.

## Remaining external setup and limitations

- Real Google authorization and two-device Drive end-to-end synchronization have **not** been verified. Register the Android release certificate/package and a Desktop OAuth client in the same Google Cloud project, enable Drive API, and authorize the same account. See [Google Drive setup](GOOGLE_DRIVE_SETUP.md).
- Sync replaces a complete dataset after explicit conflict selection; it does not merge individual records.
- Android Vault authentication remains required for snapshots containing Vault entries. Locked Vault data prevents unattended full sync until authenticated.
- Windows is initially English/dark, with selectable accent colors. Advanced recurrence creation, reminder scheduling and widgets remain Android-only; imported recurrence metadata is preserved.
- Windows installers are not Authenticode-signed and may show an unknown-publisher warning.

No private backups, release signing keys, OAuth tokens, real passwords or personal test captures are included in source control.
