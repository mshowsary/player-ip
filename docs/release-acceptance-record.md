# NovaPlay release-candidate acceptance record

Complete this record only after the final candidate branch is frozen. Do not paste credentials, portal URLs, tokens, device identifiers, signing paths or passwords into this file, a pull request or chat.

## Candidate identity

- Version name: 1.0.0-rc.1
- Version code: 1000001
- Final branch: release/1.0.0-rc.1 (created from milestone/9a-epg-foundation, the head of the 6B → 9A stack)
- Exact source commit (40 characters): 245050179c1ac872056ce335607707a23bf50307
- CI workflow run: https://github.com/mshowsary/player-ip/actions/runs/29210577782
- Release package artifact: novaplay-release-candidate (run above)
- Release-readiness report artifact: novaplay-release-readiness (run above)

## Build state

- [x] `:app:verifyReleaseCandidate` passed on the exact source commit (local run, 2026-07-13).
- [x] Packaging-tool and readiness-tool unit tests passed (part of the successful CI run above).
- [x] Release package contains exactly one APK, one AAB, `release-manifest.json` and `SHA256SUMS` (artifact downloaded and inspected).
- [x] SHA-256 values match the packaged files (verified locally with `sha256sum -c`).
- [x] Readiness report says `ready: true` for the exact source commit.
- [x] R8 mapping is stored privately (CI artifact `novaplay-r8-mapping`; not part of the release package).
- [x] No APK, AAB, keystore, signing secret or generated `dist/` output is tracked in Git (readiness `repository_hygiene: true`; stack diff against `develop` re-checked at integration).

## Distribution profile

Choose exactly one intended profile for this candidate:

- [x] Internal unsigned validation only.
- [ ] Signed personal-player distribution; managed portal is not claimed.
- [ ] Signed managed-provider distribution; a real HTTPS portal and backend have been independently configured and tested.

The Android mock portal is never evidence that a production provider backend exists.

## Physical regression checks

Owner smoke test on the exact source commit: install over existing data, no
crash, no regressions observed. The detailed per-item checklist below is to be
completed by both testers from integrated `develop` before promotion to `main`.

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

- GitHub username/name: mshowsary (project owner)
- Device/model and Android version: (owner to fill in)
- Exact commit tested: 245050179c1ac872056ce335607707a23bf50307
- Date: 2026-07-13
- Result: Approved (smoke test — install over existing data, app functional, no crash, no regressions reported)
- Release-blocking observations: none reported

### Tester 2

- GitHub username/name: PENDING — friend/collaborator has not yet tested this candidate
- Device/model and Android version: Xiaomi TV box (details to fill in at test time)
- Exact commit tested: —
- Date: —
- Result: Pending
- Release-blocking observations: —

Both testers must approve the same exact commit. Approval of an earlier parent milestone does not automatically approve later release-tooling changes.

## Integration decision

Deviation note (recorded, not hidden): integration into `develop` was performed
on the owner's explicit instruction with only Tester 1's smoke approval, to stop
the milestone stack from growing deeper. `develop` is the integration branch,
not a release; Tester 2's full pass happens from integrated `develop`, and
promotion to `main` remains blocked until both testers approve the same commit.

- [ ] Both collaborators approved the same exact final commit. (Tester 2 pending — see deviation note.)
- [x] `develop` was checked for unexpected movement (still at 740fc60, the stack's merge base).
- [x] A one-time `release/1.0.0-rc.1` integration branch was created from the approved commit.
- [x] The complete stack was compared against `develop` (86 files; no credential, binary or generated output present).
- [ ] Integration CI and highest-risk physical regression passed. (CI pending on the integration pull request; physical regression to run from `develop`.)
- [ ] Integration was squash-merged into `develop` only after approval.

## Promotion decision

Complete only after another test cycle from integrated `develop`:

- [ ] `develop → main` contains no new product changes.
- [ ] Both collaborators approved promotion.
- [ ] Main CI passed.
- [ ] The exact main commit was tagged.
- [ ] Signed artifacts were produced from the tag in a protected environment.
- [ ] Signed checksums and the matching private R8 mapping were retained.
