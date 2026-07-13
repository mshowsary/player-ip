# Milestone 9C — live player UX test plan

This milestone makes the live player self-sufficient for day-to-day zapping:
an in-player channel picker, previous-channel recall, direct digit zapping
inside the player, and persisted video scale modes. It contains no guide UI —
the grid guide (9B) was shelved by owner decision and stays out of the app.

## Branch position

```text
develop (integrated 1.0.0-rc.1 + CI-on-develop fix)
└── milestone/9c-live-player-ux
```

## What changed

- **Channel picker in the player**: D-pad LEFT (TV) or the list button in the
  overlay (touch) opens a left-side panel with the current category's channels
  (paged, current channel highlighted). Selecting zaps immediately; LEFT/BACK
  or tapping outside closes it.
- **Previous-channel recall**: typing a lone `0` on the remote swaps back to
  the previously watched channel (classic recall); touch has a swap button in
  the overlay.
- **In-player digit zapping**: number keys buffer digits (shown top-right) and
  after ~1.4 s zap to that channel number within the current category; numbers
  beyond the lineup land on the last channel.
- **Video scale modes**: FIT → STRETCH → ZOOM, cycled with D-pad RIGHT (TV) or
  the aspect button (touch), confirmed by a brief on-screen label and
  persisted across app restarts.
- No schema change. New pure policy `PlayerDigitPolicy` with JUnit tests; the
  digit-key mapping is now shared between the Live browser and the player.

## Pull and verify the branch

```powershell
git fetch origin --prune
git switch --track origin/milestone/9c-live-player-ux
git rev-parse HEAD
```

## Build and automated checks

```powershell
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest
./gradlew :app:lintDebug :app:lintRelease
./gradlew :app:assembleDebug
```

New unit tests: `PlayerDigitPolicyTest`.

## Install-over-existing check — required

Install over the previous build; playlists, bookmarks, progress and settings
must survive. No Guide entry exists anywhere (this build is based on develop,
which never contained it).

## TV / remote checks (Xiaomi box or emulator D-pad)

1. Play a live channel from the mock server.
2. UP/DOWN still zap; OK still toggles the info overlay — unchanged behavior.
3. LEFT opens the channel panel; focus lands in the list; UP/DOWN move through
   channels, OK zaps to the selection and closes the panel; LEFT or BACK
   closes without zapping. BACK with the panel closed still exits the player.
4. Type `4` on the remote: after the digit indicator commits, playback zaps to
   channel 4 (Bein Sports 1). Type `99`: lands on the last channel.
5. Zap somewhere else, then type `0`: playback returns to the previous channel.
6. RIGHT cycles Fit → Stretch → Zoom with the label flashing; kill and restart
   the app — the chosen mode is still applied.
7. Rapid key mashing (digits + UP/DOWN + LEFT) never crashes or interleaves
   zaps (the zap mutex serializes them).

## Touch checks (phone)

1. Tap during playback: the overlay now shows list / swap / aspect buttons
   next to the zap arrows.
2. The list button opens the panel; tapping a channel zaps; tapping the dimmed
   area closes it.
3. The swap button returns to the previously watched channel.
4. The aspect button cycles the scale with the label flash; swipe zapping and
   tap-to-toggle overlay still work.

## Regression

- HLS→TS fallback, reconnection and the error screen behave as before (stop
  the mock server mid-playback to check).
- Backgrounding stops the stream; returning restores it.
- French and Arabic: new labels render correctly; RTL panel remains usable.

## Approval record

```text
Tester 1 approved: <SHA>
Tester 2 approved: <same SHA>
CI passed:         <same SHA>
```

## Recovery / rollback

No database changes; reverting to the previous build has no data impact.
