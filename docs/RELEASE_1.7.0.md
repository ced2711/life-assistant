# Life Assistant 1.7.0

Life Tracker is now **Life Assistant**, by **ced2711**. The Simplified Chinese
interface uses **生活助手**. This release does not add AI services or a desktop
pet; those are possible future features, not present capabilities.

## Changes

- Android launcher label, About screen, backup filenames, notifications,
  recovery messages and widget guidance use the new name.
- Windows application/installer branding, title, About screen, authorization
  callback and manual backup filenames use the new name.
- Settings on both platforms includes an offline license viewer and a link
  to this release's source code.
- First-party licensing changes prospectively to AGPL-3.0-only with a narrow
  Google Play services linking permission. Previous Apache releases retain
  their original terms. See [licensing notes](LICENSING.md).
- The repository is now <https://github.com/ced2711/life-assistant>.
- Includes the previously prepared 1.6.1 authorization diagnostics, retained
  local sync-recovery snapshots, Windows offline defaults and UI/localization
  parity improvements. Live Google OAuth still requires developer setup.

## Upgrade without losing data

Do **not** uninstall the existing app or clear its storage first. A manual
encrypted `.tlb` backup is recommended before any upgrade.

Android retains `com.ced2711.lifetracker`, the release signing certificate,
Room schema 6 and its migration chain, launcher component identities,
DataStore and Android Keystore identifiers. The standard APK is version code
14. Older TaskLedger/Life Tracker `.tlb` exports remain readable.

Windows retains its existing installer upgrade UUID and default install
directory. The encrypted local data, attachments, recovery snapshots,
settings and remembered DPAPI credentials stay under `%APPDATA%/Life Tracker`.
The old name in this folder is intentional: no data is moved or duplicated.

Both platforms retain the Google Drive protocol `life-tracker-sync-v1`,
cloud filename prefix and MIME type. Reuse the existing Google Cloud project
and OAuth clients. A repository/display rename must not create a new cloud
namespace. Google's consent-screen name must be updated separately in the
existing Cloud project if it has already been configured.

## Verification

- Android JVM tests: **342/342**, in both standard debug and release builds.
- Shared cloud-sync tests: **18/18**, including unchanged legacy namespace.
- Windows JVM tests: **32/32**, including legacy data/config directory and
  offline bundled-license checks. Total unique JVM tests: **392**.
- Release Android lint: **0 errors, 53 warnings and 3 hints**.
- Signed Android 1.6.1 (code 13) -> 1.7.0 (code 14) in-place emulator upgrade
  succeeded without uninstalling. A read-only snapshot across 15 user tables
  containing one synthetic todo had exactly the same SHA-256 before/after:
  `213496e281d4240770f374c43830a8977104e2bc01dea4b36abde39497b5b5b2`.
- Six post-upgrade Android instrumentation tests passed: snapshot readability,
  the new label, three retained launcher aliases, bundled legal assets, and
  isolated backup export/restore round-trips. The snapshot test also passed
  separately before upgrading. No personal user data was printed or bundled.
- Release APK signature certificate SHA-256 remains
  `4a23242a1e146b8f8966ce2694c2bcc166733572d57eb94ee29c80bcfc25478b`.
- Windows EXE/MSI and portable app-image builds succeeded. MSI metadata was
  compared to 1.6.1: the product name/version changed, while UpgradeCode
  `{4F5DCD57-85A1-4CB5-9E14-A0D5C8B8940B}` and the upgrade family are unchanged.
  A real installed Windows 1.6.1 -> 1.7.0 upgrade was not exercised in this run;
  installer-table inspection and isolated data/config tests are not a full
  end-to-end installer test. Windows binaries are not Authenticode-signed.
- The root AGPL license was compared with the official FSF text, and the full
  license, additional permission and NOTICE were verified in both packages.

No claim of real Google OAuth, Samsung One UI or physical Galaxy Fold testing
is made by unit tests or AOSP emulator checks. The current Google Cloud setup
limitation remains; this rebrand does not register OAuth clients automatically.
