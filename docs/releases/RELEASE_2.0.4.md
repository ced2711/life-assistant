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
  from the right-click menu or with a double click.
- **The tray menu has Open, Settings and Quit.** Settings holds **Start with Windows** and
  **Keep in the tray when closed** as check marks, so both can be changed without opening the
  window. They are the same settings as in Settings → Notifications; a change in one place shows
  in the other.

- **The PC app's folders are called Life Assistant.** Earlier versions installed into
  `AppData\Local\Life Tracker` and kept their data in `AppData\Roaming\Life Tracker`, after the
  app's old name. 2.0.4 installs into `AppData\Local\Life Assistant` and replaces the old copy;
  on its first start it moves the data folder to `AppData\Roaming\Life Assistant` and renames the
  data file to `life-assistant-local.tlb`. Starting with Windows and AI assistants already
  connected in Claude Desktop or Claude Code are pointed at the new place by themselves. If the
  data folder cannot be moved (a file in it is in use), the old folder stays in use and nothing is
  lost; the move is tried again on the next start.

- **The PC window's title bar follows the app's theme.** Windows drew it white whenever Windows
  itself was in light mode, above a dark app. It is now dark with the dark theme, in the app's own
  background colour on Windows 11, and light with the light theme.
- **The calendar looks the same for today as for any other day**, on the phone and the PC. The
  Today button that leads back to today used to appear only for other days and pushed the title
  and the view switcher aside; it now keeps its place and is only hidden while today is shown. The
  PC's Day view is titled with the date for today too, as on the phone.

## Compatibility

- Signed with the same key as 2.0.3: install it over 2.0.3 without uninstalling.
- On the phone only the calendar changed.
- The backup and sync format is the same as in 2.0.2.
