# NovaPlay — Android TV IPTV Player

Native Android TV IPTV player in the spirit of IBO Player Pro. Ships with **zero content**:
users add a personal playlist (Xtream Codes or M3U) using the MAC Address + Device Key shown
on the setup screen, or a managed provider assigns playlists through a portal using
device-code pairing. One codebase builds many branded players via white-label packs
(`brands/`). Built for low-end Android TV boxes (1–2 GB RAM); the UI is D-pad-first but
adapts to phone, tablet and touch.

## Stack

- Kotlin · Jetpack **Compose for TV** (`androidx.tv:tv-material`) — no XML, no Leanback
- **Media3 ExoPlayer** (HLS, MPEG-TS, progressive MP4/MKV)
- **Room + Paging 3** — the DB owns every large list; UI sees a paged window only
- **SQLite FTS4** (external content, `unicode61`) — accent-/case-insensitive prefix search at 50k+ rows
- **XMLTV EPG** — streamed parse, normalized channel keys, bounded retention window
- **Coil** (memory + disk cache) · **Retrofit + kotlinx.serialization** (lenient JSON)
- **DataStore** (settings) · **Hilt** (DI) · **WorkManager** (periodic sync) · MVVM + StateFlow
- **ZXing** (QR for pairing and phone-entry codes) · Android Keystore-encrypted secrets
- R8 + resource shrinking in release

## Build

Requires JDK 17+ and an Android SDK with platform 35 (`local.properties` → `sdk.dir`).

```bash
./gradlew :app:assembleDebug              # debug: MOCK_ACTIVATION = true
./gradlew :app:assembleRelease            # minified + shrunk, mock OFF (unsigned)
./gradlew :app:verifyReleaseCandidate     # everything CI gates on: tests + lint + APK/AAB
python3 -m unittest discover -s tools -p 'test_*.py'   # release-tooling tests
```

Branded builds: `-PnovaplayBrand=<slug>` (or `NOVAPLAY_BRAND`) selects a pack under
`brands/<slug>/`; the default is `novaplay`. Portal, version and signing are configured
per build via `NOVAPLAY_*` environment variables / `novaplay*` Gradle properties — see
`CLAUDE.md` § Build configuration and `brands/README.md`.

---

# Debugging locally

Local debugging has two halves: the **debug APK** (`BuildConfig.MOCK_ACTIVATION = true` —
portal pairing and licensing calls are stubbed by `MockPortal`,
`app/src/debug/java/com/novaplay/tv/data/remote/MockPortal.kt`, whose assignment list is
**deliberately empty**) and the **mock server** (`tools/mock_server.py`) you point a
personal playlist at. No provider credentials exist anywhere in the repository.

## 1. Start the mock server

The server is a dependency-free Python script that fakes a full Xtream Codes panel + an M3U
host on port **8899**: categories, live lists (including a dirty no-name entry to exercise
the skip path), movies, series with seasons, `get_vod_info` / `get_series_info`, an XMLTV
guide on `/xmltv.php` rolling around the current time, and HTTP Range so ExoPlayer can
seek. The live `.m3u8` URLs deliberately return **404** so you can watch the HLS → TS
fallback fire. XMLTV channel ids deliberately differ in casing from the Xtream
`epg_channel_id`s to exercise case-insensitive guide matching.

It serves `test.mp4` from its own directory — any H.264/AAC MP4 renamed `test.mp4` works,
or generate a 10-minute test-pattern clip:

```bash
ffmpeg -f lavfi -i "testsrc2=size=1280x720:rate=25:duration=600" \
       -f lavfi -i "sine=frequency=440:duration=600" \
       -c:v libx264 -profile:v baseline -pix_fmt yuv420p -c:a aac \
       -movflags +faststart tools/test.mp4

python3 tools/mock_server.py        # -> "mock xtream server on :8899"
```

> Stick to `-profile:v baseline -pix_fmt yuv420p` for emulator testing — the emulator's
> software decoder rejects exotic profiles and odd resolutions with a bare
> `MediaCodec.native_configure` error. Real boxes with hardware decoders are more tolerant.
>
> `OSError: Address already in use` → an old instance is still running: `fuser -k 8899/tcp`.

## 2a. Run on an emulator

Any phone AVD works — `adb install` ignores the leanback `<uses-feature>` filter. A real
Android TV AVD (API 30+ "Television" image) is closer to production but not required.

```bash
emulator -avd <your_avd> &
./gradlew installDebug
adb shell am start -n com.novaplay.tv/.MainActivity
```

On the **Set up your player** screen, add a personal playlist pointing at the mock server —
**`10.0.2.2` is the emulator's alias for your machine**:

- Xtream: server `http://10.0.2.2:8899`, any username/password (the mock ignores them)
- M3U: `http://10.0.2.2:8899/list.m3u`

Phone launchers hide leanback-only apps, hence the explicit `am start` above.

**Navigating with D-pad in the emulator:** click the emulator window once, then use the
keyboard — arrow keys = D-pad, **Enter** = OK, **Esc** = BACK, number keys = channel digits.
The on-screen TV keyboard appears for search fields.

## 2b. Run on a physical phone / Android TV box

1. Enable *Developer options → USB debugging* on the device, then either plug in USB or use
   Wi-Fi adb (`adb connect <device-ip>:5555` — on TV boxes usually under
   *Settings → Device Preferences → About → Status* for the IP).
2. `10.0.2.2` doesn't exist outside the emulator. For an **Xtream** playlist just enter your
   computer's LAN IP as the server (`http://192.168.1.20:8899`). The mock **M3U** hardcodes
   `10.0.2.2` inside `list.m3u` — edit the `M3U` constant in `tools/mock_server.py` for LAN
   testing.
3. Make sure your firewall allows inbound 8899 and that device + computer share the same network.
4. `./gradlew installDebug` (with the device selected), then launch — TV boxes show the app in
   the leanback launcher; on a phone use the `am start` command above.

The UI adapts to touch on phones and tablets. To drive it TV-style anyway, pair a Bluetooth
remote/keyboard or send key events from your computer:

  | Action | keyevent |
  |---|---|
  | move focus | `adb shell input keyevent KEYCODE_DPAD_UP / DOWN / LEFT / RIGHT` |
  | OK / select | `KEYCODE_DPAD_CENTER` |
  | back | `KEYCODE_BACK` |
  | channel digits | `KEYCODE_0` … `KEYCODE_9` |
  | type in search | `adb shell input text 'tele'` |

## 3. A guided smoke tour

1. **Setup screen** shows the identity panel (MAC Address + Device Key, QR for phone entry)
   next to the personal-playlist entry. Add the mock Xtream playlist as above — sync runs
   and Home opens in the hero layout.
2. **Live TV** → categories *News / Sports / Maroc*, with now/next guide lines from the
   XMLTV feed; open **Search**, type `tele` → finds *Télé Maroc* (accent-insensitive FTS).
   Play it: the player logs a 404 on `.m3u8`, falls back to `.ts`, and the test pattern
   plays. **UP/DOWN** zaps, **OK** shows the info overlay.
3. **Movies** → *Batman Begins* → details load plot/genre from `get_vod_info` → **Play**.
   Hold **RIGHT** to feel the accelerating seek, back out after ~20 s, reopen → a pre-focused
   **Resume from…** button.
4. **Series** → *Breaking Code* → season tabs + episodes; partially-watched episodes grow a
   thin progress bar.
5. **Settings** → change subtitle style and watch the live preview update instantly.
6. Add the **M3U** playlist (`http://10.0.2.2:8899/list.m3u`) and make it active → Live TV
   now shows the M3U groups (*Direct / Français*) parsed by the streaming M3U parser, with
   the guide loaded from the playlist's `url-tvg` attribute.

## 4. Useful debugging commands

```bash
adb logcat -s "SyncRepository"                        # sync failures (never crash the app)
adb logcat | grep -E "ExoPlayerImplInternal|MediaCodec"  # playback errors
adb shell pm clear com.novaplay.tv                    # wipe app -> back to setup screen
```

- The application id follows the brand pack; the default `novaplay` brand installs as
  `com.novaplay.tv` (used in the commands above).
- The Room DB lives at `/data/data/com.novaplay.tv/databases/novaplay.db`
  (`adb shell "run-as com.novaplay.tv sqlite3 databases/novaplay.db '.tables'"` on debug builds).
- Device identity (Device ID + MAC + key) persists in DataStore. `pm clear` regenerates the
  Device ID and key, but the MAC re-resolves through a deterministic chain
  (`NetworkInterface` eth0 → wlan0 → `/sys/class/net/eth0/address` → `ANDROID_ID`-derived
  pseudo-MAC; emulators land on the last) and usually comes back identical — the portal then
  reattaches the install to its existing device row (reinstall takeover), preserving the
  license and the trial clock. Wiping data is not a fresh trial.
- Testing against a **real Xtream panel**: add it as a personal playlist in the app — no code
  changes, and credentials are Keystore-encrypted before persistence. Never commit provider
  URLs or credentials anywhere.
- Testing against a **real development portal**: build with `-PnovaplayMockActivation=false`
  and `-PnovaplayPortalBaseUrl=http://127.0.0.1:8000` (debug allows HTTP for local hosts only).

---

## Branding

`NovaPlay` is the default brand, not a hardcoded name. A brand pack under `brands/<slug>/`
supplies the app name, application id, accent colors, launcher assets, feature toggles and
an optional preset portal / update channel — see `brands/README.md`. The build fails fast on
an incomplete pack; the code namespace stays `com.novaplay.tv` for every brand.

## Portal configuration

- Base URL lives in `BuildConfig.PORTAL_BASE_URL`, resolved as environment variable >
  Gradle property > brand pack > placeholder (`https://portal.example.com`). Release builds
  require a non-reserved HTTPS host and fail closed before any I/O otherwise.
- Pairing uses a **device-code flow** (`docs/portal-pairing-contract.md`): the TV shows a
  short user code + QR, polls with a private high-entropy session secret, and stores
  revocable bearer tokens after approval. The legacy
  `GET /api/v1/devices/{mac}/playlists?key=…` endpoint remains only as a migration path.
- The portal resolves entitlements and device lifecycle server-side
  (`docs/managed-access-policy.md`); unknown states fail closed.

## Architecture map

```
core/           Device identity (MAC chain + key), stable content IDs, lenient
                serializers, safe error copy, portal endpoint policy
background/     WorkManager periodic catalogue sync
data/db/        Room entities, FTS4 tables, DAOs (paged + search + zap queries), migrations
data/remote/    Portal contract + build-variant MockPortal, Xtream client (streaming decode)
data/m3u/       Line-by-line streaming M3U parser
data/epg/       XMLTV parser, channel-key normalization, retention policies
data/repo/      Activation, pairing, sync (transactional installs), content, access, diagnostics
data/security/  Keystore-backed playlist secrets and portal tokens
data/prefs/     DataStore-backed settings (subtitle style, live format, layout)
data/update/    Sideload update-manifest check (brand-configured HTTPS channel)
di/             Hilt modules (network, DB, app scope)
ui/             Adaptive Compose screens: activation/setup, home (hero), live (+player),
                movies, series, VOD player, playlists, settings, managed-access gate
brands/         White-label brand packs
tools/          mock_server.py, release packaging + readiness scripts
```

Key behaviors:

- **Launch gate**: no active playlist → setup screen; otherwise Home, with a silent
  non-blocking refresh when the catalogue is stale.
- **Live zapping**: DPAD UP/DOWN in the player moves through the current category via indexed
  `(playlistId, categoryId, num)` queries; low-latency live load control.
- **HLS↔TS fallback**: live playback errors retry with the alternate container within a
  bounded retry policy before surfacing an error overlay.
- **Search**: FTS4 `MATCH` with sanitized prefix tokens (`bat mov` → `bat* mov*`), 300 ms
  debounce, `flatMapLatest` stale-query cancellation. Never `LIKE '%…%'`.
- **EPG**: Xtream `epg_channel_id`, M3U `tvg-id` and XMLTV `channel` match through one
  normalized key; programmes persist only inside a 12 h back / 48 h forward window.
- **Watch progress**: persisted periodically and on stop; resume/completion rules
  (≥ 95 % or < 60 s remaining counts as finished) live in pure policy objects.
- **Bookmarks**: keyed by the provider's stable stream/series id so they survive re-syncs;
  surfaced as a Home rail and in Live TV.
- **Subtitle styling**: global `CaptionStyleCompat` via `SubtitleView`, live preview in Settings.
- **Parental control**: 4-digit PIN (salted hash, attempt lockout) locks categories across
  Live/Movies/Series; locked content is excluded in the Room queries (browse, search,
  zapping, Home rails), not just hidden in the UI.
- **Licensing**: self-service installs verify against the portal; enforcement uses the
  last verified state with a bounded 72 h offline grace (lifetime licenses stay valid
  offline indefinitely once verified).

## Out of scope (v1, by design)

Full EPG grid, catch-up/timeshift, multi-screen.
