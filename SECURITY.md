# Security notes

Life Tracker is local-only and deliberately requests no network access. Sensitive Vault values are encrypted at rest and access is gated by Android system authentication.

Never commit or upload any of the following:

- release keystores or signing passwords;
- `.tlb` user backups or their passwords;
- personal migration APKs;
- device/emulator data captures containing real user content.

Backup format constants, Vault AAD strings, the application ID, Room schema history, and the release signing certificate are compatibility boundaries. Change them only with an explicit, tested migration plan.

If a security issue is discovered, report it privately to the repository owner rather than opening a public issue.
