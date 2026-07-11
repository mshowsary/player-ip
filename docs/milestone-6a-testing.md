# Milestone 6A — performance, background refresh, and diagnostics

Install over the existing app without clearing data.

## Upgrade smoke test

1. Launch NovaPlay and confirm it reaches Home without a crash.
2. Confirm existing playlists, bookmarks, recents, policies, and Live playback remain available.
3. Browse a long Live category and confirm logos continue loading smoothly.
4. Rotate between portrait and landscape and confirm Settings remains usable.

## Sync and health panel

1. Open **Settings**.
2. Select **Sync & health** in the lower-right corner.
3. Confirm the dialog scrolls and all controls remain readable.
4. Confirm the active catalogue counts approximately match the app.
5. Confirm Database, Image cache, Temporary snapshots, Free storage, Memory, and Network are displayed.
6. Press **Refresh health** and confirm the values update without a crash.
7. Press **Copy support info** and paste it into a text editor.
8. Confirm the copied report contains no playlist URL, server, username, password, token, MAC address, device key, or Device ID.

## Manual refresh diagnostics

1. In **Sync & health**, press **Re-sync active playlist**.
2. Wait for synchronization to complete.
3. Reopen or refresh the panel.
4. Confirm it shows a successful result, Manual trigger, duration, completion time, and catalogue counts.
5. Disable networking and run the refresh again.
6. Confirm the error is readable and does not expose credentials.
7. Confirm the previously installed catalogue remains available.

## Automatic refresh setting

Test each option:

- **Off** — background periodic work is cancelled.
- **Daily** — one constrained refresh is scheduled roughly every 24 hours.
- **Twice daily** — one constrained refresh is scheduled roughly every 12 hours.

Background work requires an unmetered connection, adequate battery, and adequate storage. Android may delay periodic work. It is not expected to run immediately during this short physical test.

Optional ADB inspection:

```powershell
adb shell dumpsys jobscheduler | Select-String "novaplay"
adb shell dumpsys activity service WorkManager
```

## Low-memory behavior

A low-RAM TV box receives a smaller image-memory cache and disables image crossfades. Ordinary phones and tablets receive a larger but still bounded cache. The app should not change visual layout based on this setting.
