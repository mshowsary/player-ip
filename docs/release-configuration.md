# NovaPlay release configuration

## Provider portal URL

The production control-plane URL is supplied at build time. It must be an HTTPS origin and must not contain embedded credentials, query parameters, or a fragment.

Use either a Gradle property:

```powershell
.\gradlew.bat :app:assembleRelease -PnovaplayPortalBaseUrl=https://portal.your-domain.example
```

or an environment variable:

```powershell
$env:NOVAPLAY_PORTAL_BASE_URL = "https://portal.your-domain.example"
.\gradlew.bat :app:assembleRelease
```

When no real portal URL is supplied, the app still builds and personal Xtream/M3U mode remains available, but managed pairing reports that the provider portal is not configured.

Debug builds may use HTTP only for local development hosts such as `localhost`, `127.0.0.1`, or the Android emulator host bridge. Production portal traffic is always HTTPS-only.

## IPTV stream compatibility

NovaPlay continues to permit user-supplied HTTP playlist and stream sources because many legitimate Xtream and MPEG-TS providers have not migrated all delivery endpoints to HTTPS. This exception does not apply to the provider control plane: portal requests use a dedicated client that rejects another host, an unsafe scheme, and redirects.

## Mock data separation

Mock activation code and development assignments exist only in the debug source set. The release source set contains no mock playlist assignment. CI runs release-specific tests to verify this boundary.

Provider credentials must never be committed as production configuration. Use personal playlist entry for local testing or connect a protected development portal.

## Signing

The generated release APK is unsigned until a private signing configuration is supplied outside the repository. Signing keys, aliases, and passwords must remain in a secure secret manager or protected local environment and must not be committed.

## Backup and transfer

Application backup is disabled. Explicit Android backup and device-transfer rules also exclude databases, preferences, files, encrypted tokens, playlist details, viewing history, and temporary data.

## CI release verification

Pull requests build and test both variants:

- Debug unit tests, compilation, and lint
- Release unit tests, minified compilation, and lint
- Debug APK artifact
- Unsigned release APK artifact
- Room schema artifact

A successful unsigned release build verifies source-set separation, R8 rules, resource shrinking, release-only compilation, and production manifest merging. It is not a distributable signed release.
