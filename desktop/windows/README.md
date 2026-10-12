# Windows

Windows-only code. Secrets are protected with DPAPI for the signed-in Windows account. The app
installs into `%LOCALAPPDATA%/Life Assistant` and keeps its data in `%APPDATA%/Life Assistant`;
versions before 2.0.4 used `Life Tracker` folders (the app's old name), and the first start of a
newer version moves the data folder over. Starting with Windows is a value under the user's `Run`
key. Installers: `:desktop:packageReleaseExe` and `:desktop:packageReleaseMsi`.
