# Managed access policy contract

The portal resolves tenant, reseller, subscription, country and device rules on the server and returns the resulting device policy to NovaPlay. The Android client does not decide a customer country or reseller entitlement by itself.

## Payload

The pairing approval response and `GET /api/v1/device/playlists` may include:

```json
{
  "policy": {
    "status": "active",
    "allow_live": true,
    "allow_movies": true,
    "allow_series": true,
    "message": "Managed services are active on this device.",
    "support_code": "ACCOUNT-REFERENCE",
    "policy_revision": 42
  }
}
```

Supported lifecycle states:

- `active`: each service is controlled by its corresponding entitlement boolean.
- `suspended` or `paused`: all managed streaming services are blocked temporarily.
- `revoked` or `disabled`: all managed streaming services are blocked until the device is paired or enabled again.
- Unknown values fail closed and are treated as suspended.

## Missing policy behavior

A personal installation that has never entered a managed portal session remains unrestricted.

Once a response belongs to a managed session, an omitted policy can never silently switch the app to personal access:

- If a last-known managed policy exists, NovaPlay preserves it so offline enforcement remains stable during a backward-compatible portal rollout.
- If the device has just entered a managed session and no managed policy has ever been stored, NovaPlay installs a suspended fail-closed policy until the portal returns an explicit decision.

## Session lifecycle

Portal credentials and managed policy are related but have different storage lifecycles:

- A successful policy response replaces the current managed decision.
- A missing or unreadable refresh token, token refresh `401/403`, authorization `401/403`, or installation mismatch clears the bearer tokens and persists a local `revoked` policy.
- An unreadable encrypted token envelope is treated as a revoked managed session, not as a reason to fall back to the legacy MAC/key flow.
- Temporary network failures and server `5xx` responses preserve the last-known policy.
- Only an explicit user disconnect returns the policy to `unmanaged` personal access.

Cached managed catalogues may remain on disk after revocation so a later repair does not destroy user data, but guarded Live, Movies, Series, details and player routes remain blocked.

## Enforcement

The client applies the policy in three places:

1. Home removes unavailable streaming destinations and shows a managed-access notice when access is restricted.
2. Phone/tablet navigation omits unavailable top-level services.
3. Direct routes, deep links and player routes are guarded by a restriction screen, so hiding a button is never the only enforcement.

The Settings screen shows the current status, service entitlements, policy revision, provider message and support code. It can request a fresh portal bootstrap.

Debug policy presets remain stable until the tester explicitly presses **Refresh managed access**. Automatic mock refreshes do not silently overwrite the selected preview state.

## Server responsibilities

- Enforce subscription, tenant, reseller, country and adult-content rules before returning playlists or stream credentials.
- Return an explicit policy on pairing approval and authorized playlist responses.
- Revoke device tokens when a device is revoked.
- Return `401` or `403` consistently for revoked or invalid device credentials.
- Increment `policy_revision` whenever effective access changes.
- Keep provider messages short and free of secrets.
- Never rely on Android UI hiding as the only authorization control.
- Continue rejecting unauthorized playlist and token requests server-side.

Category-level adult filtering is intentionally not inferred from category names. A later contract will use explicit server-provided category/content identifiers so localization and naming differences cannot bypass policy.
