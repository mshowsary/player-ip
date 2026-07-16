# Milestone 11B — release channels (in-app update) test plan

The sideload distribution channel: brands that ship outside a store configure
`brand.updateUrl`, and the app gains a "Check for updates" action under
Settings → This device. The check is read-only and fail-closed: the app never
downloads or installs anything itself — an available update surfaces a button
that opens the download link in the browser. Store-distributed brands leave
the URL unset and see nothing.

## Branch position

```text
develop (…, 11A white-label brand packs)
└── milestone/11b-release-channels
```

## What changed

- `brand.updateUrl` (optional, per brand) → validated at build time, overridable
  with `-PnovaplayUpdateUrl` / `NOVAPLAY_UPDATE_URL` for testing.
- Pure `UpdateCheckPolicy` (unit-tested): a prompt appears only for a
  well-formed manifest with a strictly newer `version_code` and an HTTPS
  download link (local HTTP allowed in debug builds only). Anything malformed
  fails closed.
- `UpdateRepository`: bounded manifest fetch, sanitized errors (the update URL
  never appears in messages), no side effects.
- Settings → This device: check button, up-to-date/failure status, and a
  prominent "Get version X" button that opens the link externally.
- Mock server serves `/updates.json` with a always-newer mock version.

## Build and automated checks

```powershell
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest
./gradlew :app:lintDebug :app:lintRelease
./gradlew :app:assembleDebug :app:assembleRelease
```

New unit tests: `UpdateCheckPolicyTest`.

## Update-channel checks (mock server)

```powershell
python3 tools/mock_server.py
./gradlew installDebug -PnovaplayUpdateUrl=http://10.0.2.2:8899/updates.json
```

1. Settings → This device now shows "Check for updates". Press it: "Version
   99.0.0-mock is available" appears with the mock notes and a
   "Get version 99.0.0-mock" button; pressing it opens the download link
   outside the app.
2. Stop the mock server, check again: a readable failure appears — no crash,
   no URL leaked in the message.
3. Default build (`./gradlew installDebug`, no property): the panel shows no
   update section at all — the default brand has no update URL.

## Fail-closed spot checks

Serve a manifest with `version_code` equal/below the installed build (edit
`tools/mock_server.py` temporarily): the check reports "latest version".
An `apk_url` pointing at remote HTTP must report a failure, never a prompt.

## Approval record

```text
Tester 1 approved: <SHA>
Tester 2 approved: <same SHA>
CI passed:         <same SHA>
```

## Out of scope (deliberate)

Store packaging (Play/Amazon listings, data-safety forms) and per-brand
signing are account-holder work driven by `docs/release-configuration.md`;
this milestone ships the technical update channel only.
