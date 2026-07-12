# NovaPlay 1.0.0-rc.1

NovaPlay 1.0.0-rc.1 is the first internally packaged release candidate for the rebuilt Android phone, tablet and TV player. It is intended for collaborator validation, not public distribution.

## Highlights

- Personal Xtream and M3U playlist creation, editing, testing, synchronization and local-file import.
- Encrypted playlist credentials and portal tokens using Android Keystore-backed AES-GCM storage.
- Stable content identities that preserve bookmarks, recents and playback progress across catalogue refreshes.
- Responsive phone, tablet, foldable and Android TV layouts with touch and D-pad interaction modes.
- Adaptive Live TV, Movies and Series browsing with category search, bookmarks and paging.
- Bounded Live and VOD playback recovery, buffering watchdogs, stream fallback, resume rules and persistent audio/subtitle preferences.
- Secure device-code portal pairing and managed-access policy foundations.
- Fail-closed managed lifecycle behavior for paused, revoked or unreadable sessions.
- Constrained background synchronization and privacy-safe Sync & health diagnostics.
- Release hardening, backup exclusion, debug/release source separation and protected portal transport.
- Shared readable error handling, TalkBack semantics and focus restoration.
- English, French and Arabic localization foundation with RTL support.
- Versioned APK/AAB packaging with SHA-256 checksums and a privacy-safe release manifest.

## Upgrade behavior

The candidate is designed to install over the existing tested application without clearing data. Existing playlists, encrypted credentials, catalogue data, active selection, bookmarks, recents, viewing progress and settings should remain available.

Application backup and device-transfer restoration remain disabled because encrypted credentials and device-bound keys must not be restored onto another installation.

## Important boundaries

- A production provider/reseller backend is not included. Managed pairing and policy features remain an Android client foundation plus debug/test behavior until a real backend is implemented and independently secured.
- CI produces an unsigned release APK/AAB. Public or external distribution requires signing from a protected environment.
- French and Arabic cover the shared shell and critical common states; some legacy catalogue, player and detailed Settings wording may remain English.
- Provider-supplied playlist names, category names, titles, policy messages and support references are displayed as supplied.
- Stream availability, codecs, metadata quality and server behavior remain dependent on the user's authorized playlist provider.

## Internal acceptance requirements

- Android CI succeeds on the exact candidate commit.
- Both collaborators test and approve the same commit.
- Install-over-existing causes no crash or data loss.
- Playlist synchronization and Live playback work.
- VOD playback is checked when suitable content is available.
- Managed Paused and Revoked states remain enforced.
- Phone portrait/landscape and forced TV/remote navigation remain usable.
- APK/AAB SHA-256 checksums match `SHA256SUMS`.
- The release manifest contains no URL, credential, token, device identifier or signing secret.

## Not yet approved for

- Google Play or another public store
- Customer distribution
- Production provider/reseller onboarding
- Production billing, subscriptions or account management
- Production claims about portal availability or service uptime
