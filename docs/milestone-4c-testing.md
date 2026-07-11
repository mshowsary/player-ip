# Milestone 4C — managed access and service policy testing

Install this branch over the current app without clearing data.

## Upgrade smoke test

1. Launch the app and confirm existing playlists remain.
2. Open Live TV and play a channel.
3. Confirm Movies, Series, Playlists and Settings still open normally.
4. Confirm the bottom bar or side rail remains visually stable.

## Managed access panel

Open **Settings → Managed access**.

1. Confirm the panel shows the current status, Live TV, Movies and Series availability.
2. Confirm the policy revision and support code are readable when present.
3. Press **Refresh managed access** and confirm a temporary result message appears.
4. Confirm no option overlaps or changes size in portrait, landscape or TV mode.

## Debug policy previews

These controls exist only in debug builds.

### Live only

1. Select **Live only**.
2. Return to Home.
3. Confirm Live TV remains visible.
4. Confirm Movies and Series are removed from Home and touch navigation.
5. Navigate directly to Movies or Series using back-stack history if possible.
6. Confirm a polished unavailable-service screen appears instead of the catalogue.
7. Confirm Playlists and Settings remain accessible.

### Paused

1. Select **Paused**.
2. Return to Home.
3. Confirm a managed-access warning appears.
4. Confirm Live TV, Movies and Series are unavailable.
5. Confirm Home, Playlists and Settings remain usable.
6. Open a previously visited Live, movie or series route through history and confirm the restriction screen blocks it.
7. Confirm the support code is visible on the restriction screen.

### Revoked

1. Select **Revoked**.
2. Confirm the wording changes to revoked and all managed streaming sections remain blocked.
3. Confirm the app does not delete local playlists or crash.

### Restore

1. Select **Full access**.
2. Confirm Live TV, Movies and Series return immediately.
3. Select **Personal** and confirm all services remain available without a managed warning.
4. Press **Refresh managed access** to restore the debug portal's normal managed policy.

## TV / remote mode

1. Force **TV / remote** mode.
2. Navigate through the Managed access panel with a keyboard, controller or D-pad.
3. Confirm focus remains on one item at a time.
4. Change from Full access to Live only and back.
5. Confirm Home focuses the first available card and never loses focus because Live, Movies or Series disappeared.

## Unit tests

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

The tests cover unrestricted personal access, service entitlements, suspended/revoked states, fail-closed unknown states and bounded portal messages.
