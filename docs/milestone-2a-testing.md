# Milestone 2A — durable identity and migration testing

This milestone changes the Room database from version 2 to version 3 without clearing app data.

## Upgrade test on the existing installation

Do **not** clear the app before this test.

1. On the Milestone 1A build, play a movie or episode for at least 70 seconds.
2. Confirm the Resume button appears.
3. Install the Milestone 2A debug APK over the existing app.
4. Launch the app and confirm playlists, bookmarks, recents, and Resume are still present.
5. Play the same item and confirm it resumes at approximately the saved position.

## Progress after catalogue refresh

1. Play a movie for at least 70 seconds and leave playback.
2. Open Playlists and synchronize the active Xtream playlist.
3. Reopen the same movie.
4. Confirm Resume still appears after the movie rows were refreshed.
5. Repeat with one series episode.

## Stable M3U favourites

Use two local M3U files containing the same channels in different orders. Give at least one channel a `tvg-id`.

1. Import the first file and bookmark a channel.
2. Replace or re-import the reordered file, then synchronize it.
3. Confirm the bookmark still points to the same channel instead of the same row number.

## Playlist deletion

1. Save progress, bookmarks, and recent items on a disposable playlist.
2. Delete that playlist.
3. Confirm another playlist still works and no content from the removed playlist appears in Resume, bookmarks, or recents.

## Useful logs

```powershell
adb logcat -s AndroidRuntime Room SyncRepository
```

A migration failure usually appears as a Room schema validation exception during app startup.
