# Milestone 3D — adaptive playlist and settings testing

Install this branch over the existing app. Do not clear app data.

## Playlist management on a touch phone

1. Open **Playlists** in portrait.
2. Confirm **Add playlist** uses the available width.
3. Confirm **Import M3U** and **Refresh portal** fit below it without clipping.
4. Confirm each playlist card shows active state, type, ownership and last synchronization.
5. Confirm **Use**, **Sync**, **Edit** and **Remove** are available directly on personal playlist cards.
6. Confirm managed playlists do not expose **Edit**.
7. Remove a disposable playlist and confirm the confirmation dialog explains the local data that will be removed.

## Playlist editor validation

1. Open **Add playlist**.
2. Press **Save and synchronize** while required fields are empty.
3. Confirm each missing field gets an inline red error.
4. Enter a malformed server or M3U address and confirm the URL-specific warning.
5. Confirm **Test connection** stays on one line in portrait and landscape; it must not split a final letter onto another line.
6. Enter valid Xtream details and use **Show/Hide** for the password.
7. Press **Test connection** and then **Save and synchronize**.
8. Edit the saved playlist and confirm encrypted server, username and password values are still available.
9. Repeat basic validation with an M3U URL.
10. Confirm a local imported M3U can be renamed without exposing an editable internal file path.

## Settings on touch devices

1. Open **Settings** in portrait.
2. Confirm Interface, Live playback, Storage and synchronization, Subtitle appearance, and This device appear as separate cards.
3. Change every Interface, stream-format and subtitle option.
4. Confirm selected options use a compact outline and dot without scaling, glowing or overlapping neighbouring options.
5. Confirm the same stable option size in Auto, Touch and TV / remote modes.
6. Confirm the Live playback Auto label is compact; the fallback explanation remains in the card description.
7. Scroll to the bottom and confirm the bottom navigation does not cover the final device information.
8. Rotate to landscape and confirm both columns scroll independently.

## TV / remote mode

1. Select **Settings → Interface mode → TV / remote**.
2. Navigate playlist cards and open their action dialog with OK/Enter.
3. Navigate every playlist editor field, including password Show/Hide.
4. Confirm focused settings options receive a clear two-pixel outline but do not grow into adjacent choices.
5. Confirm focus can move through both Settings columns.
6. Return Interface mode to **Auto** after testing.
