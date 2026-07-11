# Milestone 3C — compact category picker and activation polish

Install this build over the existing application. Do not clear app data.

## Large-category playlist

Use a playlist with more than eight provider categories.

1. Open Live in portrait.
2. Confirm the top row stays short: Search, All, Bookmarks, Recent, and one Categories/current-category chip.
3. Tap Categories.
4. Confirm a vertically scrolling category picker opens.
5. Scroll deep into the list without horizontal chip scrolling.
6. Enter part of a category name in Filter categories.
7. Select a result and confirm the picker closes and that category loads.
8. Confirm the Categories chip now displays the selected category name.
9. Open the picker again and select another category.
10. Rotate the phone and confirm the selected category remains active.

Repeat the basic picker test on Movies and Series when those catalogues expose categories.

## Small-category playlist

Use a playlist with eight or fewer provider categories, when available.

1. Open Live in portrait.
2. Confirm the familiar direct horizontal category chips remain.
3. Confirm Search, All, Bookmarks, Recent, and every provider category are directly available.

## Activation screen

This can be reached after removing all disposable playlists, or after clearing data only when you intentionally want to repeat onboarding.

1. Confirm the MAC address and device key appear in separate readable cards.
2. Tap the MAC card and confirm the copied message appears.
3. Paste into a notes app and confirm the value is correct.
4. Repeat with the device key.
5. Confirm both activation buttons fill the available width on a portrait phone.
6. Confirm Add my own playlist still opens playlist setup.
7. In forced TV mode, confirm D-pad focus can reach both code cards and activation buttons.

## Regression checks

- Existing playlists, bookmarks, and recent channels remain.
- Live search still works.
- Live playback opens and returns to the selected category.
- Wide layouts and forced TV mode still use the category rail.
