# Life Assistant licensing

Copyright 2026 ced2711. First-party code from release 1.7.0 is available under
GNU Affero General Public License version 3 only (`AGPL-3.0-only`), with the
narrow additional permission in [ADDITIONAL_PERMISSIONS.md](../ADDITIONAL_PERMISSIONS.md).
The full, unmodified license text is in [LICENSE](../LICENSE).

## What this means

- Running the application, including commercial use, is permitted.
- Sharing covered binaries requires complying with the license's Corresponding
  Source and notice requirements. Shared modified versions remain under the
  applicable AGPL terms.
- If a modified covered program supports remote network interaction, its users
  must be prominently offered its Corresponding Source under section 13. This
  is why AGPL was chosen with possible future hosted services in mind.
- Ordinary use and private local modifications do not by themselves require
  public publication. A user's notes, passwords, spending records and files are
  not application source code and do not become public under this license.
- Merely calling an unrelated third-party AI API does not automatically
  relicense that service or its model. The status of a combined or derivative
  work depends on the actual integration and applicable law.
- There is no warranty. The full license, not this summary, controls.

## Google Play services permission

Android currently uses Google's Play services libraries for Google account
authorization. Their Maven metadata specifies Google's Android SDK license,
not AGPL. The copyright holder grants only the narrow linking permission
described in [ADDITIONAL_PERMISSIONS.md](../ADDITIONAL_PERMISSIONS.md).
This does not permit keeping Life Assistant modifications proprietary when the
AGPL requires their Corresponding Source, and does not grant rights to Google's
libraries beyond their own terms.

## Earlier releases and third-party code

Earlier Life Tracker versions distributed under Apache License 2.0 retain
their original license. No existing Apache grant is withdrawn. Git history
is retained so recipients can identify the applicable version and terms.

Third-party dependencies, the Gradle wrapper, and bundled runtimes keep their
own licenses. Do not replace their copyright headers with the app's license.
See [NOTICE](../NOTICE), dependency metadata/notices, and the desktop runtime's
`legal` directory. The first-party license is not a claim of ownership over
third-party code. A distributor is responsible for auditing the dependencies
in the binaries they actually distribute.

## Corresponding source and building a fork

The source accompanying a binary must match that binary's version, including
local patches. Release 1.7.1 uses the `v1.7.1` tag in
<https://github.com/ced2711/life-assistant>. Follow the repository's build
instructions; use your own Android signing key and your own OAuth application
registration for a separately distributed fork. The author's private signing
key, OAuth credentials, personal backups and user data are not included.

Both Android and Windows expose the bundled license, linking permission and
notices offline in Settings, and provide a link to the matching source tag.
When redistributing a modified version, update the source link to where users
can obtain that version's complete Corresponding Source. A future hosted
modified version must provide a suitable source offer to its remote users;
the desktop Settings link alone is not sufficient for a separate web service.

This document describes the project's licensing choices, not legal advice or
a guarantee that every possible redistribution or integration is compliant.
