# Milestone 12B — QR codes and phone-entry playlists

Two self-service features on top of the verified 12A setup (same servers,
same `adb reverse` wiring, same live-portal build flags):

```powershell
adb reverse tcp:8000 tcp:8000
adb reverse tcp:8899 tcp:8899
.\gradlew installDebug -PnovaplayMockActivation=false "-PnovaplayPortalBaseUrl=http://127.0.0.1:8000"
```

Portal running in `novaplay-management` (`alembic upgrade head` first, then
uvicorn) and `python tools\mock_server.py` for content.

## 1. Pairing QR (managed flow)

1. Clear app data (`adb shell pm clear com.novaplay.tv`), open the app.
2. The activation screen now shows a **QR code** under the pairing code.
3. Scan it with your phone's camera (the other phone, or close the app and
   scan your own screen from the portal browser device): it must open
   `http://127.0.0.1:8000/activate?code=XXXX-XXXX`.
   - Logged out → the friendly customer landing page with the code shown and
     the WhatsApp share button.
   - Logged in as staff → the approval screen with the code prefilled.
   - NOTE: on a second phone `127.0.0.1` won't resolve — this URL check is
     easiest from the PC browser. In production the QR carries the real
     portal domain and any phone opens it.

## 2. Add a playlist from your phone (self-service)

1. Complete a pairing (or use personal mode) so you reach Home, then open
   **Playlists**.
2. Tap **Add from your phone** — a panel shows a fresh code + QR and
   "Waiting for your playlist…".
3. On the PC browser, open `http://127.0.0.1:8000/add-playlist`, type the
   code (any format), confirm it shows your device's name, and enter:
   - Type: Xtream · server `http://127.0.0.1:8899` · any username/password.
4. Submit → the phone's panel should close **by itself within ~5 s**, the
   playlist appears in the list, sync runs, channels play.
5. Repeat with an M3U link: `http://127.0.0.1:8899/playlist.m3u`.
6. Cancel path: open the panel, press Cancel — no playlist is added; the
   code dies at its 15-minute expiry.
7. Privacy check (portal side): after pickup the delivery row keeps no
   credential columns (they are wiped) — visible in the portal DB if you
   care to look; nothing about your playlist remains.

## Regression sweep

- The classic on-TV editor ("Add playlist") and M3U import still work.
- With no portal configured (plain `.\gradlew installDebug`), the
  "Add from your phone" button does not appear at all.
- TV mode: the new button is reachable by D-pad in the header row.
