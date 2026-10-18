# Life Assistant 2.0.6

## Changed

- **Calendar pages slide.** On the phone a swipe moves the month, week or day with the finger,
  settles on the next one when let go and springs back after a short drag; before, the page
  changed only after the swipe ended, without any movement. On both the phone and the PC the
  arrows, Today and the keyboard slide the next page over from its side. The PC's calendar header
  keeps one arrangement for every month, so a month with a longer name no longer pushes the view
  switcher to another line.
- **License.** Life Assistant is no longer open source: from 2.0.6 on, all rights are reserved.
  Settings → About shows the copyright and the notices of the third-party parts; the license
  text and the source link are gone. Versions published earlier keep their licenses.

## Fixed

- **Only one copy of the PC app runs.** A copy noticed another one only once that one's local
  connection was open, seconds after it started; copies started in those seconds (at sign-in,
  by several AI assistants at once, or with a double click) all kept running in the tray. The
  last of them could not open the data, and it was the one AI assistants reached, so they found
  Life Assistant locked. Now the first copy takes a lock the moment it starts; any later start
  waits for it, brings its window forward when the start was yours, and ends. If the first copy
  has ended (even by crashing), the next start takes over.
- **AI assistants no longer find a just-started PC app locked.** When an assistant starts Life
  Assistant in the tray, its request now waits the moment it takes the app to open the data with
  the remembered password, instead of being told the app is locked. It is still told so when a
  password is really needed or the app lock is on.
- **AI assistants in Claude Desktop reach the running PC app again.** Claude Desktop, installed
  from the Microsoft Store, lets the programs it starts see AppData only through a private copy.
  Once a copy of Life Assistant had been started from there, the file telling assistants where
  the app listens stayed old in that copy, so they were told Life Assistant was not running
  while it was open in the tray. The app now also publishes that file in the temporary folder,
  which every program sees alike; assistants skip a file whose app has ended; and a copy started
  from inside such a private view starts again outside it, so it never works on an old copy of
  the data. A failure to publish the file is now written to `life-assistant.log` in the app's
  folder instead of passing unnoticed.

## New

- AI assistants on the PC can rename a daily checklist item in place (`rename_checklist_item`);
  its place in the list and today's tick stay.

## Compatibility

- Signed with the same key as 2.0.5: install it over 2.0.5 without uninstalling.
- The backup and sync format is the same as in 2.0.2.
