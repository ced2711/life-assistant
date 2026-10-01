# Linux

Linux-only code. Data lives in `$XDG_DATA_HOME/life-assistant` (usually `~/.local/share/life-assistant`).
Secrets are encrypted with a device key kept in the desktop keyring through `secret-tool`
(install `libsecret-tools`), or, without a keyring, in a file only your user can read.
Packages (`.deb`, portable `.tar.gz`) are built by the `Linux build` GitHub Actions workflow.
