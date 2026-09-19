# Security policy

Life Tracker works offline. Optional Google Drive sync uses Google's OAuth authorization and uploads password-encrypted snapshots to the user's app-specific Drive storage. Android now requests network access for this optional feature. Sensitive Android Vault values remain encrypted at rest and access is gated by Android system authentication. Windows stores its local dataset in an encrypted `.tlb` file and protects remembered credentials using Windows DPAPI; extracted working attachments remain in the current Windows user's application-data directory.

Cloud revisions expose timestamps, sizes, device IDs and change fingerprints to Google, but not plaintext backup content. OAuth tokens, remembered passwords, local credentials, and device-bound Vault keys must never be logged or included in backups. Sync is opt-in and full-dataset conflicts require explicit user resolution. See [Google Drive setup](docs/GOOGLE_DRIVE_SETUP.md).

Uploads create new encrypted files and do not automatically delete earlier revisions, including resolved branches. Cloud head checks before and after upload detect competing changes, but cannot make Google Drive's separate file operations atomic. Concurrent branches remain available for explicit resolution. Retained history consumes Drive storage; disconnecting does not erase it.

Before an explicit Use cloud replacement, each platform keeps an encrypted local recovery copy in its private application-data directory. These copies use the sync/data password in effect when created, are not automatically deleted, and remain after disconnect. Android provides an export action and Windows an open-folder action. Recovery copies are not recursively included in subsequent `.tlb` backups. Uninstalling or clearing application data can remove them, so users should export important copies separately.

Never commit or upload any of the following:

- release keystores or signing passwords;
- `.tlb` user backups or their passwords;
- personal migration APKs;
- device/emulator data captures containing real user content.

Backup format constants, Vault AAD strings, the application ID, Room schema history, and the release signing certificate are compatibility boundaries. Change them only with an explicit, tested migration plan.

## Reporting a vulnerability

Please use [GitHub's private vulnerability reporting](https://github.com/ced2711/life-tracker-android/security/advisories/new). Include the affected version, reproduction steps, impact, and any suggested mitigation.

Do not open a public issue for an unpatched vulnerability and do not include real user data, backups, passwords, or signing credentials in a report. If private reporting is temporarily unavailable, contact the repository owner without disclosing the issue publicly.
