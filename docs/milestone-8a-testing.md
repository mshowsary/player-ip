# Milestone 8A — release-candidate packaging test plan

This milestone prepares a repeatable release-candidate build and packaging process. It does not merge the pending stack, publish an application, create a store listing, add a production provider portal or commit a signing key.

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
```

Nothing from this stack is merged into `develop` or `main` by this test.

## What changed

- The repository default version is `1.0.0-rc.1` with version code `1000001`.
- Version code/name may be overridden by protected environment variables.
- Partial signing configuration fails early instead of silently producing an unexpected artifact.
- A complete external signing environment can sign both APK and AAB without committing secrets.
- `:app:verifyReleaseCandidate` runs tests, lint, debug/release APK builds, release AAB build and metadata generation.
- `tools/package_release.py` creates stable artifact names, SHA-256 checksums and a privacy-safe manifest.
- The manifest distinguishes the approved source commit from GitHub's CI merge/test commit.
- Packaging fails when zero or multiple release APKs/AABs are present.
- CI validates the packager and uploads the RC package, private R8 mapping, lint reports and Room schemas.

## Pull and verify the branch

```powershell
git fetch origin --prune
git switch --track origin/milestone/8a-release-candidate-packaging
```

If it already exists locally:

```powershell
git switch milestone/8a-release-candidate-packaging
git reset --hard origin/milestone/8a-release-candidate-packaging
```

Then:

```powershell
git status
git rev-parse --short HEAD
```

The working tree must be clean. Record the exact commit used by both testers.

## Install-over-existing application regression

Install the debug build without clearing application data:

```powershell
.\gradlew.bat :app:installDebug
```

Confirm:

1. The application starts without a crash.
2. Existing playlists, active selection and catalogue remain.
3. Bookmarks and playback progress remain.
4. Live playback still works.
5. Movies, Series, Settings and Sync & health still open.
6. Managed Paused/Revoked previews still enforce guarded routes.
7. Language selection and RTL behavior remain available.
8. Touch and forced TV/remote navigation still work.

Milestone 8A is primarily build tooling, so unexpected UI or data changes are a failure.

## Complete local RC verification

Run:

```powershell
.\gradlew.bat :app:verifyReleaseCandidate
```

Expected outputs include:

```text
app\build\outputs\apk\debug\
app\build\outputs\apk\release\
app\build\outputs\bundle\release\
app\build\release-candidate\release-metadata.properties
```

The command must complete without test, compilation, R8, resource-shrinking or lint failure.

## Package the candidate

Run:

```powershell
python tools\package_release.py --output dist\release-candidate
```

The output directory must contain exactly:

- One versioned `.apk`
- One versioned `.aab`
- `release-manifest.json`
- `SHA256SUMS`

For the default unsigned build, artifact names should contain:

```text
novaplay-1.0.0-rc.1-1000001-<source-commit>-unsigned
```

## Validate checksums

From PowerShell:

```powershell
Get-ChildItem dist\release-candidate\*.apk,dist\release-candidate\*.aab |
  Get-FileHash -Algorithm SHA256
```

Compare the displayed hashes with `dist\release-candidate\SHA256SUMS`. They must match exactly.

Run the packaging command a second time without rebuilding. `release-manifest.json` and `SHA256SUMS` must remain identical for the same artifact bytes and the same source/build commit values.

## Commit traceability

For a local build:

- `commit` should equal the local branch `HEAD`.
- `build_commit` should normally equal `commit`.

For a GitHub pull-request artifact:

- `commit` must equal the pull request's exact head SHA—the commit both collaborators fetch and approve.
- `build_commit` may differ because GitHub compiles a temporary merge commit against the PR base.
- Versioned APK/AAB filenames must use the source `commit`, not the temporary merge commit.

This prevents a collaborator from approving one branch SHA while the package appears to belong to an unrelated synthetic SHA.

## Privacy inspection

Open `release-manifest.json` and `SHA256SUMS` in a text editor.

They may contain:

- Application ID
- Version code/name
- Approved source commit
- Tested build/merge commit
- Build channel
- Portal-configured boolean
- Signing-configured boolean
- Artifact filename, size and SHA-256

They must not contain:

- Provider portal hostname or URL
- Playlist URL
- Username or password
- Access/refresh token
- MAC address
- Device ID or device key
- Keystore path, alias or password

## Unsigned versus signed boundary

Normal CI and normal local testing produce an unsigned release candidate. Do not distribute it as a production build.

External signing is optional for this milestone and should be attempted only from a protected environment using the variables documented in `docs/release-configuration.md`. No signing secret may be pasted into an issue, pull request, chat, source file or command committed to shell history.

## CI artifacts

After CI succeeds, confirm the run contains:

- `android-build-log`
- `novaplay-debug`
- `novaplay-release-unsigned`
- `novaplay-release-candidate`
- `novaplay-r8-mapping`
- `android-lint-reports`
- `room-schemas`

Download `novaplay-release-candidate` and repeat the checksum, commit-traceability and privacy inspections. Keep `novaplay-r8-mapping` private.

## Pass criteria

- No application crash or data loss on install-over-existing.
- `:app:verifyReleaseCandidate` succeeds.
- APK and AAB are both produced.
- Packaging-tool unit tests pass.
- Exactly one APK and one AAB are packaged.
- Manifest and checksum file are stable and privacy-safe.
- Source and build commits are recorded correctly.
- SHA-256 values match the packaged files.
- CI uploads all expected artifacts.
- Both collaborators approve the same final commit before any integration PR is created.
