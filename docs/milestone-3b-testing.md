# Milestone 3B — responsive Live, Movies, and Series testing

Install this branch over the existing application. Do not clear app data.

## Portrait phone

1. Open Live TV.
2. Confirm the screen title and current category are readable.
3. Confirm categories appear as a horizontal chip strip.
4. Scroll channels and verify rows are comfortable to tap.
5. Tap the bookmark icon near its edges; the target should still respond.
6. Search for a channel, close search, and verify the previous browser remains usable.
7. Open Movies and Series.
8. Confirm poster cards are not tiny and normally form about two or three columns, depending on phone width.
9. Confirm bookmark targets are easier to tap and do not open the poster accidentally.

## Landscape phone

1. Rotate while Live, Movies, or Series is open.
2. Confirm the selected category and scroll state do not reset merely because of rotation.
3. On sufficiently wide windows, confirm category chips become a fixed side rail.
4. Confirm the rail does not consume 30 percent of very wide screens.
5. Confirm the catalogue uses additional columns without stretching individual posters.

## TV simulation

1. Open Settings and choose **TV / remote** interface mode.
2. Open Live TV and verify the category rail is visible.
3. Navigate the rail and channel list with a keyboard or controller D-pad.
4. Long-press Enter/OK on a channel and confirm its bookmark changes.
5. Open Movies or Series and confirm posters remain large and focus is clearly visible.
6. Long-press a poster to toggle its bookmark.
7. Return Settings to **Auto** after testing.

## Playback regression

1. Start a Live channel from the redesigned list.
2. Exit playback and confirm the selected category is preserved.
3. Confirm the channel list remains responsive and no duplicate audio continues.
