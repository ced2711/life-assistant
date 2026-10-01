# GitHub sync setup

GitHub sync stores the same password-encrypted snapshots that Google Drive sync uses, on a
dedicated branch (`life-assistant-sync`) of **one private repository**. Sign-in uses GitHub's
device flow: the app shows a short code, you approve it at <https://github.com/login/device>, and
no client secret is ever stored in the apps.

## For people using the app

Choose **Connect GitHub** in **Backup & sync** (Android) or **Settings** (Windows):

1. On Android, choose the sync password. On Windows the data password is used. **Use the same
   password on every device.**
2. GitHub opens and the code is already copied. Paste it, choose **Authorize**, and return.
3. Life Assistant creates a private repository named `life-assistant-data` in your account on
   first use (or reuses it) and starts syncing.

Nothing else is needed when the build carries a built-in GitHub sign-in, which release builds do.

## For developers: the built-in sign-in

Release builds use one GitHub **OAuth App** for everybody. It is created once:

1. Open <https://github.com/settings/applications/new>.
2. **Application name**: `Life Assistant`. **Homepage URL** and **Authorization callback URL**:
   `https://github.com/ced2711/life-assistant` (the callback is not used by the device flow but is
   required by the form).
3. Turn **Enable Device Flow** on and register the app.
4. Copy the **Client ID** (it starts with `Ov23`). It is not a secret. Do **not** generate a
   client secret.
5. Add `lifeassistant.github.clientId=Ov23…` to your personal Gradle properties
   (`$GRADLE_USER_HOME/gradle.properties`). Both the Android and the Windows builds pick it up.

The OAuth App asks for the `repo` scope because classic scopes offer nothing narrower that can
create and write a private repository. The token stays on the device (Android Keystore-backed
storage, Windows DPAPI) and is only used for the sync repository.

Builds without a built-in Client ID ask for one when connecting. Leave the repository field
empty to use `life-assistant-data`.

### Existing GitHub App setups

Connections made with a GitHub App (Client IDs starting with `Iv`) keep working: such an app
only reaches the repositories it is installed on, needs **Enable Device Flow**, token expiration
turned off, and **Contents: Read and write** permission. New setups should use the OAuth App
above instead, because it needs no repository or installation step.

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
| Device flow is off for this GitHub app | Turn on **Enable Device Flow** in the app settings. |
| GitHub did not recognize this Client ID | Copy the Client ID again from the app page. |
| GitHub did not allow creating the private repository | Create a private `life-assistant-data` repository yourself, then connect again. |
| Use a private repository | Change the repository to private, or pick another one. |
| GitHub sign-in expired or was revoked | Choose **Reconnect**. |
