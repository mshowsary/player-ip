# Milestone 9A — EPG foundation test plan

This milestone adds the TV-guide data layer: XMLTV parsing, Xtream (`xmltv.php`)
and M3U (`url-tvg`) guide sources, database schema v4, guide refresh inside the
catalogue sync, and now/next programme display on Live channel rows and in the
live player overlay. It does not add the grid guide UI (9B) or catch-up (9C).

## Branch position

```text
develop
└── milestone/6b-release-hardening
    └── fix/6b-sync-health-responsive-layout
        └── milestone/7a-managed-access-lifecycle
            └── milestone/7b-accessibility-readable-errors
                └── milestone/7c-localization-rtl-foundation
                    └── milestone/7d-interaction-focus-consistency
                        └── milestone/8a-release-candidate-packaging
                            └── milestone/8b-release-readiness-guardrails
                                └── milestone/9a-epg-foundation
```

Nothing from this stack enters `develop` or `main` during this test.

## What changed

- Room schema v3 → v4 (additive only): `playlists.epgUrl`, `live_channels.epgChannelId`,
  new `epg_programmes` table with browse and pruning indices.
- Xtream sync captures `epg_channel_id` per channel; M3U sync captures `tvg-id`
  and the header's `url-tvg`/`x-tvg-url` guide address (stored sealed, like credentials).
- Guide refresh runs as a best-effort final step of every catalogue sync: staged
  bounded download, streaming SAX parse, transactional per-playlist replacement.
  A failed guide refresh never fails the sync and keeps the previous guide.
- Programmes are stored only for channels present in the playlist and only inside
  the retention window (12 h back, 48 h forward).
- Live channel rows show the airing programme (or "Next: …"); the live player
  overlay shows the airing programme with its time range plus the next one.
- Sync & health shows a "TV guide" programme count; the mock server gained
  `/xmltv.php` and the M3U header now advertises it.

## Pull and verify the branch

```powershell
git fetch origin --prune
git switch --track origin/milestone/9a-epg-foundation
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

All must pass. New unit tests: `EpgPoliciesTest`, `XmltvParserTest`.

## Install-over-existing (migration) check — required

1. Start from a device/emulator that already has the Milestone 8B build with a
   synced playlist, bookmarks, watch progress and settings.
2. Install this build over it (`./gradlew installDebug`, no uninstall).
3. Launch: Home must open normally — no crash, no re-activation.
4. Verify bookmarks, recently viewed, resume positions and settings survived.
5. Live channels initially show no programme lines (guide not yet fetched);
   trigger a manual refresh, then programme lines appear for mapped channels.

## Xtream guide flow (mock server)

1. `python3 tools/mock_server.py`, add/select the mock Xtream playlist
   (`http://10.0.2.2:8899`, any username/password), sync.
2. Live list: France 24, BBC World News, Al Jazeera, Bein Sports 1, Télé Maroc
   and 2M Monde show a current programme line ("Programme N on …").
   The Xtream ids are deliberately mixed-case — matching must still work.
3. Eurosport has no `epg_channel_id` and must show its name only, no guide line.
4. Open a mapped channel: the info overlay shows the programme with a time
   range and a "Next: …" line; after zapping, the overlay shows the new
   channel's programme.
5. Sync & health: "TV guide" shows a non-zero programme count. Programmes for
   `not-in-playlist.xx` are never installed (count stays well below the full
   feed size; foreign-channel filtering).

## M3U guide flow (mock server)

1. Add the mock M3U playlist (`http://10.0.2.2:8899/list.m3u`), select and sync.
2. Direct One / Direct Two / Chaîne Française show programme lines sourced from
   the `url-tvg` header address.

## Guide failure keeps data (fail-safe check)

1. With a synced mock playlist showing guide lines, stop the mock server.
2. Trigger a manual refresh (it fails).
3. Existing channels, categories and the previous guide lines remain readable;
   the app never crashes and never falls back to an empty catalogue.

## Privacy check

1. Open Sync & health and copy the support report.
2. It must not contain the guide URL, playlist URL, server address, credentials
   or tokens — only counts and sanitized statuses.

## Form factors

- TV/remote (Xiaomi box): programme lines readable at 10 feet, D-pad focus
  behavior on Live unchanged, digit-jump still works, overlay unchanged apart
  from the added programme lines.
- Phone (touch): rows show two-line channel entries without clipping in compact
  width; landscape and RTL (Arabic) render without truncation issues.

## Approval record

```text
Tester 1 approved: <SHA>
Tester 2 approved: <same SHA>
CI passed:         <same SHA>
```

## Recovery / rollback

Schema v4 is additive. Reinstalling the 8B build over the same data is safe
only via Android's normal downgrade rules (not allowed by the platform), so
roll back by clearing app data or reinstalling; personal playlists must then be
re-added. No destructive migration exists in this milestone.
