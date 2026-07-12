# NovaPlay stacked-branch release integration process

The milestone branches are intentionally stacked so each change could be tested without entering `develop` or `main`. After both collaborators approve the same final Milestone 8A commit, integrate the stack once rather than repeatedly merging child branches back through every parent.

## Before integration

1. Freeze `milestone/8a-release-candidate-packaging` at one exact commit.
2. Both collaborators fetch and test that commit.
3. Record approval on the Milestone 8A pull request.
4. Confirm Android CI and release-candidate packaging pass on that same commit.
5. Confirm `develop` has not moved unexpectedly. If it has moved, create a fresh integration branch and resolve/test the combined result there.

## Create one integration branch

Create a release branch at the approved Milestone 8A commit:

```powershell
git fetch origin --prune
git switch milestone/8a-release-candidate-packaging
git pull --ff-only
git switch -c release/1.0.0-rc.1
git push -u origin release/1.0.0-rc.1
```

Open one pull request:

```text
release/1.0.0-rc.1 → develop
```

That integration pull request contains the complete approved stack from Milestone 6B through Milestone 8A. The individual stacked pull requests remain useful as review and testing records, but they are not merged one by one into each intermediate branch.

## Integration review

Before merging the release branch into `develop`:

- Compare the complete diff against `develop`.
- Confirm no credential, token, keystore, APK/AAB or generated `dist/` file is present.
- Confirm CI succeeds.
- Download and inspect the release-candidate artifact and checksums.
- Re-run the highest-risk physical checks: install-over-existing, playlist sync, Live playback, VOD playback when available, managed Paused/Revoked access, phone rotation, forced TV/remote navigation and Sync & health.
- Require approval from both collaborators.

Use a squash merge for the integration pull request so `develop` receives one coherent release-candidate commit while the detailed milestone history remains available in the original branches and pull requests.

## After integration into develop

1. Fetch the updated `develop` branch locally.
2. Build and test from `develop`, not from the old milestone branch.
3. Run `:app:verifyReleaseCandidate` again.
4. Recreate the release package and confirm its Git commit now matches the integrated `develop` commit.
5. Close the superseded stacked pull requests with a comment linking to the integration pull request. Do not delete their branches until the release is accepted.

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
4. Store the signed artifact checksums and private R8 mapping with the release record.
5. Keep the previous signed version and signing key backups available for rollback and future updates.

## Rules that prevent spaghetti

- Never develop directly on `main`.
- Do not add unrelated fixes to a frozen release branch.
- A release-blocking fix gets its own branch from the release branch, its own review, and another full RC verification.
- Never force-push an approved release branch.
- Never merge a commit that only one collaborator tested when both approvals are required.
- Never commit signing secrets, provider credentials or generated release binaries.
- Do not claim managed provider functionality is production-ready until a real backend has been built and independently tested.
