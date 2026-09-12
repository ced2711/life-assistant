# Security policy

Life Tracker is local-only and deliberately requests no network access. Sensitive Vault values are encrypted at rest and access is gated by Android system authentication.

Never commit or upload any of the following:

- release keystores or signing passwords;
- `.tlb` user backups or their passwords;
- personal migration APKs;
- device/emulator data captures containing real user content.

Backup format constants, Vault AAD strings, the application ID, Room schema history, and the release signing certificate are compatibility boundaries. Change them only with an explicit, tested migration plan.

## Reporting a vulnerability

Please use [GitHub's private vulnerability reporting](https://github.com/ced2711/life-tracker-android/security/advisories/new). Include the affected version, reproduction steps, impact, and any suggested mitigation.

Do not open a public issue for an unpatched vulnerability and do not include real user data, backups, passwords, or signing credentials in a report. If private reporting is temporarily unavailable, contact the repository owner without disclosing the issue publicly.
