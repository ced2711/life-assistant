# Life Assistant 2.0.2

## New

- **A daily checklist on Today.** For the things done every day, such as brushing your teeth,
  showering or checking homework. They are not todos: they never appear in Todo, the calendar or
  the widget. Ticks count for the day only, so every morning starts with nothing ticked. Once
  everything is ticked the checklist folds into one line until tomorrow. Add, rename, reorder and
  remove items in **Edit**. The checklist is part of backups and GitHub sync, so every device
  shows the same items and today's ticks.
- On the PC, AI assistants connected through MCP can read the checklist, tick items and add or
  remove them (removing asks first, like other deletions).

## Changed

- **Today is in the menu again by default** and a new device opens on it. It also comes back on
  devices that updated from 2.0.1, where it was off unless switched on. You can still hide it in
  Settings → Appearance → modules in the menu.

## After updating

- Install 2.0.2 on **every** device before the next sync. The backup and sync format has a new
  version for the checklist; older versions refuse to read it rather than lose the checklist, so a
  device still on 2.0.1 stops syncing until it is updated.

## Compatibility

- Signed with the same key as 2.0.1: install it over 2.0.1 without uninstalling.
- Existing data is kept; the database gains two tables for the checklist.
