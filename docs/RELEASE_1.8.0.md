# Life Assistant 1.8.0

## New

- **Diary**: one auto-saved page per day. Calendar days with a page show a green dot in the
  top-right corner, and the day details open the page directly.
- **Confessional**: write something down, then burn it (it is gone) or seal it on this device.
  Sealed words are encrypted with a device key (Android Keystore, Windows DPAPI), never backed up
  or synced, and Android blocks screenshots on this screen.
- **App lock** (off by default): fingerprint, face or screen lock on Android, the data password on
  Windows, after the app has been in the background for a chosen time.
- **Modules in menu**: choose which modules appear in navigation. Diary and Confessional start
  hidden.
- **Delete from the todo editor**, with Undo on Android.
- **GitHub sync** as an alternative to Google Drive. See [GitHub sync setup](GITHUB_SYNC_SETUP.md).

## Fixed

- The header no longer leaves an empty strip under a punch-hole camera or small notch; only the
  title and buttons step around the camera.
- Google Drive errors explain the real cause (API disabled, rate limit, full storage) instead of
  always asking to reconnect. The Windows Desktop client secret is required and labelled so.
- Windows sign-in and sync keep running when you leave the Settings page.

## Lighter

- Android release APK: 18.4 MB → about 4 MB (code and resource shrinking).
- Windows installer: about 104 MB → 51 MB (ProGuard shrinking of the release build).
- Backups, cloud uploads and the Windows data file are compressed before encryption; text-heavy
  data shrinks more than five times.
- Cloud sync keeps the 10 newest versions instead of every version ever uploaded, and only the
  5 newest local recovery copies are kept.

## Compatibility — read before updating

- **New signing key.** 1.8.0 is signed with a new release key, so Android cannot install it over
  1.7.x. Before updating: open the old app → **Backup & sync** → export an encrypted `.tlb`
  backup. Then uninstall the old app, install 1.8.0, and restore the backup. Vault entries are
  included in the backup.
- **New backup format.** Backups and cloud versions written by 1.8.0 cannot be opened by 1.7.x.
  Update Android and Windows together before syncing. Older backups open normally.
- Google Drive users must register the new signing certificate's SHA-1 in Google Cloud.
- Room database schema 7; data migrates automatically.

## Verification

- JVM tests: Android 377, cloud sync 26, Windows 38 — all passing.
- Android 16 emulator: instrumented suite passing, except the 1.7.0 rebrand evidence test, which
  needs an existing installation with user data by design.
- Checked on the emulator at phone, small-window and tablet sizes with an emulated punch-hole
  camera. No physical Samsung device was tested.
