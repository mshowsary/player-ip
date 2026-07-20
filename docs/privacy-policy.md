# Privacy Policy — NovaPlay (draft template)

> **Owner to-do before launch**: replace the placeholders (company name,
> contact address, domain), have it reviewed for your jurisdictions, host it
> at `https://<your-domain>/privacy`, and reference that URL in the Google
> Play Data Safety form and the Amazon listing. This draft describes what the
> app actually does as of this repository state — keep it in sync with code.

_Last updated: 2026-07-20_

## Who we are

NovaPlay ("the app") is published by **[COMPANY NAME]**, reachable at
**[CONTACT EMAIL]**. The app is a media player: it ships with no channels,
films or series. All content comes from playlists the user (or their
reseller) configures.

## Data the app stores on the device

- Playlists the user adds (server addresses, usernames, passwords, M3U and
  EPG links) — encrypted with hardware-backed Android Keystore keys before
  being written to storage.
- Catalogue data synced from the user's own playlist sources (channel and
  title lists, guide data), bookmarks, viewing history and resume positions.
  These never leave the device. Viewing history can be cleared in Settings.
- App settings (interface, subtitles, time format, parental-control PIN as a
  salted hash — never in plain text).

The app requests only the Internet and network-state permissions. It has no
access to contacts, location, microphone, camera or files.

## Data sent to our activation service

When the player registers for its free trial or license check, it sends to
the publisher's portal:

| Data | Purpose |
|---|---|
| Installation ID and device key | Identifying this installation for its trial/license |
| Pseudo "MAC Address" label (app-generated, **not** the hardware MAC) | The support and activation identity shown on screen |
| Device model and platform (e.g. "Xiaomi MI TV, android") | Support and fraud prevention |
| App brand and version | Serving the right build and updates |
| IP address (as with any internet request) | Rate limiting and abuse prevention |

Playlists managed through the portal (added by the user on the web portal or
by their reseller) are stored on the portal encrypted at rest and delivered
only to the authenticated device.

**What we do not collect**: viewing behavior, played channels or titles,
search queries, advertising identifiers. The app contains no advertising or
analytics SDKs.

## Retention and deletion

Device records and portal-managed playlists are kept while the installation
is active. To delete them, contact **[CONTACT EMAIL]** quoting the device
code shown in Settings → This device; we remove the device record and its
playlists. Local data is deleted by uninstalling the app or clearing its
storage.

## Third parties

The app talks only to (a) the playlist/EPG servers the user configures —
which are chosen by the user and governed by those providers' own terms —
and (b) the publisher's portal. Nothing is sold or shared with data brokers.
Purchases made through Google Play or Amazon are processed by those stores
under their own privacy terms.

## Children

The app offers parental controls but is not directed at children. Content
availability is entirely determined by the playlists configured by the
account holder.

## Changes

Material changes to this policy will be announced in the app's release notes
and on this page.
