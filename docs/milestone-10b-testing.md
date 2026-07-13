# Milestone 10B — UI consolidation test plan

Two phases on one branch: removal of the four dead first-generation screens
(zero behavior change) and app-wide touch feel (press dip + long-press
haptics) through the shared NovaClickable primitive.

## Branch position

```text
develop (…, 9C live player UX)
└── milestone/10b-ui-consolidation
```

## What changed

- **Deleted (unreachable since the adaptive rewrite)**: `LiveScreen`,
  `SettingsScreen`, `AdaptiveSettingsScreen`, the `PlaylistsScreen` composable
  — net −2,390 lines. `CategoryRail`, `CategoryChipsRow` and `SearchField`
  moved to `LiveBrowserComponents.kt` (same package); `PlaylistsViewModel`
  got its own file. No navigation entry, string or behavior changed.
- **Press feedback everywhere**: touching any button, card, channel row, chip
  or tile now dips it to 96% scale while pressed (with the existing tint).
  On TV, pressing OK slightly settles the focused scale as acknowledgement.
- **Long-press haptic**: bookmarking a channel by long-press (rows and remote
  long-OK on hybrid devices) now gives a haptic tick.

## Regression sweep (the deletions must be invisible)

1. Install over existing data; everything survives.
2. Live browser: categories rail/chips, search, digit jump, bookmarks — all
   behave exactly as before on phone and TV.
3. Movies and Series grids: category rail/chips and search still work (they
   share the moved components).
4. Playlists: add/edit/test/import/remove/set-active all work (the ViewModel
   moved files but did not change).
5. Settings: the settings screen and the Sync & health dialog are unchanged.

## Touch feel checks (phone)

1. Tap-and-hold any Home tile, channel row, chip or button: it visibly dips
   while pressed and springs back on release; releasing outside cancels.
2. Long-press a channel row to bookmark: a haptic tick accompanies the toggle.
3. TV/remote mode: focus scale/border/glow unchanged; pressing OK gives a
   subtle settle instead of the touch dip.

## Approval record

```text
Tester 1 approved: <SHA>
Tester 2 approved: <same SHA>
CI passed:         <same SHA>
```

## Recovery / rollback

No data or schema changes; reverting the build has no data impact.
