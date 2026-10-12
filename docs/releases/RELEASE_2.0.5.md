# Life Assistant 2.0.5

2.0.4 was not published; 2.0.5 includes everything listed in RELEASE_2.0.4.md.

## Fixed

- **Sync no longer brings back an old version and removes newer records.** After each sync the
  app removes old versions from the cloud, one at a time and newest first. When that stopped
  halfway, for example because the phone app was closed, an old version could look like an
  unmerged change from the other device. The next sync then merged it: records added since that
  old version were removed, and recent edits (todos ticked off, new due dates) were undone.
  Old versions are now removed oldest first and only together with everything before them, and
  on GitHub all of them in a single commit, so no other device ever sees the history half
  removed.

## New

- **Sync asks before a merge removes many records.** When changes from both devices are merged
  and the other device lacks more of this device's records than a limit, sync stops and asks
  which version to keep instead of removing them. The limit is in Settings → Backup & sync on the
  phone and Settings → Cloud sync on the PC: **Always ask**, **5** (the default), **10**, **20**,
  **50** records, or **Off**. Each device keeps its own choice. Emptying the trash on the other
  device does not count.

## Compatibility

- Signed with the same key as 2.0.3: install it over 2.0.3 or 2.0.4 without uninstalling.
- The backup and sync format is the same as in 2.0.2. Devices still on 2.0.3 keep syncing, but
  only devices on 2.0.5 have the fix, so update the phone and the PC.
