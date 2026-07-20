# Milestone 14B — clear history + 12/24-hour time

Two small IBO-parity conveniences.

## What changed

- **Time format** (Settings → Interface): Auto / 12-hour / 24-hour. Auto
  follows the Android system's 24-hour setting. Applies live to the Home
  clock, the programme times in the live player overlay, and the
  playlists screen's "Synced …" stamp. Pure `TimeFormatPolicy` with JUnit
  tests; the resolved choice is provided app-wide from the root.
- **Clear viewing history** (Settings → Storage and synchronization):
  empties the Recently-viewed rows of the active playlist across Live,
  Movies and Series — the "Recent" category chips and the Home rail react
  immediately. Bookmarks and resume positions are deliberately kept.

## Checks

1. Settings → Interface → Time format → 12-hour: Home clock shows AM/PM;
   open a live channel with guide data — programme times show AM/PM too.
   24-hour flips everything back instantly. Auto matches the box setting.
2. Watch two channels, confirm both appear in Live → Recently Viewed and
   on Home. Clear viewing history in Settings → both places empty; a
   bookmarked channel stays bookmarked and a half-watched movie still
   offers Resume.
3. No schema change; install over existing data.
