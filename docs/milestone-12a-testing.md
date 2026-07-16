# Milestone 12A — live portal client (end-to-end pairing test)

This is the first test where the real app pairs against the real
NovaPlay Management portal: TV shows a code → dashboard approves it →
tokens arrive → playlists attach → channels play. No mocks in the
control plane; the only fake piece is the Xtream content server.

## What changed in the app

One build switch only: debug builds can now disable mock activation with
`-PnovaplayMockActivation=false`. The portal client itself was already
complete; the portal (novaplay-management P11) now speaks its DTOs exactly.
Release behavior is unchanged (mock is always off there).

## Prerequisites (all on the PC)

Two terminals in `novaplay-management`:

```powershell
# 1. The portal (includes P11), on port 8000
.venv\Scripts\python -m uvicorn app.main:app
```

One terminal in the app repo:

```powershell
# 2. The fake Xtream content server, on port 8899 (any username/password works)
python tools\mock_server.py
```

Dashboard prep at http://localhost:8000/portal (your provider admin login):

1. **Packages** → create one (e.g. `Gold`, device limit 2, all services on).
2. **Customers** → create one (e.g. `Test Family`), open it:
   - add a subscription (Gold, 12 months),
   - you'll assign the playlist after creating the source.
3. **Sources** → add source: kind `Xtream`,
   server `http://127.0.0.1:8899`, username `demo`, password `demo`.
4. Back on the customer page → **Playlists** → assign the source.

> `127.0.0.1` is correct on purpose: the phone reaches your PC through
> `adb reverse`, so from the phone's point of view your PC *is* localhost.

## Device wiring

Connect the phone (USB debugging on), then forward both ports:

```powershell
adb reverse tcp:8000 tcp:8000
adb reverse tcp:8899 tcp:8899
```

Build and install the live-portal debug build:

```powershell
.\gradlew installDebug -PnovaplayMockActivation=false "-PnovaplayPortalBaseUrl=http://127.0.0.1:8000"
```

## The test

1. **Fresh pairing** — open the app. With no stored session it lands on
   Activation and shows a code like `BCDF-2345` plus the activation address.
2. On the PC, dashboard → **Activate a device** → type the code (any
   format). Confirm the preview shows your phone's manufacturer/model,
   pick `Test Family`, **Approve**.
3. Within ~5 seconds the app should leave Activation on its own: tokens
   arrive on the poll together with the playlist snapshot, the playlist
   attaches, sync runs against the mock server, and Home shows content.
   Live channels should play.
4. **Remote kill switch** — on the customer page, **Revoke** the device.
   In the app, trigger a refresh (or reopen the app). Expected: managed
   access blocks with a clear message; content stops. Re-pair (new code,
   approve again) to recover — same device row in the dashboard, not a
   duplicate.
5. **Subscription lifecycle** — Suspend the subscription → app refresh
   shows "Service is paused. Contact your provider." Resume → active
   again. (Renew buttons should behave the same way from the reseller
   screen if you create a reseller user.)
6. **Wrong-code hygiene** — start a fresh pairing on the app, then in the
   dashboard deny the code. The app should report the denial and offer to
   start over.

## What to look for

- The pairing code screen never shows the session secret (only the code).
- After revoke, the app's message contains no server address, credentials
  or tokens (diagnostics rules).
- The dashboard device list shows the box with a recent "last seen".
- `adb logcat -s SyncRepository` shows the catalogue sync after approval.

## Cleanup

`adb reverse --remove-all` when done. Reinstalling with
`-PnovaplayMockActivation` omitted returns the app to its normal
mock-activation debug behavior.
