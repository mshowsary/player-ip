# Milestone 5A — TV focus safety and live playback resilience

Install this branch over the current debug app without clearing data.

## TV focus safety

Force `Settings → Interface mode → TV / remote` on the phone.

### Home

1. Open Home in portrait and landscape.
2. Confirm the bright focus outline and glow are fully visible on every side of the focused card.
3. Move focus to cards on the first row and the last row.
4. Confirm neither the top nor bottom edge is clipped by the screen or header area.
5. In portrait, confirm the available cards are balanced in the remaining space rather than being crowded against the top with a large unused area below.
6. Apply the Live-only debug policy and repeat with the reduced card set.

### Playlists

1. Open Playlists in portrait and landscape.
2. Move focus to the first playlist and the final playlist.
3. Confirm their complete focus outlines and glows remain visible.
4. Scroll through the list and confirm the focused row is brought fully into view.
5. Open and close the TV playlist action dialog.

Restore `Interface mode → Auto` after the test.

## Normal live playback

1. Open Live TV and start a working channel.
2. Confirm the channel starts normally.
3. Zap repeatedly with Up/Down or swipe vertically on touch.
4. Confirm channel changes happen in order and the player does not jump back to an earlier channel.
5. Leave the app and return; confirm the stream restores.

## Connection recovery

The easiest physical test is to interrupt networking while a channel is playing.

1. Start a working Live channel.
2. Disable Wi-Fi and mobile data.
3. Confirm the player shows a small `Reconnecting…` message instead of immediately displaying a permanent failure.
4. Re-enable networking within several seconds.
5. Confirm playback recovers automatically when the provider stream is available.
6. Leave networking disabled long enough to exhaust retries.
7. Confirm a clear error screen eventually appears with a Retry action; the app must not spin forever.
8. Re-enable networking and press Retry.
9. Confirm playback resumes.

## Alternate stream fallback

With Live format set to `Auto`, the player retries the current source briefly and then tries the alternate HLS/MPEG-TS source. Providers differ, so this may not be visually distinguishable on every playlist. The UI may briefly show `Trying alternate stream…`.

## Automated tests

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The unit tests verify bounded retry delays, source fallback and terminal exhaustion.
