# Life Assistant 1.9.0

## New

- **One-click GitHub sync.** Choose **Connect GitHub**, paste the code that is already copied,
  approve, and you are done: Life Assistant creates a private `life-assistant-data` repository in
  your account and starts syncing. No Client ID, repository or GitHub App to set up.
- **Phone and computer merge automatically.** When both changed since the last sync, changes are
  merged record by record instead of asking which side to keep. A change on one device is kept, a
  deletion applies unless the other device edited that item, and when both edited the same field
  the newer edit wins. Notes, diary pages and descriptions edited on both devices keep both
  versions, separated by a marker. Items that both devices numbered alike are renumbered.
  Simultaneous uploads are merged too. Only a cloud version with a different password still asks.
- **Linux version** (`.deb` and a portable `.tar.gz`), built automatically on GitHub.
- **Redesigned desktop app for large screens**:
  - A labelled sidebar with counts, sync status, Vault and Settings.
  - **Todo**: filter column (Today, Next 7 days, Overdue, No date, category tree, priority, tags,
    sort), list grouped by date, and an editor beside it with subtasks, due time, reminders,
    repeat, Urgent priority, attachments and Undo for deletes. Rename or delete categories and tags.
  - **Ledger**: week, month, year or all-time views with income, expense, net, average daily
    spending and largest expense; a quick-add row; the period's chart and spending by tag;
    entry time, repeating entries and a **Recurring** tab to change, stop or remove schedules.
  - **Calendar**: the month shows each day's todos and net amount, the selected day's todos,
    entries and diary page sit beside it with quick add; week and agenda views.
  - **Notes**: folders, list and editor side by side, with autosave.
  - **Vault**: opens after the data password, list and details side by side, copy buttons that
    clear the clipboard after 30 seconds.
  - **Reminders on the PC** as system notifications, a tray icon, and optionally keeping the app
    running in the tray when the window closes.
  - Reminder settings (also used by the phone), readable option names, a centered Settings page,
    reviewing a backup before it replaces local data, and exporting with a separate password.
- **Keyboard** on the desktop: Ctrl+N new item, Ctrl+F search, Ctrl+1–9 modules, Ctrl+R sync,
  Ctrl+, settings, Ctrl+S save, Esc close, arrow keys in lists and the calendar, Enter to add.
- The desktop window remembers its size and position.

## Fixed

- Deleting one occurrence of a repeating todo or ledger entry on the PC no longer lets the phone
  re-create it later.
- The PC now creates due repeating todos and ledger entries itself instead of waiting for the phone.
- New todos with a date made on the PC get the default reminders, so the phone reminds you.

## Compatibility

- Signed with the same key as 1.8.0: install it over 1.8.0 without uninstalling.
- The backup and sync format is unchanged. A 1.8.0 device still syncs with 1.9.0 but asks instead
  of merging when both changed, so update every device.
- Existing GitHub App connections keep working. Connecting again uses the new one-click sign-in,
  which syncs through the new `life-assistant-data` repository; the first sync there merges the
  data of both devices.
- Google Drive sync is shown as coming soon until its Google Cloud project is published.
