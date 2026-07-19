# NovaPlay — Tester 2 guide (Ubuntu)

Welcome! This guide sets up the whole platform on one Ubuntu machine and
walks the three test tours: the **app on your Xiaomi box**, the **owner web
portal**, and the **reseller panel**. Everything runs locally — no internet
services involved.

Two repositories, both on branch `develop`:

- `player-ip` — the Android app (this repo)
- `novaplay-management` — the portal (FastAPI)

## 1. One-time setup

### Portal (needs Python 3.12+)

```bash
python3 --version          # need 3.12+; Ubuntu 24.04 ships it.
# Ubuntu 22.04 only: sudo add-apt-repository ppa:deadsnakes/ppa &&
#   sudo apt install python3.12 python3.12-venv  (then use python3.12 below)

cd novaplay-management
git switch develop && git pull
python3 -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
```

Start it (leave running in its own terminal):

```bash
uvicorn app.main:app
```

Dev mode creates/repairs the SQLite database by itself — **never run
alembic**. First run only, create the platform owner (second terminal,
same venv):

```bash
export NOVAPLAY_BOOTSTRAP_PASSWORD='pick-a-long-password'
python -m app.bootstrap you@example.dev "Tester Two"
```

### Fake IPTV content server (in the app repo)

The playlists you add will point at this fake Xtream panel — it serves a
couple of demo channels with a test clip (any username/password accepted):

```bash
cd player-ip
python3 tools/mock_server.py        # port 8899, leave running
```

### App build + Xiaomi box

JDK 17 and the Android SDK (platform 35) are required; `local.properties`
needs `sdk.dir=/home/you/Android/Sdk`. On the box: Settings → enable
Developer options → USB/network debugging. Then:

```bash
adb connect <box-ip>:5555           # or plug USB
adb reverse tcp:8000 tcp:8000       # box reaches the portal through adb
adb reverse tcp:8899 tcp:8899       # box reaches the fake content server
adb shell pm clear com.novaplay.tv  # fresh start (only when asked below)
./gradlew installDebug -PnovaplayMockActivation=false \
    -PnovaplayPortalBaseUrl=http://127.0.0.1:8000
```

`adb reverse` drops when the connection drops — re-run both lines after
any reconnect.

## 2. Tour A — the app on the TV (the part we most want your eyes on)

1. Fresh start (`pm clear` above), open NovaPlay on the box.
2. **Setup screen**: you should see YOUR PLAYER ID — a MAC Address, a
   Device Key, a QR code, the portal address, and a trial line. Judge the
   TV layout hard: readable at couch distance? Focus order sane with the
   remote? Screenshot anything ugly.
3. Leave this screen up and do Tour B; you'll come back.

After Tour B installs a playlist: press **"I added my playlist — check
now"** on the box → channels should appear within seconds, then:

4. **Home (Hero layout)**: big Live TV panel + Movies/Series/Playlists/
   Settings cards + rails. All names must show fully. Try Classic in
   Settings → Interface, try the accent colors.
5. **Live playback**: zap with the remote (CH+/- and numbers), open the
   in-player channel list, search in it, bookmark a channel.
6. **Settings → This device**: MAC, Device Key and "Player license:
   Free trial — N day(s) left" should all show.

## 3. Tour B — the owner web portal

On the Ubuntu machine's browser:

1. `http://localhost:8000/login` — type the **MAC Address + Device Key
   exactly as the TV shows them** (do not trust browser autofill).
2. You land on **/my**: device name, trial status, playlists.
3. **Add a playlist**: Xtream, server `http://127.0.0.1:8899`, any
   username/password. Also try the PIN protection checkbox — after saving,
   the entry hides its details and Edit/Remove demand the PIN.
4. Back to the box → "check now" → the playlist installs (Tour A step 4).
5. Try **Edit**, **Remove**, **Sign out / sign in**.

## 4. Tour C — the reseller panel

Create a reseller and give them credits (easiest via the interactive API
docs at `http://localhost:8000/docs`, all in dev mode):

1. `POST /api/admin/auth/login` — body:
   `{"email": "you@example.dev", "password": "<bootstrap password>"}`
2. `POST /api/admin/users` — body:
   `{"email": "seller@example.dev", "display_name": "Seller",
     "password": "seller-password-1", "role": "player_reseller"}`
3. `POST /api/admin/reseller-credits` — body:
   `{"email": "seller@example.dev", "delta": 10}`

Then in a **private browser window**: `http://localhost:8000/portal/login`
→ sign in as the seller → you land on **/reseller**:

1. **Check a player**: enter the TV's MAC → status card must show the
   device, trial state, expiry, `******` for the Device Key (never the
   real key) and empty "activated by".
2. **Activate 1 year (1 credit)** → status flips to licensed, balance
   drops, the box's Settings shows the license on next open.
3. **Add playlist** to that MAC (the fake server again) → box picks it
   up on "check now" / Playlists → Refresh portal.
4. **Switch MAC**: `pm clear` the app (it becomes a brand-new device with
   a NEW MAC — that's by design), read the new MAC off the TV, then move
   the license from the old MAC to the new one → old shows revoked, new
   shows licensed.
5. Sanity: activating with 0 credits left must refuse politely.

## 5. What to report

- Screenshots of anything cramped, truncated, mis-focused or confusing on
  the real TV — especially the setup screen and Hero home.
- Playback: time-to-picture when zapping, any stalls, remote keys that do
  nothing.
- Any error message that contains a server address, credential or token —
  that is a bug by definition, report it immediately.
- Anything in the two portals a non-technical seller would stumble on.

Portal logs live in the uvicorn terminal; app-side:
`adb logcat -s SyncRepository` for sync, and the full logcat for crashes.
