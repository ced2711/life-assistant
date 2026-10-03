# Life Assistant 2.0.1

## Fixed

- **GitHub sync that disconnected an hour or two after connecting.** Since 2.0.0 a device that
  reconnected asked GitHub to revoke the sign-in it had used before. GitHub answered by ending
  the other sign-ins of Life Assistant for the same account as well, so reconnecting the phone
  disconnected the computer and the phone's own new sign-in, and the other way round. The apps no
  longer revoke anything: a replaced sign-in is simply forgotten on the device.
- Disconnecting on one device no longer signs the other device out.

## After updating

- Install 2.0.1 on **every** device before reconnecting. A device that still runs 2.0.0
  disconnects the others each time it reconnects or disconnects.
- Then choose **Reconnect** once on each device (Settings → Sync & backup). Your data and the
  sync password stay the same.

## Compatibility

- Signed with the same key as 2.0.0: install it over 2.0.0 without uninstalling.
- The backup and sync format is unchanged.
