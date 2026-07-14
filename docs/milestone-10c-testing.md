# Milestone 10C — device-matrix hardening test plan

Playback discipline for the real-device world: audio focus, headphone-unplug
handling, and the dedicated remote keys that TV boxes and Fire TV remotes
carry. No schema or UI-layout changes.

## Branch position

```text
develop (…, 10B UI consolidation)
└── milestone/10c-device-matrix
```

## What changed

- **Audio focus (both players)**: starting playback claims media audio focus;
  another app starting media (or an incoming call) pauses NovaPlay instead of
  both playing over each other, and playback resumes politely per platform
  rules.
- **Becoming-noisy (both players)**: unplugging headphones / disconnecting
  Bluetooth audio pauses output instead of blasting the speaker.
- **Live player remote keys**: dedicated CH+ / CH− buttons zap next/previous
  exactly like D-pad UP/DOWN; play/pause keys pause the live buffer and resume
  in place (a long pause that outlives the source recovers through the
  existing retry machinery).
- **VOD player remote keys**: play/pause keys toggle playback (and surface the
  controls); fast-forward/rewind keys seek exactly like D-pad LEFT/RIGHT.

## Build and automated checks

```powershell
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest
./gradlew :app:lintDebug :app:lintRelease
./gradlew :app:assembleDebug
```

## Phone checks

1. Play a live channel, then start music in another app: NovaPlay pauses; no
   double audio. Same during a VOD title.
2. With wired or Bluetooth headphones during playback: unplug/disconnect —
   playback pauses, nothing plays from the speaker.
3. Regression: zapping, gestures, picker, overlay all unchanged.

## TV box / Fire TV checks (emulator can simulate with adb)

```powershell
adb shell input keyevent KEYCODE_CHANNEL_UP
adb shell input keyevent KEYCODE_CHANNEL_DOWN
adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE
adb shell input keyevent KEYCODE_MEDIA_FAST_FORWARD   # VOD
adb shell input keyevent KEYCODE_MEDIA_REWIND         # VOD
```

1. Live: CH+ zaps up, CH− zaps down (same as D-pad); play/pause freezes and
   resumes the stream; after a minutes-long pause, resuming either continues
   or recovers via the reconnect path — never a dead player.
2. VOD: play/pause works with controls hidden; FF/RW seek in the same steps as
   D-pad LEFT/RIGHT.
3. On the Xiaomi box: verify the remote's actual CH and play/pause buttons
   (not just adb) drive these paths.

## Approval record

```text
Tester 1 approved: <SHA>
Tester 2 approved: <same SHA>
CI passed:         <same SHA>
```

## Recovery / rollback

No data changes; reverting the build has no data impact.
