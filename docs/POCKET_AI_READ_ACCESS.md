# Pocket AI read access (Android)

Life Assistant on Android exposes a **read-only** `ContentProvider` so the user's own Pocket AI app
(`com.ced2711.pocketai`) can read todos, the ledger, notes and the diary. It never writes, and it never
reads the Vault or the Confessional (the queries only touch the todo, category, subtask, ledger, note,
note folder and diary tables).

Code: `android/src/main/java/com/ced2711/lifetracker/data/share/` (`ReadOnlyDataProvider`, `ReadOnlyQueries`).

## Who may read

All three must hold, or the query throws `SecurityException`:

1. **Signature permission** `com.ced2711.lifetracker.permission.READ_DATA` (`protectionLevel="signature"`).
   Only apps signed with the same key as Life Assistant get it: both apps must be release builds signed
   with the `taskledger.*` key from `GRADLE_USER_HOME/gradle.properties`. A debug-signed Pocket AI is refused.
2. **Caller package** is exactly `com.ced2711.pocketai` (so the `.e2e` build and any other app on the key are refused).
3. **The Settings switch** *Settings → Security → Let Pocket AI read your data* is on. It is **off by default**,
   device-local, and never synced or restored from a backup. When it is off the exception message says so,
   so Pocket AI can tell the user to turn it on.

## What Pocket AI adds to its manifest

```xml
<!-- Declared here too, so the grant works whichever app is installed first (both apps share the key). -->
<permission
    android:name="com.ced2711.lifetracker.permission.READ_DATA"
    android:protectionLevel="signature" />
<uses-permission android:name="com.ced2711.lifetracker.permission.READ_DATA" />

<!-- Android 11+ package visibility: lets Pocket AI see the provider. -->
<queries>
    <provider android:authorities="com.ced2711.lifetracker.readonly" />
</queries>
```

Without the duplicate `<permission>`, installing Pocket AI *before* Life Assistant leaves the permission
ungranted until Pocket AI is reinstalled.

## Calling it

Authority: `com.ced2711.lifetracker.readonly`. Use `ContentResolver.query(uri, null, null, null, null)`;
`projection`, `selection` and `sortOrder` are ignored. All parameters go in the URI query string.
Dates are `YYYY-MM-DD` and times `HH:mm` (24-hour) in the phone's time zone; "today" is the phone's today.
Amounts are USD, given both as a decimal string (`"12.50"`) and as integer cents.
Booleans are integers `0`/`1`. Missing values are `NULL`.

Errors: `SecurityException` (not allowed, see above), `IllegalArgumentException` (unknown URI or bad
parameter, with a readable message), `IllegalStateException` (Life Assistant is still finishing a restore
at startup: retry in a moment), `UnsupportedOperationException` for insert/update/delete.
A query can briefly block while Life Assistant's process starts, so call it off the UI thread.

### `content://com.ced2711.lifetracker.readonly/todos`

| Parameter | Values | Default |
|---|---|---|
| `view` | `open`, `today`, `overdue`, `upcoming`, `no_date`, `completed`, `all` | `open` |
| `days` | for `upcoming`: today through today + days (1–366) | `7` |
| `from`, `to` | optional due-date bounds (inclusive) | none |
| `search` | text in title, description or tags (case-insensitive; leading `#` ignored) | none |
| `limit` | 1–500 | `50` |

Deleted todos are never returned. Order: by due date, then due time, then priority (urgent first);
undated last. `completed` view: most recently completed first.

| Column | Type | Example |
|---|---|---|
| `_id` | integer | `42` |
| `title` | text | `Physics HW` |
| `description` | text | full text (often the same as title) |
| `due_date` | text / null | `2026-10-18` |
| `due_time` | text / null | `09:30` |
| `priority` | text | `none`, `low`, `medium`, `high`, `urgent` |
| `category` | text / null | `School / HW` |
| `tags` | text | `physics,hw` (comma-separated, may be empty) |
| `completed` | 0/1 | `0` |
| `completed_at` | text / null | `2026-10-18T14:03:00` |
| `repeats` | 0/1 | from a repeating series |
| `subtasks_done`, `subtasks_total` | integer | `1`, `2` |

### `content://com.ced2711.lifetracker.readonly/ledger`

| Parameter | Values | Default |
|---|---|---|
| `from`, `to` | date range (inclusive, at most ten years) | first of this month → today |
| `type` | `expense`, `income` | both |
| `search` | text in merchant, note or tags | none |
| `limit` | 1–1000 | `100` |

Newest first. Columns: `_id`, `type` (`expense`/`income`), `amount` (`"12.50"`), `amount_cents` (`1250`),
`date`, `time`, `merchant`, `note`, `tags`, `recurring` (0/1).

### `content://com.ced2711.lifetracker.readonly/ledger/summary`

Same `from`/`to`/`type`/`search` as `/ledger`. Exactly one row:
`from`, `to`, `income`, `expense`, `net` (decimal strings), `income_cents`, `expense_cents`, `net_cents`,
`entry_count`, `average_daily_spending` (expenses ÷ days in range), `currency` (`USD`).

### `content://com.ced2711.lifetracker.readonly/ledger/by_tag`

Same `from`/`to`/`search`. Expenses grouped by tag, largest first; an entry with several tags counts toward
each; untagged spending is `(untagged)`. Columns: `tag`, `amount`, `amount_cents`.

### `content://com.ced2711.lifetracker.readonly/notes` and `/notes/<id>`

| Parameter | Values | Default |
|---|---|---|
| `search` | text in title or body | none |
| `full` | `1` to include `body` in the list | body is `NULL` |
| `limit` | 1–500 | `50` |

Pinned first, then most recently updated. `/notes/<id>` returns that one note with `body` filled.
Columns: `_id`, `title`, `folder` (`Parent / Child` or null), `pinned` (0/1), `updated` (`2026-10-18T14:03:00`),
`preview` (first 160 characters, newlines as spaces), `body`.

### `content://com.ced2711.lifetracker.readonly/diary`

`date` for one day, or `from`/`to` (default: the last seven days including today). Oldest first.
Columns: `date`, `text`.

## Example (Kotlin, in a React Native native module)

```kotlin
val uri = Uri.parse("content://com.ced2711.lifetracker.readonly/todos?view=upcoming&days=7")
context.contentResolver.query(uri, null, null, null, null)?.use { c ->
    while (c.moveToNext()) {
        val title = c.getString(c.getColumnIndexOrThrow("title"))
        val due = c.getString(c.getColumnIndexOrThrow("due_date"))
    }
}
```

Quick check from a PC (the shell user lacks the permission, so this must be **refused**):
`adb shell content query --uri content://com.ced2711.lifetracker.readonly/todos`
