# Milestone 10D — Home hub layout variants test plan

Two switchable Home arrangements so both testers can compare on real devices
and pick the product default (which can later become a brand-pack option):

- **Classic grid** — the current adaptive grid of equal cards (unchanged).
- **Hero** — a dominant Live TV panel with the other sections in a small grid
  beside it (below it on narrow phones): the familiar IPTV look.

Switch live under **Settings → Interface → Home layout**; the hub restyles
immediately, and the choice persists across restarts.

## Branch position

```text
develop (…, 10B UI consolidation)
└── milestone/10d-home-hub-layouts
```

## Checks (each layout, phone + TV/remote mode)

1. Settings → Interface → Home layout: switching updates Home instantly;
   relaunching the app keeps the choice.
2. Every section still opens (Live, Movies, Series, Playlists, Settings); the
   managed-access filtering still hides blocked sections in ALL layouts (use
   the debug policy presets in Settings → Managed access to verify Hero picks
   the first *available* section as its big panel when Live is blocked).
3. TV: initial focus lands on the hero/first element; D-pad reaches every
   card without traps; focus scale never clips at screen edges.
4. Phone portrait: Hero stacks (big panel on top, pairs below) and scrolls;
   Rows fit without horizontal overflow; landscape and tablet look sane.
5. Classic layout is pixel-identical to the previous Home.

## Approval record

```text
Tester 1 approved: <SHA>
Tester 2 approved: <same SHA>
CI passed:         <same SHA>
```

## Recovery / rollback

Preference-only feature (default Classic); no schema changes.
