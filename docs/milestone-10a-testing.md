# Milestone 10A — low-end performance test plan

This milestone targets the 1–2 GB TV boxes: install-time baseline-profile
compilation, on-demand WorkManager initialization, RAM-class playback buffers
and a visible zap-speed measurement. There are no new user-facing features —
the app should simply start faster and play more smoothly on weak hardware,
and behave identically everywhere else.

## Branch position

```text
develop (…, 9C live player UX)
└── milestone/10a-low-end-performance
```

## What changed

- **Baseline-profile installation**: the `androidx.profileinstaller` dependency
  makes the ahead-of-time compilation profiles shipped inside Compose and
  Media3 take effect at install time on sideloaded devices (IPTV boxes never
  get Play's background optimization pass). Improves cold start and first-use
  jank, most visibly on release builds.
- **On-demand WorkManager**: the eager process-start initializer is removed;
  WorkManager now initializes only when the background sync actually schedules.
  Shaves work off every app launch.
- **RAM-class playback buffers** (`PlaybackBufferPolicy`, unit-tested): Android
  low-RAM devices get smaller media-buffer ceilings for live (15 s vs 30 s) and
  VOD (20 s vs 50 s) so buffering never competes with the UI for heap; the
  zap startup threshold (~1.2 s) is identical everywhere, and VOD now has an
  explicit load control instead of library defaults.
- **Zap-speed metric**: every channel start is timed from zap request to first
  ready frame and shown in Sync & health as "Channel start: N ms · slowest
  M ms" (session-scoped, numbers only — no provider data).

## Build and automated checks

```powershell
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest
./gradlew :app:lintDebug :app:lintRelease
./gradlew :app:assembleDebug :app:assembleRelease
```

New unit tests: `PlaybackBufferPolicyTest`.

## Regression checks (any device)

1. Install over existing data: everything survives; app opens to Home.
2. Background sync still works: Settings → Sync & health → change the cadence,
   trigger a manual re-sync; a background refresh eventually completes
   (`adb logcat -s SyncRepository` shows the background trigger) — this
   verifies the on-demand WorkManager path end to end.
3. Live and VOD playback, zapping, resume, gestures: unchanged behavior.
4. Sync & health shows "Channel start" after the first zap; the number updates
   with each channel change.

## Performance checks (Xiaomi box — Tester 2's device is the reference)

Use the **release** build for timing; debug builds are much slower by nature.

1. Cold start: force-stop, clear from recents, launch. Repeat 3×. Record the
   time from icon press to Home rendered. Compare against the previous build
   if available — expect equal or better, never worse.
2. Zap speed: open Live, zap across 10 channels with UP/DOWN. Read
   "Channel start … slowest …" from Sync & health. On a healthy provider both
   numbers should sit well under ~2 000 ms; record them in the approval note.
3. Long session: 15+ minutes of continuous live playback with periodic
   zapping; no growing stutter, no crash (smaller buffers must not cause more
   rebuffering than the previous build on the same stream).
4. Phone sanity: same checks briefly on the phone — the standard (non-low-RAM)
   class keeps the old buffer behavior.

## Approval record

```text
Tester 1 approved: <SHA>
Tester 2 approved: <same SHA>   (performance reference device)
CI passed:         <same SHA>
```

## Recovery / rollback

No database or behavior-visible changes; reverting to the previous build has
no data impact.

## Deferred (recorded for later)

An app-specific generated Baseline Profile (macrobenchmark module driving the
real startup path on a managed device) would add further cold-start gains on
top of the library profiles; it needs device infrastructure this milestone
doesn't assume. Candidate for the release-hardening pass before 1.0 publication.
