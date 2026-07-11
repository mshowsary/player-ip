# NovaPlay — Android TV IPTV Player

Native Android TV IPTV player in the spirit of IBO Player Pro. Ships with **zero content**:
users activate their device on a web portal, which assigns them a playlist (Xtream Codes or M3U).
Built for low-end Android TV boxes (1–2 GB RAM).

## Stack

- Kotlin · Jetpack **Compose for TV** (`androidx.tv:tv-material`) — no XML, no Leanback
- **Media3 ExoPlayer** (HLS, MPEG-TS, progressive MP4/MKV)
- **Room + Paging 3** — the DB owns every large list; UI sees a paged window only
- **SQLite FTS4** (external content, `unicode61`) — accent-/case-insensitive prefix search at 50k+ rows
- **Coil** (memory + disk cache) · **Retrofit + kotlinx.serialization** (lenient JSON)
- **DataStore** (settings) · **Hilt** (DI) · MVVM + StateFlow, unidirectional data flow
- R8 + resource shrinking in release

## Build

Requires JDK 17+ and an Android SDK with platform 35 (`local.properties` → `sdk.dir`).

```bash
./gradlew :app:assembleDebug      # debug: MOCK_ACTIVATION = true
./gradlew :app:assembleRelease    # minified + shrunk, mock OFF (unsigned)
```

---

# Debugging locally

The app is portal-driven, so local debugging has two halves: the **debug APK** (which has
`BuildConfig.MOCK_ACTIVATION = true` — the activation screen's *Check now* attaches the
playlists hardcoded in `MockPortal`, `app/src/main/java/com/novaplay/tv/data/remote/PortalApi.kt`)
and the **mock server** (`tools/mock_server.py`) those playlists point at.

## 1. Start the mock server

The server is a dependency-free Python script that fakes a full Xtream Codes panel + an M3U
host on port **8899**: categories, 50-channel-style live lists (including a dirty no-name entry
to exercise the skip path), movies, series with seasons, `get_vod_info` / `get_series_info`,
and HTTP Range so ExoPlayer can seek. The live `.m3u8` URLs deliberately return **404** so you
can watch the HLS → TS fallback fire.

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

Any phone AVD works — `adb install` ignores the leanback `<uses-feature>` filter, and the app
is locked to landscape anyway. A real Android TV AVD (API 30+ "Television" image) is closer to
production but not required.

```bash
emulator -avd <your_avd> &
./gradlew installDebug
adb shell am start -n com.novaplay.tv/.MainActivity
```

Two emulator-specific facts:

- **`10.0.2.2` is the emulator's alias for your machine** — `MockPortal` already points there,
  so no config change is needed. Press **Check now** on the activation screen and both mock
  playlists attach and sync.
- Phone launchers hide leanback-only apps, hence the explicit `am start` above.

**Navigating a D-pad UI in the emulator:** click the emulator window once, then use the
keyboard — arrow keys = D-pad, **Enter** = OK, **Esc** = BACK, number keys = channel digits.
The on-screen TV keyboard appears for search fields.

## 2b. Run on a physical phone / Android TV box

1. Enable *Developer options → USB debugging* on the device, then either plug in USB or use
   Wi-Fi adb (`adb connect <device-ip>:5555` — on TV boxes usually under
   *Settings → Device Preferences → About → Status* for the IP).
2. `10.0.2.2` doesn't exist outside the emulator. Point `MockPortal` at your computer's LAN IP
   (both the `server` and `url` fields in
   `app/src/main/java/com/novaplay/tv/data/remote/PortalApi.kt`):

   ```kotlin
   server = "http://192.168.1.20:8899",          // your machine
   url    = "http://192.168.1.20:8899/list.m3u",
   ```
3. Make sure your firewall allows inbound 8899 and that phone + computer share the same network.
4. `./gradlew installDebug` (with the device selected), then launch — TV boxes show the app in
   the leanback launcher; on a phone use the `am start` command above.

**Navigation on a touch phone:** the UI is D-pad-only (no touch targets by design). Use one of:

- a Bluetooth keyboard/remote paired to the phone, or
- `adb shell input keyevent <KEY>` from your computer:

  | Action | keyevent |
  |---|---|
  | move focus | `KEYCODE_DPAD_UP / DOWN / LEFT / RIGHT` |
  | OK / select | `KEYCODE_DPAD_CENTER` |
  | back | `KEYCODE_BACK` |
  | channel digits | `KEYCODE_0` … `KEYCODE_9` |
  | type in search | `adb shell input text 'tele'` |

## 3. A guided smoke tour

Once activated and synced (watch the quiet "Syncing…" line at the bottom of Home):

1. **Home** header should show *Mock Xtream · expires <date> · clock* — proof `user_info` parsed.
2. **Live TV** → categories *News / Sports / Maroc*; open **Search**, type `tele` → finds
   *Télé Maroc* (accent-insensitive FTS). Play it: the player logs a 404 on `.m3u8`, falls back
   to `.ts`, and the test pattern plays. **UP/DOWN** zaps, **OK** shows the info overlay.
3. **Movies** → *Batman Begins* → details load plot/genre from `get_vod_info` → **Play**.
   Hold **RIGHT** to feel the accelerating seek, back out after ~20 s, reopen → a pre-focused
   **Resume from…** button.
4. **Series** → *Breaking Code* → season tabs + episodes; partially-watched episodes grow a
   thin progress bar.
5. **Settings** → change subtitle style and watch the live preview update instantly.
6. **Change Playlist** → set *Mock M3U* active → Live TV now shows the M3U groups
   (*Direct / Français*) parsed by the streaming M3U parser.

## 4. Useful debugging commands

```bash
adb logcat -s "SyncRepository"                        # sync failures (never crash the app)
adb logcat | grep -E "ExoPlayerImplInternal|MediaCodec"  # playback errors
adb shell pm clear com.novaplay.tv                    # wipe app -> back to activation screen
```

- The Room DB lives at `/data/data/com.novaplay.tv/databases/novaplay.db`
  (`adb shell "run-as com.novaplay.tv sqlite3 databases/novaplay.db '.tables'"` on debug builds).
- Device identity (MAC + key) persists in DataStore — `pm clear` regenerates the key, and the
  MAC re-resolves through the chain (`NetworkInterface` eth0 → wlan0 →
  `/sys/class/net/eth0/address` → `ANDROID_ID`-derived pseudo-MAC; emulators land on the last).
- Testing against a **real Xtream panel** instead of the mock: put its URL/credentials in
  `MockPortal` — the rest of the app is identical.

---

## Renaming the app

`NovaPlay` is a placeholder. To rename:

1. `app/src/main/res/values/strings.xml` → `app_name` (single source of truth for the label)
2. `app/build.gradle.kts` → `namespace` / `applicationId` (`com.novaplay.tv`)
3. Replace launcher assets: `res/mipmap-*/ic_launcher.png`, `res/drawable-xhdpi/tv_banner.png` (320×180)

## Portal configuration

- Base URL lives in `BuildConfig.PORTAL_BASE_URL` (`app/build.gradle.kts`), placeholder
  `https://portal.example.com`. Release builds poll it for real.
- Contract: `GET /api/v1/devices/{mac}/playlists?key={deviceKey}` →
  `200 {playlists:[…]}` / `404` not registered yet (keeps polling) / `403` key mismatch.

## Architecture map

```
core/          MAC resolution chain, device key, lenient JSON serializers
data/db/       Room entities, FTS4 tables, DAOs (paged + search + zap queries)
data/remote/   Portal client, Xtream client (streaming decode), DTOs, MockPortal
data/m3u/      Line-by-line streaming M3U parser
data/repo/     Activation, Sync (transactional bulk upserts), Content access
data/prefs/    DataStore-backed settings (subtitle style, live format)
di/            Hilt modules (network, DB, app scope)
ui/            Compose for TV screens: activation, home, live (+player),
               movies, series, VOD player (track dialogs), playlists, settings
tools/         mock_server.py — local Xtream/M3U test server
```

Key behaviors:

- **Launch gate**: no playlist → Activation; otherwise Home, with a silent re-sync if the
  last sync is older than 12 h.
- **Live zapping**: DPAD UP/DOWN in the player moves through the current category via indexed
  `(playlistId, categoryId, num)` queries; low-latency load control (~1.2 s startup buffer).
- **HLS↔TS fallback**: live playback errors retry once with the alternate container before
  surfacing an error overlay (configurable in Settings).
- **Search**: FTS4 `MATCH` with sanitized prefix tokens (`bat mov` → `bat* mov*`), 300 ms
  debounce, min 2 chars, `flatMapLatest`. Never `LIKE '%…%'`.
- **Watch progress**: persisted every 10 s and on stop; Resume appears at ≥ 60 s and < 95 %.
- **Subtitle styling**: global `CaptionStyleCompat` via `SubtitleView`, live preview in Settings.

## Out of scope (v1, by design)

EPG, catch-up/timeshift, favorites, parental lock, multi-screen.
#   p l a y e r - i p  
 #   p l a y e r - i p  
 