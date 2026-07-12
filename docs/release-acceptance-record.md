# NovaPlay release-candidate acceptance record

Complete this record only after the final candidate branch is frozen. Do not paste credentials, portal URLs, tokens, device identifiers, signing paths or passwords into this file, a pull request or chat.

## Candidate identity

- Version name:
- Version code:
- Final branch:
- Exact source commit (40 characters):
- CI workflow run:
- Release package artifact:
- Release-readiness report artifact:

## Build state

- [ ] `:app:verifyReleaseCandidate` passed on the exact source commit.
- [ ] Packaging-tool and readiness-tool unit tests passed.
- [ ] Release package contains exactly one APK, one AAB, `release-manifest.json` and `SHA256SUMS`.
- [ ] SHA-256 values match the packaged files.
- [ ] Readiness report says `ready: true` for the exact source commit.
- [ ] R8 mapping is stored privately.
- [ ] No APK, AAB, keystore, signing secret or generated `dist/` output is tracked in Git.

## Distribution profile

Choose exactly one intended profile for this candidate:

- [ ] Internal unsigned validation only.
- [ ] Signed personal-player distribution; managed portal is not claimed.
- [ ] Signed managed-provider distribution; a real HTTPS portal and backend have been independently configured and tested.

The Android mock portal is never evidence that a production provider backend exists.

## Physical regression checks

- [ ] Install over existing data completed without a crash or reset.
- [ ] Existing playlists and active selection remained.
- [ ] Catalogue sync remained usable.
- [ ] Live playback passed.
- [ ] Movie/episode playback, Resume and completion behavior passed where content was available.
- [ ] Managed Paused and Revoked states remained enforced.
- [ ] Portrait and landscape passed.
- [ ] Forced TV/remote navigation and dialog focus restoration passed.
- [ ] English, French and Arabic/RTL shared surfaces passed.
- [ ] Sync & health remained readable and privacy-safe.

## Collaborator approvals

### Tester 1

- GitHub username/name:
- Device/model and Android version:
- Exact commit tested:
- Date:
- Result: Approved / Rejected
- Release-blocking observations:

### Tester 2

- GitHub username/name:
- Device/model and Android version:
- Exact commit tested:
- Date:
- Result: Approved / Rejected
- Release-blocking observations:

Both testers must approve the same exact commit. Approval of an earlier parent milestone does not automatically approve later release-tooling changes.

## Integration decision

- [ ] Both collaborators approved the same exact final commit.
- [ ] `develop` was checked for unexpected movement.
- [ ] A one-time `release/1.0.0-rc.1` integration branch was created from the approved commit.
- [ ] The complete stack was compared against `develop`.
- [ ] Integration CI and highest-risk physical regression passed.
- [ ] Integration was squash-merged into `develop` only after approval.

## Promotion decision

Complete only after another test cycle from integrated `develop`:

- [ ] `develop → main` contains no new product changes.
- [ ] Both collaborators approved promotion.
- [ ] Main CI passed.
- [ ] The exact main commit was tagged.
- [ ] Signed artifacts were produced from the tag in a protected environment.
- [ ] Signed checksums and the matching private R8 mapping were retained.
