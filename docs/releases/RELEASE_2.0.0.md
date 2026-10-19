# Life Assistant 2.0.0

## New

- **A new look on the phone and the computer.** Every screen was rebuilt on one design: calm
  surfaces, round check marks in the priority colour, rows instead of cards, one accent colour,
  and the same wording on both devices.
- **Today**: a new first page. What is overdue and due today to tick off, the coming week, what
  you spent today and this month, today's diary page and your pinned notes. A new device starts
  here.
- **Phone**
  - **Todo**: quick views (All, Today, Next 7 days, Overdue, No date), search, a filter sheet
    with sort by deadline, priority, created, title or your own order, the list grouped by due
    date, subtasks that unfold in the list, swipe to delete with Undo. Dates can be typed as
    "tomorrow", "fri", "周五" or in your own date format, or picked from a calendar.
  - **Ledger**: week, month, year or all-time view with income, expense and net at the top,
    search, entries grouped by day with the day's total, swipe to delete with Undo, spending by
    tag in Statistics, and asking before a schedule is stopped.
  - **Calendar**: month, week, day and agenda views; tick todos off and add a todo for the
    selected day; swipe to change month.
  - **Notes and Diary** save by themselves while you write and when you leave; Back never loses
    text. Notes have a Pinned view and a folder menu.
  - **Vault**: tap an entry to read it, with Copy for account, password and website.
  - **Settings** grouped as on the computer.
  - **Tablets and foldables**: list and details side by side on wide screens. A book-style
    foldable held half open shows the navigation with names and the day at a glance on one half
    and the page on the other; a flip phone standing on a table shows the day at a glance on the
    upper half. Nothing is drawn across a hinge.
- **Computer**
  - **No password at start.** The app opens straight away and works offline on its own. You
    choose a data password only when you first use the Vault, the app lock, sealed confessions or
    cloud sync.
  - Every page rebuilt: Today, Todo, Ledger (Entries, Statistics, Recurring), Calendar (month,
    week, day, agenda), Notes, Diary, Confessional, Vault and Settings, and all of them fit small
    windows.
  - **AI assistants can use your data.** Switch on **Settings → AI assistants** and add Life
    Assistant to Claude Desktop or Claude Code with one click. An assistant can then read and,
    if you allow changes, add and edit todos, ledger entries, notes, diary pages and categories
    while the app runs. The Vault and the Confessional are never exposed. Off by default.
- **Daily backups.** Each day both apps keep a copy of the day before, and delete it on the third
  day. Restore one in **Settings → Sync & backup**; the Vault is not touched, and the restore can
  be undone.

## Fixed

- **GitHub sync that stopped after a day.** A sign-in that GitHub had retired made sync fail
  silently. The app now says "GitHub sign-in expired" with a Reconnect button, retires the
  sign-ins it replaces so that GitHub does not drop the one in use, and the phone no longer
  forgets its sign-in when the secure storage is briefly unavailable.
- A failed sync shows its reason.
- Counts of repeating todos no longer include every future occurrence.
- Typed dates follow the date format chosen in Settings.

## Compatibility

- Signed with the same key as 1.9.0: install it over 1.9.0 without uninstalling. The Windows
  installer updates an installed 1.9.0 in place.
- **Reconnect GitHub once on each device** after updating (Settings → Sync & backup → Reconnect).
  Your data and the sync password stay the same.
- The backup and sync format is unchanged; 1.9.0 and 2.0.0 devices sync with each other.
- A computer that already has data opens it with the password it remembers. If it did not
  remember one, it asks once more; with the app lock switched on it keeps asking, as before.
- Google Drive sync is still shown as coming soon.
