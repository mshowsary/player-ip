# Milestone 7C — localization and RTL foundation test plan

This milestone adds Android-declared support for English, French and Arabic, localizes the shared navigation shell, Home hub, common errors/bookmark actions, managed-access restriction screen, and the synchronization-health entry/title. Arabic also exercises right-to-left layout mirroring.

It does not claim a complete translation of every legacy catalogue, playlist, player or detailed Settings label. Provider-supplied playlist names, category names, content titles, policy messages and support references are displayed exactly as supplied by the provider.

## Branch position

```text
develop
└── milestone/6b-release-hardening
    └── fix/6b-sync-health-responsive-layout
        └── milestone/7a-managed-access-lifecycle
            └── milestone/7b-accessibility-readable-errors
                └── milestone/7c-localization-rtl-foundation
```

Nothing from this stack is merged into `develop` or `main` by this test.

## Install-over-existing regression

1. Keep the current app data.
2. Install the Milestone 7C debug build over the existing installation.
3. Confirm the app starts without a crash.
4. Confirm playlists, active selection, catalogue, bookmarks, progress and settings remain.
5. Confirm Live playback still works.
6. Confirm managed-access Paused and Revoked previews from Milestone 7A still block guarded routes.
7. Confirm the responsive synchronization-health layout remains intact.

## Language selection

### Android 13 and newer

Open the device's per-app language settings for NovaPlay:

```text
Android Settings → Apps → NovaPlay → Language
```

The available languages should include:

- English
- French
- Arabic

### Android 12 and older

Temporarily change the device language. NovaPlay follows the device locale on these versions.

## French test

Select French and relaunch NovaPlay when Android requests it.

Confirm the following are translated:

- Bottom navigation or tablet navigation rail.
- Home cards.
- Home synchronization failure/status text when visible.
- Shared Retry action and standard error messages.
- Bookmark add/remove state announced by TalkBack.
- Managed-access blocked screen title and actions.
- Synchronization and device-health button and dialog title.

Confirm long French labels do not overlap adjacent navigation items. Ellipsis is acceptable in the compact bottom bar; actions must remain identifiable and reachable.

## Arabic and RTL test

Select Arabic and relaunch NovaPlay.

Confirm:

- Shared navigation and Home labels are Arabic.
- Rows, navigation rail placement, Start/End padding and dialogs mirror into RTL naturally.
- Icons remain aligned with their labels.
- Home cards, managed-access notices and blocked screens remain readable.
- Dynamic Latin text such as playlist names, support codes and the NovaPlay wordmark does not cover Arabic text.
- Bottom navigation labels do not overlap.
- Portrait and landscape remain usable.
- Touch and forced TV/remote navigation both work.

The content order may mirror in RTL, but D-pad focus must remain predictable and every action must still be reachable.

## Return to English

Switch NovaPlay back to English and confirm:

- English labels return.
- No playlist or preference is reset.
- The app does not remain stuck in RTL.
- Live playback and Settings still work.

## Accessibility regression

With French or Arabic active, enable TalkBack briefly and confirm:

- Shared buttons are announced as buttons.
- Poster title/bookmark state announcements use the active locale for shared bookmark wording.
- Retry is announced in the active locale.
- Dialog headings remain recognized as headings.

## Pass criteria

- No crash or data loss when changing locale.
- English, French and Arabic appear in Android's supported app-language list on Android 13+.
- Shared shell/Home/error/bookmark/managed-block/sync-health strings switch language.
- Arabic mirrors layout without clipping or inaccessible focus.
- Returning to English restores LTR correctly.
- Debug and release tests, builds and lint pass.
