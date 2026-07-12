# Milestone 7D — interaction and focus consistency test plan

This milestone is the final shared touch, keyboard, accessibility and TV/remote interaction pass before release-candidate work. It does not add provider features or a production portal backend.

## Branch position

```text
develop
└── milestone/6b-release-hardening
    └── fix/6b-sync-health-responsive-layout
        └── milestone/7a-managed-access-lifecycle
            └── milestone/7b-accessibility-readable-errors
                └── milestone/7c-localization-rtl-foundation
                    └── milestone/7d-interaction-focus-consistency
```

Nothing from this stack is merged into `develop` or `main` by this test.

## What changed

- Bottom navigation and tablet rails use one combined spoken label per destination.
- The active top-level destination exposes a selected state to accessibility services.
- Managed policy filtering uses one deterministic, unit-tested destination order.
- Home cards use one combined spoken action label instead of announcing icon and text separately.
- Compact category chips and category rows expose selected state consistently.
- Opening a large category picker puts TV/remote focus on the first result.
- Closing or selecting from the category picker returns focus to the Categories trigger.
- Closing Synchronization and device health returns TV/remote focus to its opener.

## Install-over-existing regression

1. Keep the current application data.
2. Install the Milestone 7D debug build over the existing app.
3. Confirm the app starts without a crash.
4. Confirm playlists, catalogue, active playlist, bookmarks, progress and settings remain.
5. Confirm Live playback still works.
6. Confirm French/Arabic selection from Milestone 7C remains available.
7. Confirm Paused and Revoked managed previews still enforce guarded routes.

## Touch navigation

On a phone in Auto or Touch mode:

1. Move repeatedly between Home, Live, Movies, Series and Settings.
2. Confirm one tap performs one navigation action.
3. Confirm the active destination remains visibly highlighted.
4. Rotate between portrait and landscape and repeat.
5. Confirm hidden managed destinations do not leave empty gaps when using Live-only, Paused or Revoked debug policies.

## TalkBack navigation

Enable TalkBack briefly.

1. Move through the bottom navigation or tablet rail.
2. Confirm each destination is announced once, not once for the icon and again for the text.
3. Confirm the current destination is announced as selected.
4. Move across Home cards and confirm each card title is announced once.
5. Confirm double-tap opens exactly the announced destination.
6. Repeat in French or Arabic for the localized shared labels when practical.

## Category picker focus

Use a playlist with more than eight provider categories so the compact **Categories** control appears.

In forced TV/remote mode or with a hardware keyboard:

1. Focus Categories and press OK/Enter.
2. Confirm focus enters the dialog and lands on the first category result.
3. Move through several rows and select one.
4. Confirm the dialog closes and focus returns to Categories.
5. Reopen the dialog and press Back.
6. Confirm focus again returns to Categories.
7. Confirm selected chips and rows remain visually correct and TalkBack reports their selected state.

## Synchronization and device health focus

In forced TV/remote mode:

1. Open Settings.
2. Focus **Synchronization & device health** and open it.
3. Confirm focus begins on the first automatic-refresh choice.
4. Navigate through all choices and actions.
5. Press Back to close the dialog.
6. Confirm focus returns to **Synchronization & device health**, not to an unrelated setting or nowhere.
7. Reopen and close by the normal dismiss action and confirm the same result.

## Managed-policy navigation order

Test these debug previews:

- Full access: Home, Live, Movies, Series and Settings are present in stable order.
- Live only: Home, Live and Settings remain in stable order.
- Paused or Revoked: only Home and Settings remain in the touch shell.
- Personal: all destinations return.

Home and Settings must always remain reachable.

## Pass criteria

- No crash or data loss on install-over-existing.
- One touch or accessibility activation causes one action.
- Navigation and Home controls are announced once.
- Active destinations and selected categories expose selected state.
- Category and health dialogs restore focus to their exact openers.
- Touch, keyboard and TV/remote navigation remain predictable in portrait and landscape.
- Managed destination filtering matches the policy without changing order.
- Debug and release tests, builds and lint pass.
