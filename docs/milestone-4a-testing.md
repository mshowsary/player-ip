# Milestone 4A — secure portal pairing foundation testing

Milestone 4A introduces the client protocol, installation Device ID, token encryption and legacy migration path. The new pairing screen is intentionally deferred to Milestone 4B, so the current activation and personal-playlist experience must remain unchanged.

Install this branch over the existing application. Do not clear app data.

## Regression smoke test

1. Launch the app and confirm it does not crash.
2. Confirm existing personal and managed playlists remain present.
3. Open Live TV and confirm existing channels browse and play.
4. Open Playlists, edit an existing personal playlist and confirm encrypted values remain available.
5. Test and synchronize the playlist.
6. Disable networking, synchronize, and confirm the existing catalogue remains available.
7. Re-enable networking and synchronize successfully.

## Milestone 3D visual regression check

1. Open Add playlist in portrait and landscape.
2. Confirm the dialog has comfortable side margins and does not feel compressed.
3. Confirm Save and synchronize, Test connection and Cancel each use a full-width row.
4. Confirm Test connection is fully visible and never wraps or truncates.
5. Force TV / remote mode on the phone.
6. Tap a different playlist card or Settings option.
7. Confirm focus moves to the touched item immediately and only one focus selector remains visible.
8. Navigate with a keyboard or controller and confirm the same focus outline moves normally.

## Protocol unit tests

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

The tests verify user-code normalization, polling bounds, server slow-down handling, approved-token expiry and failure when an approval response omits its access token.

## Scope note

No production portal is connected in this milestone. Debug builds continue using the existing mock assignment path, while release builds keep the legacy endpoint as a temporary fallback. Milestone 4B will connect the new pairing session to the activation UI and remove the visible key as the long-term authentication mechanism.
