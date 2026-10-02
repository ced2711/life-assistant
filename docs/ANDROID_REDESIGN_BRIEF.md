# Android UI rewrite: rules for everyone working on a screen

The Android UI is being rewritten screen by screen on the new design (`docs/DESIGN.md`). The desktop app is already done and is the reference for look and wording: read the matching page in `desktop/src/main/kotlin/com/ced2711/lifetracker/desktop/` before you start (for example `DesktopLedgerPage.kt` for the Ledger).

## What "rewrite" means

- Replace the composables of your screens completely. Do not keep the old layouts.
- Keep every feature and behaviour of the old screen (see the feature list at the end, and read the old code before deleting it). When the desktop page has something the phone lacked and it makes sense on a phone, add it.
- Keep the data layer: view models, repositories, DAOs, workers, validation helpers and their unit tests stay. You may add small things to a view model when a screen needs them.
- Keep the public signature of the screen's entry composable (the one `MainActivity.kt` calls), so `MainActivity.kt` keeps compiling. If you really must change it, change the call in `MainActivity.kt` too and say so in your report.

## Structure of a screen

```kotlin
@Composable
fun LedgerScreen(viewModel: TaskLedgerViewModel, …) {      // thin: collects flows, owns dialogs/snackbars
    val entries by viewModel.ledgerEntries.collectAsState()
    LedgerContent(entries = entries, …callbacks…)
}

@Composable
internal fun LedgerContent(entries: List<LedgerEntryEntity>, …, onSave: (LedgerDraft) -> Unit) { … }  // stateless
```

The stateless `…Content` composables are what gets rendered for review, so they must not need a view model, a database or Android services.

## Building blocks (use these, do not invent parallel ones)

Shared with the desktop, in `android/src/main/java/com/ced2711/lifetracker/ui/design/LifeComponents.kt`:
`Space` (4dp grid), `SectionLabel`, `Panel`, `ListRow`, `CheckCircle`, `priorityColor`, `Dot`, `MoneyText`, `Segmented`, `Pill`, `Tag`, `Stat`, `EmptyState`, `RowDivider`, `IconTile`, `ProgressLine`, `ReadableWidth`, `LifeTextField`.

Android only, in `ui/components/LifeAndroid.kt`: `EditorSheet` (the full-height editor with Close and Save), `FieldLabel`, `ConfirmDialog`, `SearchField`, `DatePickerButton`, `TimePickerButton`. Dialogs must use `HingeSafeDialog` / `HingeSafeAlertDialog` (these do), never a bare `Dialog` or `AlertDialog`, so they stay off a fold's hinge.

Colours: `MaterialTheme.colorScheme` and `LifeTheme.colors` (`income`, `expense`, `danger`, `warning`, `accentSoft`, `divider`, priority colours). Never hard-code a colour. Type: `MaterialTheme.typography`. Shapes: `MaterialTheme.shapes`.

`ui/today/TodayScreen.kt` is the worked example of the pattern.

## Design rules for the phone

1. The top bar (not yours) already shows the page name, sync status and Settings. Do not repeat the page title inside the content.
2. One glance: the most important information first (what is due, the period's totals, the selected day). Controls that are rarely needed go into the editor or a menu, not onto every row.
3. Lists are rows (`ListRow`), not cards. One quiet supporting line per row, at most one trailing element.
4. Adding: a quick-add field at the top where it makes sense, and a floating action button (bottom end, `FloatingActionButton` with `containerColor = MaterialTheme.colorScheme.primary`) for "new with all details".
5. Editing: `EditorSheet`. Fields are `LifeTextField` under a `FieldLabel`; choices are `Pill`s or a `Segmented`.
6. Filters and view switches: `Segmented` for views (two to five choices), horizontally scrolling `Pill`s for filters.
7. Deleting shows an Undo snackbar where the data layer supports undo (todos, ledger entries, attachments). Anything that cannot be undone asks first with `ConfirmDialog(destructive = true)`.
8. Empty places use `EmptyState` with one sentence saying what to do.
9. Touch targets at least 48dp. Every icon-only button has a `contentDescription`. Section titles are headings (`SectionLabel` does this).
10. `isWide == true` (tablets, unfolded foldables): two panes, list on the left and the open item or details on the right, like the desktop page. Content never stretches wider than about 760dp for reading.
11. Small phones (320dp wide) and large fonts (1.5×) must not clip or overlap: let text wrap, use `FlowRow` for groups of pills and buttons, make long pages scroll.
12. Text people type is saved without asking where the desktop does so (notes, diary). Never lose typed text on Back.

## Text and translations

- Every fixed text goes through `localizedText("English text")` (or `translateUiText(text, language)` outside composition). English is written in the code.
- Add the Simplified Chinese translation of every new text to your area's file `ui/localization/Zh<Area>.kt` (a plain `mapOf("English" to "中文")`). Do not edit `UiLocalization.kt`. Reuse the desktop's wording: the same English text is very likely already translated, and then nothing needs adding.
- For texts with numbers or names, write a small function with a `when (language)` in your own file instead of gluing translated pieces together.
- Dates are shown with `UserFormatting` in the user's format. Typed dates are read with `SmartDateParser.parse(text, today, settings.dateFormat, locale)`, which also understands "tomorrow", "fri", "周五"; offer `DatePickerButton` next to the field.

## Reviewing your own work (required)

Add scenes for your screens to your file in `android/src/test/java/com/ced2711/lifetracker/ui/render/` (for example `LedgerScenes.kt`), using `RenderSamples` for data. Cover: the normal screen, the empty screen, each tab or view, the editor open, and one confirmation. Then render and look at every picture:

```bash
./gradlew --no-daemon -q :android:testStandardDebugUnitTest --tests "*ScreenRenderTest.phone" -Dscreens.dir="<absolute folder>" -Dscreens.only=<part of your scene names>
```

Devices: `phone`, `smallPhone`, `landscapePhone`, `tablet`, `foldBook`, `foldTabletop`, `largeFont`. Add `-Dscreens.theme=light -Dscreens.language=zh` for the light Chinese version. Look at the PNG files with the Read tool. Fix what is not right and render again: clipped or overlapping text, uneven spacing, something important below the fold, a control that is hard to reach, English left in the Chinese version. Do this until you would be proud of every picture. Check at least `phone` (dark, English), `phone` (light, Chinese), `smallPhone`, `tablet`, `foldBook` and `largeFont`.

Before you finish, all of these must pass:

```bash
./gradlew --no-daemon -q :android:compileStandardDebugKotlin :android:compileStandardDebugAndroidTestKotlin
./gradlew --no-daemon -q :android:testStandardDebugUnitTest
./gradlew --no-daemon -q :android:testStandardDebugUnitTest --tests "*TranslationCoverageTest*" -Dtranslations.area=ui/<your folder>
```

Instrumented tests in `android/src/androidTest` that use your composables must keep compiling; update them when you rename things, and keep what they check true.

## Working rules

- Stay inside your files (listed in your task), your `Zh<Area>.kt`, your `<Area>Scenes.kt`, and small additions to `TaskLedgerViewModel.kt`. Other people are rewriting the other screens at the same time; touching shared files causes conflicts. If a shared building block is missing something, add what you need as a private helper in your own file and mention it in your report.
- Commit your work on your branch with a clear message. Never add Claude or AI attribution to commits, comments or files. Do not push.
- Windows machine, Git Bash: use the Write tool for files (bash heredocs break on apostrophes and backslashes here). Portable Python is `/c/Users/cedri/agent-tools/python312/python.exe`. Never start an emulator and never control the screen.
- Code and comments in English. Comments say what the code is for, in plain words.

## Feature list to keep (per screen)

A/ = `android/src/main/java/com/ced2711/lifetracker/`

### Ledger (`A/ui/ledger/*`)
- Tabs Entries | Statistics | Recurring (a widget or calendar link forces Entries; `quickAddRequestToken`, `requestedEntryId` parameters must keep working: focus the amount field and show the keyboard / open that entry's editor).
- Money is USD, `formatMoney`, max 999,999,999.99, two decimals. Income green, expense red (`LifeTheme.colors`).
- Quick entry: Expense/Income, amount with a decimal keypad, Save (today, now), Details (opens the editor prefilled).
- Entries list newest first; row: merchant, else note, else type; date and time; #tags; signed amount; repeat and attachment marks; tap opens the editor; delete with Undo snackbar (6 seconds).
- New on the phone (as on the desktop): a period (Week, Month, Year, All) with previous/next, the period's income, expense and net at the top, search in merchant, note and tags, entries grouped by day with the day's net.
- Editor: type, amount, date (text + picker), time (text + picker), merchant, tags (suggest existing tags), note, repeat (unit, every N, optional end date; an entry of an existing schedule cannot change its schedule here; an existing entry can only start repeating today or earlier), attachments (10 files, 25 MB each, 128 MB total; open, remove with Undo; a future recurring entry cannot have files yet), validation messages, retry after a failed receipt copy without creating a duplicate.
- Statistics: period Week/Month/Year/Custom (start and end with pickers), Income, Expenses, Net, trend bar chart with selectable bars and accessibility actions, income/expense ratio, largest expense, average daily spending, and spending by tag (as on the desktop).
- Recurring: schedules with label, amount, Active/Stopped, frequency, start/end, tags; active: Edit rule (replacement starts tomorrow or later; generated entries use 00:00) and Stop (now asks first); stopped: Delete after confirmation (entries stay).

### Calendar (`A/ui/calendar/CalendarScreen.kt`)
- Header: previous/next, title, Today, views Month | Week | Day | Agenda.
- Month grid (first weekday from settings); each day: number, diary dot (when the Diary module is shown), the day's net amount, and its todos (titles when there is room, otherwise counts of open and done); selected day and today clearly marked; accessible summary per cell; tapping selects.
- Week view, Day view, Agenda (overdue first, then the coming 30 days, as on the desktop).
- Day details: weekday, date, net; Diary row (preview, Open diary / Write diary → `onOpenDiary`); todos (tap → `onOpenTodo`); ledger entries (tap → `onOpenLedgerEntry`).
- New on the phone (as on the desktop): tick todos off in the day details, and "Add a todo for this day" quick add.
- Showing a range creates the repeating todos that fall in it (`materializeCalendarTodoOccurrencesThrough`), keep that.
- Wide: calendar on the left, the selected day's details on the right.

### Notes, Diary, Confessional (`A/ui/notes`, `A/ui/diary`, `A/ui/confessional`)
- Notes: search (title and body); scopes All notes, Pinned (new), Unfiled and the folder tree (create inside the selected folder, rename, delete with confirmation: notes move to Unfiled, child folders move up); list pinned first then last changed, with title, two-line preview, date and folder; editor with title (derived from the first line when empty), folder, pin, body, attachments (a new note is saved first, then the picker opens; open; remove with Undo), delete with confirmation; the lock icon opens the Vault (`onOpenVault`).
- Notes must now save themselves about 700 ms after typing stops and when leaving (as the desktop and the Diary do), with a quiet "Saved" state. Back must never lose text.
- Diary: previous/next day, date with weekday, Today, delete with confirmation; one page per day, autosave after 700 ms and on leaving; clearing the text removes the page; list of pages newest first with previews; `requestedEpochDay` opens that day; maximum 1,000,000 characters.
- Confessional: explanation; text up to 20,000 characters kept in memory only; Burn (the ember animation, 1.4 s, then "Burned. It's gone."); Seal on this device; sealed confessions: Open (device authentication; none when the device has no screen lock), Hide, Burn all (confirmation), burn one (now asks first). Screenshots stay blocked (handled outside the screen).

### Vault (`A/ui/vault/VaultScreen.kt`; keep `VaultViewModel.kt` logic)
- States: locked (Unlock or Create vault; Open Android security settings when there is no screen lock; Reset vault), unlocking, error (Try again or security settings; Reset), unlocked.
- Unlocked: search, offers (Enable fingerprint unlock, Upgrade vault security), entries, Lock and Add; empty states; Reset vault at the end.
- New on the phone (as on the desktop): tapping an entry opens a reading view with Account, Password (hidden, Show/Hide) and Website, each with Copy, and the notes; Edit and Delete from there.
- Editor: label, account, password (show/hide; read-only while shown; hidden from accessibility), website (URL keyboard), notes; at least one field; Save; Delete with confirmation.
- Copy tells that the clipboard clears after 30 seconds. The Vault locks after 60 seconds without touch and when leaving. Reset asks for authentication.

### Settings, Backup & sync, app lock (`A/ui/settings`, `A/ui/backup/BackupRestoreScreen.kt`, `A/ui/lock/AppLock.kt` screen part)
- Settings rows grouped as on the desktop: Sync & backup (opens Backup & sync), Appearance (theme System/Light/Dark, accent with colour swatches, language, modules in the menu with at least one shown), Dates and times (week start, time format, date format shown as example dates), Todo (quick add fields), Reminders (notifications switch with the Android 13 permission, default reminders with custom offsets, all-day reminder time with picker), Security (Password vault, App lock with device authentication to switch, lock after leaving), About (version, licence, notices, View source).
- Backup & sync: cloud card (Connect GitHub; Google Drive coming soon; connect dialog with sync password twice, minimum 8; GitHub code dialog with the code copied, Copy, Open GitHub, progress, Cancel), connected state (automatic sync switch, last sync, attention messages including "GitHub sign-in expired" and the reason of a failed sync from `lastError`, Sync now or Reconnect, Disconnect now after confirmation), conflict dialog (wording must not say Google Drive when GitHub is used), local sync recovery copies with Export, export an encrypted backup (location, then password twice), restore from a backup in four steps (password, review counts, confirm replace, progress), personal migration card when the build has one.
- New: Daily backups (the app keeps yesterday and the day before, see `data/backup/DailyBackups.kt` and `AppContainer.dailyBackups`): list the copies with their day, Restore after confirmation (the Vault is kept), and Undo the last restore.
- App lock screen: icon, text, error, Unlock; the prompt opens by itself; Continue with a warning when the device has no screen lock.
