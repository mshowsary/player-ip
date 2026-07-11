# Milestone 3A — adaptive app shell testing

Install this build over the existing app without clearing data.

## Phone portrait

1. Open the app in portrait.
2. Confirm the system status/navigation bars are visible outside video playback.
3. Confirm a bottom navigation bar shows Home, Live, Movies, Series, and Settings.
4. Open each destination and confirm the selected tab is highlighted.
5. Return to Home and confirm cards are large enough to tap and do not clip.
6. Open Playlists from the Home card and return with Back.

## Phone landscape

1. Rotate the phone to landscape.
2. Confirm the app stays open and does not lose the selected playlist.
3. Confirm the navigation changes to a side rail when enough width is available.
4. Confirm Live categories and channel content remain usable.
5. Rotate back to portrait and confirm the bottom navigation returns.

## Interface override

1. Open Settings → Interface mode.
2. Select **TV / remote**.
3. Confirm the touch bottom/side navigation disappears and the app becomes immersive.
4. Confirm the Home Live TV card receives focus when using a keyboard/D-pad.
5. Return to Settings and select **Touch**.
6. Confirm the touch navigation shell and system bars return.
7. Select **Auto** before finishing.

## Video playback

1. Start a Live channel from touch mode.
2. Confirm playback is immersive with system bars hidden.
3. Leave playback and confirm system bars return.

## Resizing / split screen where supported

1. Put the app into split-screen or resize its emulator window.
2. Confirm navigation and Home cards respond to the current app window rather than the physical display.
3. Confirm no controls are clipped at compact, medium, or expanded widths.

Useful logs:

```powershell
adb logcat -s AndroidRuntime NovaPlay
```
