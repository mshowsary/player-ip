# Milestone 7B — accessibility, readable errors and localization foundation

This milestone improves shared UI semantics and creates a localizable, privacy-safe error presentation layer. It is intentionally a foundation rather than a claim that every screen is fully translated.

## Branch position

```text
milestone/7a-managed-access-lifecycle
└── milestone/7b-accessibility-readable-errors
```

Nothing from this branch is merged into `develop` or `main` by testing it.

## Install-over-existing regression

1. Keep the existing application data.
2. Install the Milestone 7B debug build over the current app.
3. Confirm playlists, active catalogue, bookmarks, progress and settings remain.
4. Confirm Home, Live TV, Movies, Series, Playlists and Settings open normally.
5. Play one Live channel and one available VOD item when possible.
6. Confirm the Milestone 6B responsive Sync & health layout and Milestone 7A policy previews still work.

## TalkBack / screen-reader test

Enable Android **TalkBack** or the device's screen reader.

### Shared buttons

1. Move through Home and Settings actions.
2. Confirm each shared button is announced once as a button.
3. Confirm the visible label is the spoken label.
4. Confirm tapping or double-tapping performs the same action as before.

### Movie and series posters

1. Open Movies or Series.
2. Move screen-reader focus across several posters.
3. Confirm each poster announces its title and optional subtitle without reading the poster image a second time.
4. Confirm bookmarked posters announce **Bookmarked** and others announce **Not bookmarked** when bookmarking is available.
5. Use the corner bookmark action on a touch device and confirm it is announced as **Add bookmark** or **Remove bookmark**.
6. In forced TV mode, confirm normal D-pad focus still skips the corner badge and long-press bookmark behavior remains available.

### Dialog headings

1. Open Categories, track selection or Sync & health dialogs.
2. Confirm the dialog title is exposed as a heading.
3. Confirm Back and outside-tap dismissal still work where supported.

## Readable error test

Create one or more safe failure conditions:

- Disable networking before loading content that is not cached.
- Attempt a synchronization while offline.
- Open an intentionally unavailable test stream.

Confirm:

1. Shared error screens use a short understandable message rather than a Java/OkHttp/Retrofit exception.
2. The Retry action is announced as a button and receives initial focus in TV mode.
3. Error text is announced as an assertive live update when it appears.
4. No provider URL, username, password, bearer token, access token, refresh token, device key or device ID appears in the UI or copied support information.
5. Restoring the connection and pressing Retry works normally.

Not every player-specific terminal message is converted in this milestone; the new policy covers shared error states and establishes the translation/redaction contract for later screens.

## Large text test

Set Android **Font size** to a larger value, preferably 130–150%.

Check:

- Home action cards.
- Shared Retry buttons.
- Movies/Series posters.
- Settings and Sync & health.
- One dialog in portrait and landscape.

Text may wrap where appropriate, but important actions must remain reachable and the app must not crash.

## Localization readiness check

The shared Retry, bookmark states/actions and standard error messages now come from Android string resources. Verify the visible English wording is unchanged and no resource placeholder is shown.

A full French/Arabic translation and complete migration of all legacy hardcoded screen text remain separate work; this milestone creates the safe shared mechanism first.

## Pass criteria

- No crash or data loss during upgrade.
- Shared buttons have correct button semantics.
- Poster cards announce one useful combined label plus bookmark state.
- Bookmark actions remain usable by touch and TV remote.
- Common error screens show localizable, privacy-safe messages.
- Dialog titles expose heading semantics.
- Large font settings do not make core actions unreachable.
- Debug and release builds, tests and lint pass.
