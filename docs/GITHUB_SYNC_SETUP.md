# GitHub sync setup

GitHub sync stores the same password-encrypted snapshots that Google Drive sync uses, on a
dedicated branch (`life-assistant-sync`) of **one private repository**. Sign-in uses a GitHub App
and GitHub's device flow: the app shows a short code, you approve it at
<https://github.com/login/device>, and no client secret is ever stored in the apps.

The setup takes about five minutes and is done once. Android and Windows then use the same app
and the same repository.

## 1. Create the private repository

1. Open <https://github.com/new>.
2. Name it, for example `life-assistant-data`, and choose **Private**.
3. Create it. It can stay empty; Life Assistant adds a README and its own branch on first sync.

## 2. Create the GitHub App

1. Open <https://github.com/settings/apps/new>.
2. **GitHub App name**: anything unique, for example `life-assistant-sync-<your name>`.
3. **Homepage URL**: `https://github.com/ced2711/life-assistant` (any URL works).
4. **Callback URL**: leave empty.
5. Turn **Enable Device Flow** on.
6. Under **Expire user authorization tokens**, turn the option **off**. Refreshing expiring
   tokens would require a client secret inside the apps, which is exactly what this setup avoids.
7. **Webhook**: turn **Active** off.
8. **Repository permissions** → **Contents**: **Read and write**. (Metadata: Read-only is added
   automatically.) Leave every other permission at *No access*.
9. **Where can this GitHub App be installed?** → **Only on this account**.
10. Create the app, then copy its **Client ID** (it starts with `Iv`). The Client ID is not a secret.
    Do **not** generate a client secret or private key; they are not needed.

## 3. Install the app on the repository

1. On the app's page, choose **Install App** → your account.
2. Select **Only select repositories** and pick the private repository from step 1.

## 4. Connect Life Assistant

On each device, open **Backup & sync** (Android) or **Settings** (Windows) and choose
**Connect GitHub**:

1. Enter the Client ID. The repository field can stay empty when the app is installed on exactly
   one repository.
2. On Android, choose the sync password. On Windows the data password is used. **Use the same
   password on every device.**
3. Life Assistant shows a code. Choose **Open GitHub**, sign in, enter the code and approve.
4. The first sync starts automatically.

To prefill the Client ID in your own builds, add `lifeassistant.github.clientId=Iv…` to
`gradle.properties` (Android) or set `LIFE_ASSISTANT_GITHUB_CLIENT_ID` (Windows).

## What is stored

- `life-assistant-sync` branch: `index.json` (revision metadata: timestamps, device IDs and content
  fingerprints) and `revisions/*.tlb` (encrypted snapshots). Nothing is readable without the
  sync password.
- Each upload is one commit. The branch only moves by fast-forward, so two devices uploading at
  the same moment both keep their version and the next sync asks which one to keep.
- After every successful upload only the 10 newest versions stay on the branch. Older versions
  remain in the git history until you delete the branch; deleting it simply starts a new history
  on the next sync.
- A single snapshot must stay below GitHub's 100 MB file limit.

## Troubleshooting

| Message | Fix |
| --- | --- |
| Device flow is off for this GitHub App | Turn on **Enable Device Flow** in the app settings. |
| GitHub did not recognize this Client ID | Copy the Client ID again from the app page. |
| The GitHub app cannot write to owner/name | Install the app on that repository with Contents read and write. |
| Use a private repository | Change the repository to private, or pick another one. |
| GitHub sign-in expired or was revoked | Choose **Reconnect**. If this happens every 8 hours, turn off token expiration (step 2.6). |
