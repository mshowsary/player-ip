# Milestone 12C — player identity, trial and license (Account rows)

Same setup as 12A/12B (portal + mock server + adb reverse + live-portal
build flags). Requires portal ≥ P13 (`alembic upgrade head`).

## Trial lifecycle

1. Clear app data, install the live-portal build, open the app and go to
   **Settings → This device**. Within a few seconds two new rows appear:
   - **Device code** — `NP-XXXX-XXXX` (permanent identity)
   - **Player license** — `Trial — 7 day(s) left`
2. Public status page: on the PC open
   `http://localhost:8000/api/public/player-devices/NP-XXXX-XXXX` — same
   status, plus the device name; never any secret.
3. Grant the lifetime license (platform-owner login):
   `POST /api/admin/player-devices/NP-XXXX-XXXX/license` (curl or the API
   docs page). Reopen Settings → the row flips to **Lifetime license
   active**.
4. Expiry: in the portal DB set `trial_ends_at` in the past for the row
   (or wait 7 days…), reopen Settings → **Trial ended — activate to
   continue**.
5. Offline: stop the portal, reopen Settings → the last state shows with
   an **(offline)** suffix instead of flipping.

## Boundaries

- Managed-only white-label brands (`allowPersonalPlaylists=false`) and
  plain mock builds show no license rows at all.
- The device secret never appears on screen or in the public lookup.

## Not yet in this milestone (deliberate)

Hard trial enforcement (blocking personal playback after expiry) is
display-only for now — the gate placement (playback vs. playlist screen vs.
launch) is an owner UX decision before we wire it.
