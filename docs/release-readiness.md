# NovaPlay release readiness

`tools/release_readiness.py` is the final local and CI guard after `tools/package_release.py`. It validates the tracked repository, the exact package file set, source-commit traceability, manifest privacy, artifact sizes and SHA-256 checksums.

## Internal candidate

```powershell
.\gradlew.bat :app:verifyReleaseCandidate
python tools\package_release.py --output dist\release-candidate
python tools\release_readiness.py `
  --repository . `
  --package-dir dist\release-candidate `
  --expected-commit (git rev-parse HEAD)
```

This profile accepts the normal unsigned CI/local candidate. It is for validation, not public distribution.

## Signed personal-player release

After building in a protected signing environment, require an externally signed package:

```powershell
python tools\release_readiness.py `
  --repository . `
  --package-dir dist\release-candidate `
  --expected-commit (git rev-parse HEAD) `
  --require-signed
```

A personal-player release can remain useful without a provider control plane because users may add their own permitted Xtream/M3U playlists.

## Signed managed-provider release

Only when a real HTTPS portal/backend has been built and independently tested:

```powershell
python tools\release_readiness.py `
  --repository . `
  --package-dir dist\release-candidate `
  --expected-commit (git rev-parse HEAD) `
  --require-signed `
  --require-portal
```

This checks that the Android build reports a configured portal. It does not test or create the backend and must never be used as proof that provider/reseller operations are production-ready.

## Repository hygiene

The readiness gate rejects tracked:

- APK and AAB files
- Keystore/signing containers
- `.env`, `local.properties` and similar local configuration
- Generated `dist/` and build output
- High-confidence private key and production-token patterns

Debug-only mock fixtures are not production credentials, but they must remain isolated from the release source set and must never be presented as a deployed portal.

## Readiness report

The default report is:

```text
dist/release-readiness/release-readiness.json
```

It contains only release identity, booleans, counts and passed-check flags. Do not add provider hostnames, credentials, device identifiers or signing details to it.
