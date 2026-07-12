# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

NovaPlay — a native Android TV IPTV player (single Gradle module `:app`, package `com.novaplay.tv`). Ships with **zero content**: users pair the device with a web portal, which assigns playlists (Xtream Codes or M3U). Targets low-end Android TV boxes (1–2 GB RAM); the UI is D-pad-first but adaptive to touch/phone form factors.

Stack: Kotlin · Jetpack Compose for TV (`androidx.tv:tv-material`, no XML/Leanback) · Media3 ExoPlayer · Room + Paging 3 · SQLite FTS4 · Hilt · Retrofit + kotlinx.serialization (lenient) · DataStore · WorkManager · Coil. MVVM + StateFlow, unidirectional data flow.

## Commands

Requires JDK 17+ and Android SDK platform 35 (`local.properties` → `sdk.dir`).

```bash
./gradlew :app:assembleDebug        # debug build (MOCK_ACTIVATION = true)
./gradlew :app:assembleRelease      # minified R8 + resource shrinking, mock OFF, unsigned
./gradlew :app:testDebugUnitTest    # unit tests (plain JUnit4, JVM only)
./gradlew :app:lintDebug            # Android lint
# single test class:
./gradlew :app:testDebugUnitTest --tests "com.novaplay.tv.ui.player.PlaybackRetryPolicyTest"
```

CI (`.github/workflows/android-ci.yml`) runs `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — keep all three green.

### Running locally

Local debugging has two halves (full walkthrough in README.md):

1. **Mock server**: `python3 tools/mock_server.py` — dependency-free fake Xtream panel + M3U host on port 8899. Serves `tools/test.mp4` (generate with the ffmpeg command in the README; keep `-profile:v baseline -pix_fmt yuv420p` for emulator software decoders). Live `.m3u8` URLs deliberately 404 to exercise the HLS→TS fallback. Port stuck: `fuser -k 8899/tcp`.
2. **Debug APK**: `MOCK_ACTIVATION = true` makes the activation screen's *Check now* attach the playlists hardcoded in `MockPortal` (`app/src/main/java/com/novaplay/tv/data/remote/PortalApi.kt`). On the emulator `10.0.2.2` already points at your machine; on a physical device edit `MockPortal`'s `server`/`url` to your LAN IP. To test against a real Xtream panel, put its credentials in `MockPortal` — the rest of the app is identical.

```bash
./gradlew installDebug
adb shell am start -n com.novaplay.tv/.MainActivity   # phone launchers hide leanback apps
adb logcat -s "SyncRepository"                        # sync failures (never crash the app)
adb logcat | grep -E "ExoPlayerImplInternal|MediaCodec"  # playback errors
adb shell pm clear com.novaplay.tv                    # wipe -> back to activation screen
```

D-pad in the emulator: arrow keys = D-pad, Enter = OK, Esc = BACK, number keys = channel digits. On a touch device without a remote: `adb shell input keyevent KEYCODE_DPAD_*` / `KEYCODE_DPAD_CENTER` / `KEYCODE_BACK`.

## Architecture

```
core/           MAC resolution chain, device key, stable content IDs, lenient JSON serializers,
                privacy-safe error messages (SafeErrorMessage)
background/     WorkManager periodic catalogue sync (BackgroundCatalogSyncWorker + Scheduler + Policy)
data/db/        Room entities, FTS4 tables, DAOs (paged + search + zap queries), DatabaseMigrations
data/remote/    Portal client + MockPortal, Xtream client (streaming decode), DTOs
data/m3u/       Line-by-line streaming M3U parser
data/repo/      Activation, PortalPairing (protocol + repository), Sync (transactional bulk upserts),
                Content access, ManagedAccess policy, PlaylistManager, AppDiagnostics
data/security/  PlaylistSecrets (Keystore AES-GCM), PortalTokenStore
data/prefs/     DataStore-backed settings
di/             Hilt modules (network, DB, app scope)
ui/             Compose screens: gate, activation, home, live (+player), movies, series,
                VOD player, playlists, settings, access-blocked; navigation/ holds the nav graph
tools/          mock_server.py
```

Core flows that span multiple packages:

- **Launch gate** (`ui/gate/GateViewModel`): migrates plaintext secrets, then no active playlist → Activation; otherwise Home with a silent re-sync if the last sync is older than 12 h. Sync and policy refreshes never block navigation.
- **Portal pairing** (`data/repo/PortalPairingProtocol` + docs/portal-pairing-contract.md): device owns a random installation `device_id` (MAC is a legacy display hint only). Pairing = `user_code` (displayable) + `session_secret` (never displayed/logged/in URLs) → revocable bearer tokens stored Keystore-encrypted in `PortalTokenStore`.
- **Managed access** (`data/repo/ManagedAccessRepository` + docs/managed-access-policy.md): the portal resolves entitlements server-side and returns a device policy gating live/movies/series. Unknown lifecycle states **fail closed** (treated as suspended); a missing policy keeps the current one.
- **Sync** (`data/repo/SyncRepository`): Xtream/M3U → transactional bulk upserts into Room. The DB owns every large list; the UI only ever sees a Paging 3 window. Sync errors surface via `SyncStatus` — they must never crash the app.
- **Secrets** (`data/security/PlaylistSecrets`): provider credentials are sealed with Android Keystore before being written to Room; already-sealed values (prefix-marked) are never double-encrypted; plaintext rows from old builds are migrated once at launch.
- **Live playback** (`ui/live/` + `ui/player/PlaybackRetryPolicy`): DPAD UP/DOWN zaps via indexed `(playlistId, categoryId, num)` queries; each source gets 2 bounded-backoff retries before falling back to the alternate HLS/MPEG-TS container, then an error overlay.
- **Search**: FTS4 `MATCH` with sanitized prefix tokens (`bat mov` → `bat* mov*`), 300 ms debounce, min 2 chars, `flatMapLatest`. Never `LIKE '%…%'`.
- **Watch progress**: persisted every 5 s while playing and on stop (`VodPlayerViewModel.PROGRESS_SAVE_INTERVAL_MS`); detail screens show Resume at ≥ 60 s and < 95 % watched (`isResumable`), while the in-player `VodResumePolicy` uses a 30 s minimum and rewinds 10 s on resume. (The README's "every 10 s / ≥ 60 s" wording predates these constants.)
- **Adaptive UI** (`ui/theme/Adaptive.kt`): `ResolvedUiMode` (TV/TOUCH) + `WindowWidthClass` drive layout via composition locals; `Adaptive*`/`Responsive*` screens are the form-factor-aware variants.

## Conventions

- **Testable policy objects**: decision logic lives in small pure-Kotlin objects (`PlaybackRetryPolicy`, `VodPlaybackPolicy`, `BackgroundSyncPolicy`, `PortalPairingProtocol`, `ManagedAccessPolicy`, `PlaylistFormRules`, `CatalogLayoutSpec`…) separate from ViewModels, tested with plain JUnit4 — no Robolectric or instrumentation tests in this repo. When adding behavior with rules worth testing, follow this pattern.
- **Fail closed** on invalid/unknown state (policy states, retry indices) rather than guessing.
- **Privacy-safe diagnostics**: anything user-copyable (support info, error messages) must never contain playlist URLs, servers, credentials, tokens, MAC, device key, or device ID — route error text through `SafeErrorMessage`.
- **Room migrations**: schema JSON is exported to `app/schemas/` (KSP `room.schemaLocation`) and CI uploads it — commit schema changes and add a migration in `DatabaseMigrations.kt`; upgrades must preserve user data (milestone docs test install-over-existing).
- `docs/milestone-*-testing.md` are per-milestone manual test scripts; new user-facing milestones get one. `docs/portal-pairing-contract.md` and `docs/managed-access-policy.md` are the portal API contracts the real backend must honor.
- Out of scope for v1 by design: EPG, catch-up/timeshift, favorites, parental lock, multi-screen.
