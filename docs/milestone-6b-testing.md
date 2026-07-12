# Milestone 6B — production hardening test plan

Install over the existing debug application. Do not clear app data for the primary test.

## Upgrade regression

1. Launch NovaPlay and confirm it does not crash.
2. Confirm existing playlists, bookmarks, recents, policies, and settings remain.
3. Open Live TV and play a channel.
4. Edit and test a personal playlist.
5. Run a manual synchronization and confirm the previous catalogue remains available if networking is disabled.
6. Confirm Movies and Series screens still open normally.

## Build and security status

1. Open `Settings → Sync & health`.
2. Scroll to **Build and security**.
3. Confirm the installed test build reports `Debug`.
4. Confirm the portal status says the mock is debug-only and not included in release.
5. Confirm application-data backup is disabled.
6. Confirm HTTP playlists remain supported while the provider portal is described as HTTPS-only.
7. Copy support information and confirm it contains the status summaries but no portal hostname, playlist URL, username, password, token, MAC address, device key, or Device ID.

## Fresh debug-install behavior

This milestone removes committed provider assignments from the mock source set. This check is optional because it clears local app data.

1. Record any personal playlist details you need before testing.
2. Clear the debug installation.
3. Launch NovaPlay.
4. Confirm the activation experience opens without crashing.
5. Confirm debug pairing can approve but does not silently inject a committed provider account.
6. Choose **Add my own playlist** and confirm personal Xtream/M3U setup remains available.

## Release build verification

CI now builds the minified release variant and runs release-specific unit tests. A developer can also run:

```powershell
$env:NOVAPLAY_PORTAL_BASE_URL = "https://portal.your-domain.example"
.\gradlew.bat :app:testReleaseUnitTest :app:assembleRelease :app:lintRelease
```

The resulting release APK is unsigned. It is intended only to verify release compilation, shrinking, manifest merging, and mock-data exclusion until private signing is configured.

## Debug StrictMode

During ordinary testing, inspect Logcat for `StrictMode` warnings after browsing, playback, synchronization, rotation, and app background/foreground transitions. The debug policy logs accidental main-thread networking, leaked activities, and leaked closable objects without crashing the app.
