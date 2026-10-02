# Life Assistant design

How the Android and desktop apps look and behave. Shared code: `android/src/main/java/com/ced2711/lifetracker/ui/theme/TaskLedgerTheme.kt` (colours, type, shapes) and `ui/design/LifeComponents.kt` (building blocks); the desktop build compiles both.

## Principles

1. **One glance.** Every screen answers its main question at the top: what is due, how much was spent, what happened that day. Details come after, never before.
2. **Calm.** Neutral surfaces and one accent colour the user picks. Other colours carry meaning only: green money in, red money out and overdue, orange/blue for priority. No decoration, no shadows; groups are separated by a step in surface colour.
3. **Few controls, in reach.** Adding something is always one obvious action (a quick-add field, or + on phones). Rarely used actions live in the editor or a menu, not on every row.
4. **Nothing is lost.** Text saves as you type. Deleting shows Undo; deleting things that cannot be undone asks first. Daily backups keep yesterday's data.
5. **Same app on both devices.** Same words, colours, order and rules; the desktop uses its room for side-by-side panes and the keyboard, the phone for thumbs (bottom navigation, sheets, swipes).

## Tokens

- **Spacing:** 4dp grid (`Space`): 4, 8, 12, 16, 20, 24, 32. Page padding: phone 16–20dp, desktop 28–32dp.
- **Shapes:** rows and fields 14dp, panels 20dp, pills fully round, sheets 28dp top corners.
- **Type:** page title `headlineLarge` (30sp bold). Section labels `labelLarge` semibold in the secondary colour. Body 16/14sp. Money and counts use tabular figures.
- **Surfaces (dark / light):** background `#0F1012` / `#F6F6F4`; panels `surfaceContainer`; inputs and selected segments `surfaceContainerLowest`/`High`.
- **Semantic colours:** `LifeTheme.colors` — income, expense (= danger), warning, priority urgent/high/medium/low, soft accent for selection and today.

## Navigation

- Modules: **Today**, Todo, Ledger, Calendar, Notes, Diary, Confessional (Diary and Confessional hidden by default; any module can be hidden; at least one stays).
- **Desktop:** left sidebar (232dp, folds to a 72dp rail with Ctrl+B or the button): app name, modules with counts, then sync status, Vault and Settings at the bottom. Ctrl+1…9 opens modules in order.
- **Phone:** bottom navigation with the visible modules (labels shown). The top of each page has the page title, sync status and the settings entry. Vault and Backup & sync open from Settings (and Vault from Notes).

## Patterns

- **Lists** are rows, not cards: round check mark (todos), title, one quiet line of details, and at most one trailing element (amount, time, chevron). Groups (Overdue, Today, Tomorrow, Later, No date) have small labels with counts.
- **Quick add** sits where the list starts: type and press Enter. Optional details (date, priority, category) are pills next to it, never required.
- **Editors:** desktop opens them in the right pane; the phone in a full-height sheet. Save with Ctrl+S / the Save button; Esc / back closes (asking only when something would be lost).
- **Segmented controls** switch views (Month/Week/Agenda, Entries/Statistics/Recurring, periods). **Pills** filter.
- **Empty states** say what the place is for and offer the one next step.
- **Confirmations** only for what Undo cannot fix (stopping schedules, removing stopped schedules, burning sealed confessions, replacing data with a backup, disconnecting sync).

## Platform notes

- Desktop: hover shows secondary actions, but every hover action is also reachable from the editor or a menu. Every page works at 1024px wide; side panels collapse into the main column below 1100px instead of disappearing.
- Phone: one-handed reach first; swipe a todo right to complete and left to delete (with Undo); long press opens the same actions as a menu.
