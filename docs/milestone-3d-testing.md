# Milestone 3D — playlist and settings UX testing

Install this branch over the existing application. Do not clear app data.

## Playlist management on a phone

1. Open **Playlists** in portrait.
2. Confirm **Add playlist** is full width and Import/Refresh fit below it.
3. Confirm each playlist is displayed as a readable card with type, ownership, sync state, expiry information when available, and an ACTIVE label for the current playlist.
4. Confirm the card exposes direct touch actions without first opening another menu.
5. Test **Use**, **Sync**, **Edit**, and **Remove** on a disposable playlist.
6. Confirm Remove still asks for confirmation.

## Playlist editor

1. Add an Xtream playlist.
2. Press **Save and synchronize** with empty fields and confirm inline errors appear.
3. Enter a server without `http://` or `https://` and confirm the URL error appears.
4. Enter valid details and use **Show/Hide** on the password field.
5. Press **Test connection** and confirm it succeeds.
6. Save and confirm the playlist synchronizes.
7. Edit the same playlist and confirm all encrypted values still appear correctly.
8. Add an M3U URL and confirm its validation and save flow.
9. Import a local M3U file and confirm editing only permits renaming it.

## Settings on a phone

1. Open **Settings** and confirm the page has clearly separated cards.
2. Change Interface mode, Live stream format, subtitle size/color/background/edge and confirm each selection updates immediately.
3. Confirm the subtitle preview updates.
4. Run **Re-sync active playlist** and confirm its current status is shown.
5. Clear the image cache and confirm the temporary success label.
6. Confirm the device information card remains readable in portrait and landscape.

## TV / remote simulation

Set `Settings → Interface mode → TV / remote`.

1. Open Playlists and navigate cards with a D-pad/keyboard.
2. Press OK on a playlist and confirm the focused action dialog opens.
3. Open the editor and navigate between fields, the password Show/Hide control, Test, Save, and Cancel.
4. Open Settings and confirm focus starts on the first Interface option.
5. Navigate through both settings columns and confirm the focus ring is always visible.
6. Return Interface mode to Auto after testing.
