# Desktop app (Windows and Linux)

Compose Desktop app with its own bundled Java runtime. Windows and Linux run the same code;
only a few operating-system parts differ:

- `src/` — everything shared by Windows and Linux.
- `windows/` — Windows-only code (DPAPI protection of remembered passwords, tokens and sealed
  confessions). Packaging: `.exe` and `.msi`.
- `linux/` — Linux-only code (device key in the desktop keyring or an owner-only file).
  Packaging: `.deb` and a portable `.tar.gz`.

The app also compiles a few model files directly from `../android/src/main/java` (listed in
`build.gradle.kts`), so both apps read and write the same backup format.

Build on Windows: `.\gradlew.bat :desktop:test :desktop:packageReleaseExe`
(output in `desktop/build/compose/binaries/main-release/exe/`).
Linux packages can only be built on Linux; the `Linux build` GitHub Actions workflow does it.
