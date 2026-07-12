# CLAUDE.md

This file provides guidance to coding agents working with this repository.

## What this is

NovaPlay — a native Android TV IPTV player (single Gradle module `:app`, package `com.novaplay.tv`). It ships with zero content: users add a personal Xtream/M3U source or pair with a future provider portal. Targets low-end Android TV boxes (1–2 GB RAM); the UI is D-pad-first but adaptive to touch and phone form factors.

Stack: Kotlin · Jetpack Compose for TV (`androidx.tv:tv-material`, no XML/Leanback) · Media3 ExoPlayer · Room + Paging 3 · SQLite FTS4 · Hilt · Retrofit + kotlinx.serialization · DataStore · WorkManager · Coil. MVVM + StateFlow, unidirectional data flow.

## Commands

Requires JDK 17+ and Android SDK platform 35 (`local.properties` → `sdk.dir`).

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:testReleaseUnitTest
./gradlew :app:lintDebug
./gradlew :app:lintRelease
# single test class:
./gradlew :app:testDebugUnitTest --tests "com.novaplay.tv.ui.player.PlaybackRetryPolicyTest"
```

CI builds, tests and lints both debug and release variants. Release is minified and resource-shrunk, mock activation is off, and the generated APK remains unsigned until private signing is configured outside the repository.

### Running locally

The dependency-free `tools/mock_server.py` provides a fake Xtream panel and M3U host on port 8899 for personal-playlist and playback testing. It does not implement the provider pairing portal.

```bash
python3 tools/mock_server.py
./gradlew installDebug
adb shell am start -n com.novaplay.tv/.MainActivity
adb logcat -s "SyncRepository"
adb logcat | grep -E "ExoPlayerImplInternal|MediaCodec"
```

On the Android emulator, `10.0.2.2` reaches the development machine. A physical device can use the machine's LAN address or `adb reverse` where appropriate.

Debug activation is a UI/protocol stand-in only. Its mock assignment list is deliberately empty and production source sets contain no provider credentials. Never commit provider URLs, usernames, passwords, tokens, signing keys or portal secrets. For local catalogue testing, add a personal Xtream/M3U source through the app. A real portal test requires a compatible development backend; debug may use HTTP only for local hosts, while production portal traffic requires HTTPS.

D-pad in the emulator: arrow keys = D-pad, Enter = OK, Esc = BACK, number keys = channel digits. On a touch device without a remote: `adb shell input keyevent KEYCODE_DPAD_*` / `KEYCODE_DPAD_CENTER` / `KEYCODE_BACK`.

## Architecture

```
core/           Device identity, stable content IDs, serializers, safe errors, portal endpoint policy
background/     WorkManager periodic catalogue sync
 data/db/       Room entities, FTS4 tables, DAOs, migrations
 data/remote/   Portal contract, build-variant mock, Xtream client and DTOs
 data/m3u/      Streaming M3U parser
 data/repo/     Activation, pairing, sync, content, access policy and diagnostics
 data/security/ Keystore-backed playlist secrets and portal tokens
 data/prefs/    DataStore-backed settings
 di/            Hilt modules for network, database and app scope
 ui/            Adaptive Compose screens and navigation
 tools/         Local Xtream/M3U playback server
```

Core flows:

- **Launch gate:** migrates plaintext secrets, routes to Activation when no active playlist exists, otherwise opens Home and may schedule a non-blocking refresh.
- **Portal pairing:** device-code flow separates the visible user code from the private session secret and revocable tokens. Production calls fail before I/O unless the portal endpoint is securely configured.
- **Managed access:** the portal resolves entitlements server-side; unknown lifecycle states fail closed.
- **Sync:** Xtream/M3U data is transactionally installed in Room. Existing catalogues remain readable after failed refreshes.
- **Secrets:** credentials and portal tokens are encrypted through Android Keystore before persistence.
- **Live playback:** bounded retries, stall detection and HLS/MPEG-TS fallback.
- **Search:** FTS4 prefix matching with debounce and stale-query cancellation.
- **Watch progress:** persisted periodically and on stop; resume and completion rules live in pure policy objects.
- **Adaptive UI:** resolved TV/touch mode plus window width classes drive phone, tablet and TV layouts.

## Conventions

- Keep decision rules in small pure-Kotlin policy objects with JUnit tests.
- Fail closed for invalid security, policy and retry states.
- User-copyable diagnostics and errors must never contain playlist URLs, servers, credentials, tokens, MAC address, device key, Device ID or portal hostname.
- Commit Room schema changes under `app/schemas/` and provide explicit non-destructive migrations.
- Install milestone builds over existing data unless the test plan explicitly marks a fresh-install check optional.
- Use short-lived feature/fix branches and pull requests. Milestones merge into `develop` only after CI plus both physical-device approvals. `main` remains the stable release branch.
- `docs/milestone-*-testing.md` contains per-milestone manual checks; portal contracts live under `docs/`.
