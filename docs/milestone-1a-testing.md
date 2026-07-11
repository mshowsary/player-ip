# Milestone 1A — Personal playlist management

This branch adds the first usable personal-playlist flow while preserving portal-managed activation.

## Pull and install

```bash
git fetch origin
git switch milestone/1a-playlist-management
git pull
./gradlew :app:installDebug
```

For a clean onboarding test:

```bash
adb shell pm clear com.novaplay.tv
adb shell am start -n com.novaplay.tv/.MainActivity
```

## Test 1 — Xtream onboarding

1. On the activation screen, choose **Add my own playlist**.
2. Choose **Add** and keep **Xtream** selected.
3. Enter a name, server URL, username and password.
4. Press **Test** and confirm the account responds.
5. Press **Save**.
6. Confirm the catalogue synchronizes and the app opens Home.
7. Open Live, Movies and Series and verify content appears.

## Test 2 — M3U URL

1. Open **Playlists** from Home.
2. Choose **Add**, then **M3U**.
3. Enter a name and M3U URL.
4. Test and save it.
5. Confirm the new playlist becomes active and Live content appears.

## Test 3 — Local M3U file

1. Open **Playlists**.
2. Choose **Import M3U**.
3. Select a `.m3u` or `.m3u8` file with the Android system picker.
4. Confirm the file is copied into private app storage and synchronized.
5. Reboot the app and confirm the imported playlist still works.

## Test 4 — Management actions

For a personal playlist, verify:

- Set active
- Synchronize now
- Edit
- Remove

For a portal-managed playlist, verify **Edit** is not offered, while activation refresh and synchronization continue to work.

## Layout checks

Repeat the playlist screens in:

- Phone portrait
- Phone landscape
- Android TV emulator at 720p
- Android TV emulator at 1080p

Check that text fields accept touch/keyboard input and all buttons remain reachable.

## Expected temporary limitations

- Provider credentials are still stored using the existing Room model; Keystore encryption belongs to the next data-durability milestone.
- Imported files are app-private copies and cannot be edited in place.
- Android Share-target import is not included in this first slice.
- The current application still uses the existing landscape manifest policy; full adaptive window-size layouts are a later milestone.

Report the exact screen, action and error text for every failure. For playback problems, also capture:

```bash
adb logcat | grep -E "SyncRepository|ExoPlayerImplInternal|MediaCodec"
```
