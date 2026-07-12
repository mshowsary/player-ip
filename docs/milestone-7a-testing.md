# Milestone 7A — managed access lifecycle test plan

This milestone hardens the boundary between **managed portal access** and **personal playlist access**. It does not add a production portal backend and it does not require a domain name for the normal debug checks.

## Branch relationship

Milestone 7A is stacked on the still-unapproved Milestone 6B responsive fix:

```text
fix/6b-sync-health-responsive-layout
└── milestone/7a-managed-access-lifecycle
```

Nothing from either branch is accepted into `develop` or `main` by this test.

## Install-over-existing regression

1. Keep the existing app data.
2. Install the Milestone 7A debug build over the current installation.
3. Confirm the app starts without a crash.
4. Confirm existing playlists, active playlist, catalogue, bookmarks, progress and settings remain.
5. Open Live TV and confirm playback still works.
6. Confirm the corrected **Synchronization & device health** layout remains usable in portrait and landscape.

## Debug managed-policy stability

Open **Settings → Managed access**.

1. Select **Paused** in **Debug policy preview**.
2. Leave Settings, browse another available area, background the app for at least 20 seconds, then return.
3. Confirm the policy is still **Managed access paused** and was not silently replaced by the debug mock.
4. Try opening Live TV, Movies and Series from Home, navigation and any available back-stack route.
5. Confirm guarded streaming routes remain blocked and the restriction screen is readable.
6. Repeat with **Revoked** and confirm the state remains revoked.

## Explicit refresh exits the preview

1. While **Paused** or **Revoked** is selected, press **Refresh managed access**.
2. Confirm the temporary debug preview is cleared deliberately.
3. In the normal debug mock build, the policy should return to the mock portal policy after the refresh completes.
4. Confirm the app does not crash and the permitted destinations return.

## Personal preview

1. Select **Personal** in the debug policy preview.
2. Confirm Live TV, Movies and Series are not blocked by managed policy.
3. Confirm this preview remains stable until **Refresh managed access** is pressed.

The Personal debug preset exercises the resulting UI policy only. The production transition to personal mode still occurs only through an explicit portal disconnect action.

## Offline behavior

1. Select **Paused** or **Revoked**.
2. Disable Wi-Fi and mobile data.
3. Close and reopen the app.
4. Confirm the last blocked policy remains enforced offline.
5. Restore networking and press **Refresh managed access**.

A temporary network error must not convert managed access into personal access.

## Hidden lifecycle checks covered by unit tests

The Android CI unit tests verify that:

- A newly managed response with no policy fails closed as suspended.
- An omitted policy preserves the last-known managed decision.
- A valid portal policy replaces the previous decision.
- Revocation blocks Live TV, Movies and Series and preserves the provider reference/revision.
- Display messages and support codes are length-bounded.
- Only an explicit disconnect produces unrestricted personal access.

The following production paths require a compatible portal backend or dedicated integration fixture and are not claimed as physically validated by the debug mock:

- Refresh-token `401/403` revocation.
- Authorized-playlist `401/403` revocation.
- Unreadable encrypted token envelope after a device/Keystore mismatch.
- Legacy portal-key `403` revocation.

## Pass criteria

- No crash or data loss on install-over-existing.
- Debug Paused/Revoked/Personal previews remain stable until manual refresh.
- Manual refresh deliberately restores the mock portal decision.
- Managed blocked states remain enforced offline and across app restart.
- Live playback and the Milestone 6B responsive settings fix remain intact.
- CI passes debug and release tests, builds and lint.
