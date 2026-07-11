# NovaPlay secure portal pairing contract

This document defines the application-side contract for the provider and reseller portal. The real portal implementation must follow the same security properties even if endpoint names change before production.

## Security model

The Android application owns a random installation `device_id`. It is generated once and persisted through application upgrades. A MAC address may be supplied only as a legacy support/display hint; it is not an authentication secret.

Pairing uses two different values:

- `user_code`: short, human-readable and safe to display on a television. It is entered on the portal and cannot authorize API requests by itself.
- `session_secret`: high-entropy value returned only to the device. It authorizes polling for that single short-lived pairing session and must never be displayed, logged or placed in a URL.

After the reseller or customer approves the pairing, the portal returns a revocable access token and refresh token. Tokens are bound to `device_id`, stored with Android Keystore encryption and sent only through an `Authorization: Bearer` header over HTTPS.

## Create a pairing session

`POST /api/v1/pairing/sessions`

Example request:

```json
{
  "device_id": "random-installation-uuid",
  "legacy_mac": "optional-support-identifier",
  "device_name": "Manufacturer Model",
  "platform": "android",
  "app_version": "1.0.0",
  "capabilities": ["xtream", "m3u", "live", "vod", "series"]
}
```

Example response:

```json
{
  "session_id": "opaque-session-id",
  "user_code": "ABCD-2345",
  "verification_uri": "https://portal.example.com/activate",
  "expires_at": 1783792800,
  "interval_seconds": 5,
  "session_secret": "high-entropy-single-session-secret"
}
```

Server requirements:

- Expire unused sessions after approximately 5–10 minutes.
- Store only a hash of `session_secret`.
- Rate-limit creation by IP, device and account.
- Do not reveal whether a reseller/customer account exists through error wording.
- A code must be single-use and invalid immediately after approval, denial or expiry.

## Poll pairing status

`GET /api/v1/pairing/sessions/{session_id}`

Required header:

```text
X-Pairing-Secret: <session_secret>
```

Supported response statuses are `pending`, `slow_down`, `approved`, `denied` and `expired`.

On approval, return device tokens and optionally the first managed playlist snapshot:

```json
{
  "status": "approved",
  "access_token": "short-lived-access-token",
  "refresh_token": "rotating-refresh-token",
  "expires_in": 3600,
  "playlists": []
}
```

The client clamps polling to 3–30 seconds and adds five seconds after `slow_down` or HTTP 429.

## Authorized device endpoints

`GET /api/v1/device/playlists`

```text
Authorization: Bearer <access_token>
```

`POST /api/v1/device/token/refresh`

```json
{
  "device_id": "random-installation-uuid",
  "refresh_token": "rotating-refresh-token"
}
```

Refresh tokens should rotate on every successful refresh. Revoking a device in the portal must invalidate both current access and refresh tokens.

## Production requirements

- HTTPS is mandatory. Do not permit cleartext portal traffic in release builds.
- Never put access tokens, refresh tokens, session secrets or provider credentials in query parameters.
- Redact authorization headers and secrets from application and server logs.
- Return generic errors to the device; detailed diagnostics belong in protected server logs.
- Apply tenant, reseller, country, adult-content and playlist policies on the server. The Android client must not be the only enforcement point.
- Audit pairing approval, token refresh, playlist assignment, policy changes and device revocation.

## Migration

The current app retains the legacy MAC + visible device-key endpoint only as a temporary compatibility path. Once the real portal and pairing UI are deployed together, that endpoint should be removed from the application and server.
