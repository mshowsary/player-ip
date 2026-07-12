# Milestone 8B — release readiness guardrails test plan

This milestone adds the final repository and artifact-integrity gates before the stacked work is frozen for collaborator approval and integration. It does not merge the stack, sign a public build, publish the app, or add a production provider/reseller backend.

## Branch position

```text
develop
└── milestone/6b-release-hardening
    └── fix/6b-sync-health-responsive-layout
        └── milestone/7a-managed-access-lifecycle
            └── milestone/7b-accessibility-readable-errors
                └── milestone/7c-localization-rtl-foundation
                    └── milestone/7d-interaction-focus-consistency
                        └── milestone/8a-release-candidate-packaging
                            └── milestone/8b-release-readiness-guardrails
```

Nothing from this stack enters `develop` or `main` during this test.

## What changed

- CI and local release checks reject tracked APK/AAB files, signing containers, local environment files, generated build output and high-confidence private key/token material.
- The generated release package must contain exactly one APK, one AAB, `release-manifest.json` and `SHA256SUMS`.
- Every artifact filename, byte size and SHA-256 digest must match the manifest and checksum file.
- The package source commit must match the exact branch commit being approved.
- The readiness command rejects unexpected package files and tampered artifacts.
- Optional publication gates can require external signing and, for managed-provider builds, a configured portal.
- CI uploads a privacy-safe `release-readiness.json` report.
- The integration process now freezes the final Milestone 8B commit rather than an earlier child milestone.

## Pull and verify the branch

```powershell
git fetch origin --prune
git switch --track origin/milestone/8b-release-readiness-guardrails
```

When it already exists locally:

```powershell
git switch milestone/8b-release-readiness-guardrails
git reset --hard origin/milestone/8b-release-readiness-guardrails
```

Then record:

```powershell
git status
git rev-parse HEAD
```

The working tree must be clean. Both collaborators must eventually test the same full commit SHA.

## Install-over-existing smoke test

Install without clearing app data:

```powershell
.\gradlew.bat :app:installDebug
```

Confirm:

1. The application starts without a crash.
2. Existing playlists, catalogue and active selection remain.
3. Bookmarks and playback progress remain.
4. Live and VOD playback still work where available.
5. Managed Paused/Revoked previews still block guarded routes.
6. French/Arabic selection, rotation, touch and TV/remote navigation still work.
7. Sync & health still opens and returns focus correctly.

Milestone 8B changes release tooling, not product behavior. Any unexpected UI or data change is a failure.

## Build and package the candidate

Run:

```powershell
.\gradlew.bat :app:verifyReleaseCandidate
python tools\package_release.py --output dist\release-candidate
```

Then run the readiness gate against the exact local commit:

```powershell
python tools\release_readiness.py `
  --repository . `
  --package-dir dist\release-candidate `
  --expected-commit (git rev-parse HEAD) `
  --report dist\release-readiness\release-readiness.json
```

Expected terminal result includes:

```text
readiness passed
```

## Inspect the readiness report

```powershell
Get-Content dist\release-readiness\release-readiness.json
```

The report may contain:

- Source commit
- Version code/name
- Build channel
- Portal-configured boolean
- Signing-configured boolean
- Number of tracked/package files checked
- Boolean check results

It must not contain URLs, credentials, tokens, device identifiers, signing paths, aliases or passwords.

## Tamper check

Make a temporary copy of the package directory outside the repository, append harmless bytes to the copied APK, and run the readiness script against that copy. It must fail with a checksum mismatch. Delete the temporary copy afterward.

Do not alter the real candidate package or commit any generated artifact.

## Publication boundaries

Normal CI and normal local testing use an unsigned candidate:

```powershell
python tools\release_readiness.py --repository . --package-dir dist\release-candidate
```

A protected environment preparing any distributable build must require signing:

```powershell
python tools\release_readiness.py `
  --repository . `
  --package-dir dist\release-candidate `
  --require-signed
```

A managed-provider distribution must additionally require a real configured portal:

```powershell
python tools\release_readiness.py `
  --repository . `
  --package-dir dist\release-candidate `
  --require-signed `
  --require-portal
```

The managed gate cannot make the backend exist. It only prevents a managed-branded artifact from being accepted when the Android build reports no portal configuration.

## CI artifact check

After CI succeeds, confirm these artifacts exist:

- `novaplay-debug`
- `novaplay-release-unsigned`
- `novaplay-release-candidate`
- `novaplay-release-readiness`
- `novaplay-r8-mapping`
- `android-lint-reports`
- `room-schemas`
- `android-build-log`

Keep the R8 mapping private.

## Pass criteria

- No application crash or data loss on install-over-existing.
- Release-tool unit tests pass.
- `:app:verifyReleaseCandidate` passes.
- The package contains exactly four expected files.
- Readiness verifies the exact source commit and all checksums.
- Tampered or unexpected package files are rejected.
- Repository hygiene finds no tracked release binaries, signing material or high-confidence secrets.
- The readiness report is privacy-safe.
- CI uploads all expected artifacts.
- Both collaborators approve the same final Milestone 8B commit before the one-time integration branch is created.
