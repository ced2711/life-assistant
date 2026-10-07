# Life Assistant 2.0.4

## New

- **The PC app can start with Windows, in the tray.** Turn on **Start with Windows** in
  Settings → Notifications. Life Assistant then starts when you sign in without opening its
  window: reminders, sync and AI assistants are ready, and the tray icon opens the window.
  Turning it on also keeps the app in the tray when its window is closed. It is a per-user startup
  entry, so it needs no administrator rights and shows in Task Manager → Startup apps.
  On Linux the same switch is called **Start when you sign in** and adds an entry to
  `~/.config/autostart`.

## Changed

- **One click on the tray icon opens the window**, brought to the front even when it was
  minimized or behind other windows. Before, a single click did nothing and the window opened only
  from the right-click menu or with a double click. The right-click menu still has Open and Quit.

## Compatibility

- Signed with the same key as 2.0.3: install it over 2.0.3 without uninstalling.
- The phone app has no changes besides the version number.
- The backup and sync format is the same as in 2.0.2.
