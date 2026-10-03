# Life Assistant 2.0.1

## Fixed

- **GitHub sync that stopped working some hours after connecting.** GitHub sign-ins of Life
  Assistant are valid for eight hours and come with a second key to renew them (GitHub's default
  for apps registered since August 2026). The apps threw that second key away and never renewed,
  so every device was disconnected at most eight hours after it connected. Now each device keeps
  the renewal key, renews its sign-in by itself shortly before it expires, and tries once more
  when GitHub turns a sign-in down. Nothing has to be reconnected by hand any more.
- **Reconnecting or disconnecting one device no longer signs the others out.** 2.0.0 asked GitHub
  to revoke the sign-in a device had used before, and GitHub then removed the app's whole
  authorization, with the sign-ins of every device. The apps no longer revoke anything.

## New

- **The home-screen widget was rebuilt.** It wears the app's own colours and accent and follows
  the app's light or dark setting. It lists what is overdue (in red) and due today, with the
  round check marks in their priority colours, the due time, and a bar for the day's progress.
  The list scrolls, ticking a todo off shows at once, tapping a title opens that todo, and two
  round buttons add a todo or an expense with the keyboard already open. The single-cell and the
  one-row sizes show the count and the next todo.

## Changed

- **Today is no longer in the menu by default.** Switch it on in Settings → Appearance → modules
  in the menu if you want it. A device that has not chosen Today shows the usual Todo, Ledger,
  Calendar and Notes, and a new device opens on Todo.

## After updating

- Install 2.0.1 on **every** device, then choose **Reconnect** once on each
  (Settings → Sync & backup). The sign-in saved by an older version has no renewal key, so this one
  reconnect is needed; after it the devices stay connected. Your data and the sync password stay
  the same.

## Compatibility

- Signed with the same key as 2.0.0: install it over 2.0.0 without uninstalling.
- The backup and sync format is unchanged.
