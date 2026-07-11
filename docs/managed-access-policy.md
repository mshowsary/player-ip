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

A missing policy keeps the current policy during a backward-compatible portal rollout. A new installation with no policy remains unrestricted for personal playlists.

## Enforcement

The client applies the policy in three places:

1. Home removes unavailable streaming destinations and shows a managed-access notice when access is restricted.
2. Phone/tablet navigation omits unavailable top-level services.
3. Direct routes, deep links and player routes are guarded by a restriction screen, so hiding a button is never the only enforcement.

The Settings screen shows the current status, service entitlements, policy revision, provider message and support code. It can request a fresh portal bootstrap.

## Server responsibilities

- Enforce subscription, tenant, reseller, country and adult-content rules before returning playlists or stream credentials.
- Revoke device tokens when a device is revoked.
- Increment `policy_revision` whenever effective access changes.
- Keep provider messages short and free of secrets.
- Never rely on Android UI hiding as the only authorization control.
- Continue rejecting unauthorized playlist and token requests server-side.

Category-level adult filtering is intentionally not inferred from category names. A later contract will use explicit server-provided category/content identifiers so localization and naming differences cannot bypass policy.
