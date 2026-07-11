# Milestone 5B — VOD playback resilience and control polish

Install this branch over the existing application without clearing app data.

## Upgrade smoke test

1. Launch the app and confirm Home, Live, Playlists and Settings still open.
2. Confirm existing playlists, bookmarks, recents and managed policy remain present.
3. Play a Live channel to confirm Milestone 5A behaviour remains intact.

## Movie or episode test

This section requires at least one playable movie or episode. Skip unavailable checks rather than clearing or changing a working provider.

1. Start a movie or episode.
2. Confirm the title and episode number are readable and not clipped.
3. Confirm the bottom controls include Play/Pause, back 10 seconds and forward 10 seconds.
4. Tap or focus the progress bar and seek to another position.
5. Pause, leave playback and reopen with Resume.
6. Confirm Resume begins approximately ten seconds before the saved point for context.
7. Confirm a position shorter than 30 seconds does not create a misleading Resume state.
8. Seek close to the end or let the item finish; confirm the next opening starts from the beginning instead of offering a near-end resume.

## Recovery test

1. Start a playable movie or episode.
2. Disable networking while it is playing.
3. Confirm the player shows bounded recovery messages such as `Reconnecting…` or `Restoring playback…`.
4. Restore networking and confirm playback continues close to the previous position.
5. Repeat while leaving networking disabled.
6. Confirm automatic retries eventually stop and a usable Retry action appears instead of an endless spinner.
7. Restore networking and press Retry.

## Audio and subtitle persistence

This requires media with multiple audio or subtitle tracks.

1. Open the Audio dialog and select a non-default language.
2. Open another title that offers the same language and confirm it is selected automatically.
3. Select a subtitle language and repeat with another title.
4. Select Subtitles → Off, leave playback and open another title with subtitles.
5. Confirm subtitles remain off until explicitly enabled again.
6. In TV/remote mode, confirm each track dialog initially focuses the selected row rather than always focusing its first row.

## Phone and TV layouts

1. Check portrait and landscape phone layouts.
2. Confirm the transport buttons fit without clipping or covering the time counter.
3. Force Settings → Interface mode → TV / remote.
4. Navigate Play/Pause, ±10 seconds, the progress bar, Audio and Subtitles with a keyboard/controller.
5. Confirm focus outlines stay inside the viewport.
6. Return Interface mode to Auto afterward.

## Automated checks

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Unit tests cover resume rewind, short-progress suppression, completion normalization and bounded recovery delays.
