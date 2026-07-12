# NovaPlay stacked-branch release integration process

The milestone branches are intentionally stacked so each change can be tested without entering `develop` or `main`. After both collaborators approve the same final Milestone 8B commit, integrate the stack once rather than repeatedly merging child branches back through every parent.

## Before integration

1. Freeze `milestone/8b-release-readiness-guardrails` at one exact commit.
2. Both collaborators fetch and test that same commit.
3. Complete `docs/release-acceptance-record.md` and record approval on the Milestone 8B pull request.
4. Confirm Android CI, release-candidate packaging and the release-readiness gate pass on that exact source commit.
5. Download the RC package and readiness report; verify checksums and privacy-safe contents.
6. Confirm `develop` has not moved unexpectedly. If it has moved, create a fresh integration branch, resolve the combined result there and repeat the full test cycle.

## Create one integration branch

Create a release branch at the approved Milestone 8B commit:

```powershell
git fetch origin --prune
git switch milestone/8b-release-readiness-guardrails
git pull --ff-only
git rev-parse HEAD
git switch -c release/1.0.0-rc.1
git push -u origin release/1.0.0-rc.1
```

Open one pull request:

```text
release/1.0.0-rc.1 → develop
```

That integration pull request contains the complete approved stack from Milestone 6B through Milestone 8B. The individual stacked pull requests remain useful as review and testing records, but they are not merged one by one into each intermediate branch.

## Integration review

Before merging the release branch into `develop`:

- Compare the complete diff against `develop`.
- Confirm no credential, token, keystore, APK/AAB or generated `dist/` file is present.
- Confirm CI succeeds.
- Download and inspect the release-candidate artifact, readiness report and checksums.
- Confirm the readiness report names the exact source commit under review.
- Re-run the highest-risk physical checks: install-over-existing, playlist sync, Live playback, VOD playback when available, managed Paused/Revoked access, phone rotation, forced TV/remote navigation and Sync & health.
- Require approval from both collaborators.

Use a squash merge for the integration pull request so `develop` receives one coherent release-candidate commit while the detailed milestone history remains available in the original branches and pull requests.

## After integration into develop

1. Fetch the updated `develop` branch locally.
2. Build and test from `develop`, not from the old milestone branch.
3. Run `:app:verifyReleaseCandidate` again.
4. Recreate the release package.
5. Run `tools/release_readiness.py` against the integrated `develop` commit.
6. Confirm the package source commit and readiness report now match the integrated `develop` commit.
7. Close the superseded stacked pull requests with a comment linking to the integration pull request. Do not delete their branches until the release is accepted.

## Promotion to main

Only after the integrated candidate has passed another test cycle on `develop`, open:

```text
develop → main
```

The promotion pull request should contain no new product changes. It is a release approval boundary.

After both collaborators approve and CI passes:

1. Merge into `main`.
2. Create an annotated tag such as `v1.0.0-rc.1` on the exact `main` commit.
3. Build the signed APK/AAB from that tag in the protected release environment.
4. Run the readiness tool with `--require-signed`; add `--require-portal` only for a genuinely managed-provider distribution backed by a real tested portal.
5. Store the signed artifact checksums, readiness report and private R8 mapping with the release record.
6. Keep the previous signed version and signing-key backups available for rollback and future updates.

## Rules that prevent spaghetti

- Never develop directly on `main`.
- Do not add unrelated fixes to a frozen release branch.
- A release-blocking fix gets its own branch from the release branch, its own review and another full RC/readiness verification.
- Never force-push an approved release branch.
- Never merge a commit that only one collaborator tested when both approvals are required.
- Never commit signing secrets, provider credentials or generated release binaries.
- Never accept a package whose manifest commit differs from the exact reviewed source commit.
- Do not claim managed provider functionality is production-ready until a real backend has been built and independently tested.
