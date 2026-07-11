# Milestone 4B — secure pairing activation UI

Milestone 4B replaces the legacy MAC-and-key-first activation experience with a short-lived one-time pairing code. The app still supports personal Xtream and M3U sources.

## Install

Install over the existing application first to verify no regression:

```powershell
.\gradlew.bat :app:installDebug
```

To exercise the activation screen itself, clear the debug app only after the upgrade smoke test:

```powershell
adb shell pm clear com.novaplay.tv
adb shell am start -n com.novaplay.tv/.MainActivity
```

## Upgrade smoke test

1. Existing playlists remain available after installing over the previous build.
2. Live TV, movies and series still browse and play.
3. Personal playlist editing, testing and synchronization still work.
4. Settings and forced TV/remote focus remain stable.

## Pairing screen — phone touch mode

1. Clear app data and launch.
2. Confirm the screen presents a secure one-time code rather than asking the user to copy a MAC address and permanent key.
3. Confirm the code, portal address, expiry countdown and three numbered instructions are readable.
4. Tap the code and portal address; verify each copies and shows a short confirmation.
5. Tap **Check now** and verify the button changes to a checking state without duplicating navigation.
6. Tap **Create a new code** and confirm the old code is replaced and the countdown restarts.
7. Tap **Add my own playlist** and verify the existing personal playlist flow opens.
8. Rotate between portrait and landscape and confirm the layout changes from stacked cards to a balanced two-column arrangement without clipping.

## Pairing screen — TV / remote mode

1. Force **TV / remote** in Settings, then clear data and relaunch, or use a TV emulator/device.
2. Confirm one focus selector is visible and begins on the primary action after the code is ready.
3. Navigate through code, portal address, Check now, New code and Add my own playlist using D-pad or keyboard.
4. Press Back only when expected; the screen must not create duplicate pairing sessions.
5. Touch a different item while TV mode is forced on a phone and confirm focus follows the touched item.

## Mock approval

Debug mock activation approves automatically after the polling interval. Confirm:

1. The code remains visible long enough to inspect.
2. The status changes from waiting to checking.
3. Managed playlists are attached once.
4. Navigation moves to Home once.
5. The first synchronization starts without blocking navigation.

## Failure and expiry behavior

For a build pointed at an unavailable portal:

1. The screen shows a friendly retry message without exposing endpoint details or secrets.
2. **Create a new pairing code** retries cleanly.
3. Personal playlist setup remains available.
4. Expired and denied sessions do not continue polling.
5. Approval with no assigned playlist changes to **Device connected** and keeps checking assignments.

## Automated checks

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Unit tests cover pairing-code protocol behavior, countdown formatting and portal-address presentation.
