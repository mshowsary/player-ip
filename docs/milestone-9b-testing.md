# Milestone 9B — grid guide UI test plan

This milestone adds the grid TV guide on top of the 9A data layer: a frozen
half-day timeline with one lane per channel, a shared-scroll time ruler, a
focus-driven detail strip, and guide entries in the touch navigation and the
Home hub. It does not add catch-up playback (9C) or day paging.

## Branch position

```text
develop (integrated 1.0.0-rc.1: stack 6B → 9A)
└── milestone/9b-epg-guide-ui
```

The stacked-branch era ended with integration PR #24; milestone branches now
base directly on `develop`.

## What changed

- New top-level Guide destination: touch bottom bar / rail entry and a Home hub
  tile, both gated by the Live entitlement (guide shows and tunes live channels
  only). Blocked Live access hides/blocks the guide exactly like Live.
- `GuideTimeline` pure policy: half-hour slots, 4 dp per minute, a window from
  one slot before "now" to 12 hours ahead, and gap/cell lane math that clips
  overlapping or window-crossing programmes so lanes never desynchronize from
  the ruler.
- Guide screen: channels page vertically through the same windowed pager as
  Live; every visible row collects its own indexed range query. All lanes and
  the ruler share one horizontal scroll state. The view opens scrolled to just
  before "now"; airing cells are highlighted and follow a minute tick.
- Focus (TV) or tap (touch) shows channel, time range, title and description in
  the detail strip; OK or tap tunes the channel in the live player.
- Strings in English, French and Arabic.
- No schema change: one new indexed DAO range query over the 9A table.

## Pull and verify the branch

```powershell
git fetch origin --prune
git switch --track origin/milestone/9b-epg-guide-ui
git status
git rev-parse HEAD
```

Record the exact commit; both testers must approve the same SHA.

## Build and automated checks

```powershell
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest
./gradlew :app:lintDebug :app:lintRelease
./gradlew :app:assembleDebug
```

All must pass. New unit tests: `GuideTimelineTest`, updated
`TopLevelNavigationPolicyTest`.

## Install-over-existing check — required

1. Install over the previous build (`./gradlew installDebug`, no uninstall).
2. Launch: Home opens; playlists, bookmarks, progress and settings survive.
3. A new "TV Guide" tile appears on Home; touch devices also show "Guide" in
   the bottom bar / rail after Live.

## Guide flow (mock server)

1. `python3 tools/mock_server.py`, sync the mock Xtream playlist.
2. Open the Guide: a time ruler starts near the current half-hour; rows for all
   channels; the view lands with the airing column visible.
3. Channels with guide data (France 24, BBC World News, …) show programme
   cells; Eurosport (no guide id) shows an empty lane that still scrolls in
   sync with the others.
4. Horizontal scroll/drag on any lane moves ruler and all lanes together —
   columns never drift apart.
5. TV: D-pad RIGHT/LEFT moves between programmes in a lane, UP/DOWN between
   channels; the detail strip updates with the focused programme (title, time
   range, description). OK on a cell tunes that channel in the live player.
6. Touch: tap a cell — the channel starts playing. The tapped programme shows
   in the detail strip on return.
7. Airing cells are visibly highlighted; the highlight moves to the next
   programme within a minute or two of a slot boundary.
8. M3U playlist: Direct One/Two and Chaîne Française lanes show programmes
   sourced from url-tvg.

## Managed access

1. With a managed policy that disables Live, the Guide disappears from touch
   navigation and the Home tile, and direct navigation shows the blocked
   screen — never the guide content.

## Form factors

- TV (Xiaomi box): 10-foot readability of ruler/cells/detail strip, focus never
  traps inside a lane, BACK leaves the guide normally.
- Phone compact + landscape: guide usable, ruler aligned, no horizontal page
  scroll of the whole screen (only lanes scroll).
- Arabic/RTL: screen renders without truncation; lane scrolling remains usable.

## Guide-less provider (fail-safe)

1. Add a playlist whose source has no EPG (or stop the mock server before the
   guide refresh).
2. The Guide opens with channel rows and empty lanes — no crash, no spinner
   deadlock; the empty state appears only when there are no channels at all.

## Approval record

```text
Tester 1 approved: <SHA>
Tester 2 approved: <same SHA>
CI passed:         <same SHA>
```

## Recovery / rollback

No database changes. Reverting to the previous build restores the previous UI
with no data impact.
