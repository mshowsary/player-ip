# Milestone 2B — safe synchronization and protected credentials

## Upgrade test

Install this build over the current app without clearing data.

1. Launch the app and confirm it opens normally.
2. Confirm existing playlists remain present.
3. Open an existing Xtream playlist and confirm its server, username, and password can still be edited.
4. Press **Test** and confirm the account still connects.
5. Synchronize the playlist and confirm Live TV, Movies, and Series remain available as before.

The first launch converts existing plaintext provider values into Android Keystore-encrypted values in the same Room rows.

## Failed synchronization safety

1. Confirm a working playlist already has channels in the catalogue.
2. Temporarily disable Wi-Fi/mobile data or edit a disposable playlist to use an unavailable server.
3. Start synchronization and wait for the failure message.
4. Restore the valid server/network.
5. Confirm the previously synchronized catalogue was not erased.
6. Synchronize again and confirm it succeeds.

## M3U safety

1. Import or add a valid M3U source and synchronize it.
2. Confirm channels are visible.
3. Temporarily make the source unavailable, then synchronize.
4. Confirm the old channel list remains available after the failed refresh.
5. Restore the source and synchronize successfully.

## Storage inspection (optional)

On a debuggable build, Android Studio App Inspection should show `enc:v1:` values in the playlist `server`, `username`, `password`, and `url` columns instead of provider credentials.

## Useful logs

```powershell
adb logcat -s AndroidRuntime Room SyncRepository
```

Sync logs should never print a complete provider URL or password.
