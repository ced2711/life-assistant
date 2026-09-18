# Google Drive setup (Android + Windows)

Google Drive sync is optional. Local use and manual encrypted `.tlb` export/import do not require a Google account. The application does not contain a configured production OAuth project. A Google account alone is not enough to enable this integration: the application clients must first be registered.

## One Google Cloud project for both apps

1. Create or select a project in [Google Cloud Console](https://console.cloud.google.com/).
2. Enable **Google Drive API** in APIs & Services.
3. Configure Google Auth Platform / OAuth consent: application name **Life Tracker**, your support email, and your audience. For personal testing, choose External/Testing and add your own Google account as a test user. Do not publish real user data in screenshots or project descriptions.
4. Request only `https://www.googleapis.com/auth/drive.appdata`. This gives access to the app's hidden data folder, not the rest of the user's Drive.
5. Create an **Android** OAuth client with package `com.ced2711.lifetracker` and the SHA-1 fingerprint of the APK signing certificate. Use the release certificate for release APKs; a debug certificate requires a separate Android client. A future Google Play build needs its Play App Signing certificate registered too.
6. Create a **Desktop app** OAuth client in the **same project**. Enter that client ID and, if supplied by Google's Desktop client configuration, its client secret in Windows Settings. Do not create a Web application client for Windows. Native Desktop client secrets are not confidential server secrets; PKCE and system-browser consent protect authorization. This app keeps the local value in Windows DPAPI rather than source control.

Both clients must belong to the same Cloud project and use the same Google account to access the same `appDataFolder`. Android's authorization client identifies the installed app using its package/signing certificate; there is no Android client ID textbox in the app.

An OAuth project left in Testing can require reauthorization when Google's test credentials expire. For distributing to other people, configure the production consent screen, privacy policy, audience and any verification Google requires for that project. Each user grants access to their own Drive.

## Connect your devices

1. Make a manual encrypted `.tlb` backup before the first sync.
2. On Android, open **Settings → Backup & sync**, authorize Google Drive, and set a strong sync password. Select automatic sync if desired. If Vault contains entries, unlock it when prompted to create a complete encrypted snapshot.
3. On Windows, create local encrypted storage using the **same sync password**. In Settings, enter the Desktop OAuth configuration and connect the same Google account in the system browser.
4. An empty Windows dataset can download the cloud snapshot. If both sides already have data, the app asks which complete dataset to keep. Choosing a side replaces the other dataset; it is not a record-by-record merge. The latest 30 cloud snapshots are retained. Older confirmed ancestors may be deleted after a successful upload; competing branches are never pruned automatically. A device offline longer than the retained history may require an explicit conflict choice on reconnect.
5. Changes are uploaded/downloaded while the app is running, or by Android scheduled work when Android allows it. A 15-minute background interval is a scheduling request, not a guaranteed delivery time. Windows must be running and unlocked to sync.

The password is for Life Tracker encryption, **not** your Google password. Losing it can make the encrypted cloud backup unreadable. Google authorization tokens and encryption keys are never included in `.tlb` backups.

## Security and behavior

- Both clients upload encrypted `.tlb` snapshots using the existing PBKDF2-SHA256 / AES-256-GCM format.
- Each upload creates an immutable revision. Device clock differences do not decide which data wins.
- Simultaneous changes and competing cloud branches require an explicit choice; the app does not silently merge or overwrite them.
- The sync engine rechecks local state before applying a download. Attachments are staged before committing a replacement.
- Android Vault encryption remains gated by device authentication. A complete sync involving locked Vault entries waits for unlock. There is no unattended Vault key export or weakened biometric setting.
- Disconnecting removes local sync credentials/state, keeps local data and leaves existing encrypted Drive files available. It is not a remote wipe.
- Google can see account access, traffic, ciphertext size and revision metadata (device ID, timestamps and fingerprints); it cannot read the encrypted backup content without the password.

## Troubleshooting

- **Developer error / app not configured:** check Android package name, release SHA-1, Drive API enablement and OAuth project.
- **Access blocked / test user:** add the selected account to the consent screen's test users, or complete production publishing requirements.
- **Empty cloud on the other device:** verify both OAuth clients are in the same Cloud project and both devices selected the same account.
- **Wrong password / damaged backup:** use the same Life Tracker sync password on both devices. Do not choose a destructive overwrite to work around a password mismatch.
- **Waiting for unlock:** open Android Life Tracker and authenticate its Vault, then sync.
- **Cloud history missing:** reconnect only after verifying the Google account and project; local data remains available.

Official references: [Drive app data](https://developers.google.com/workspace/drive/api/guides/appdata), [Android authorization](https://developers.google.com/identity/authorization/android), [OAuth for installed apps](https://developers.google.com/identity/protocols/oauth2/native-app).
