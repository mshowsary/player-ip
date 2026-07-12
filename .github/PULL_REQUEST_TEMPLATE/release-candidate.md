## Candidate identity

- Final branch:
- Exact source commit:
- Version:
- CI run:
- Release package artifact:
- Readiness report artifact:

## Scope

- [ ] This pull request contains the approved release-candidate stack only.
- [ ] No unrelated product change was added after candidate freeze.
- [ ] No APK, AAB, keystore, signing secret, provider credential or generated `dist/` output is tracked.
- [ ] Managed-provider functionality is not claimed without a real tested backend.

## Automated verification

- [ ] Debug and release unit tests passed.
- [ ] Debug and release lint passed.
- [ ] Minified release APK and release AAB built.
- [ ] Packaging-tool and readiness-tool tests passed.
- [ ] SHA-256 checksums match.
- [ ] `release-readiness.json` says `ready: true` for the exact source commit.
- [ ] R8 mapping is retained privately.

## Physical verification

- [ ] Install over existing data passed.
- [ ] Playlist sync and Live playback passed.
- [ ] VOD playback, Resume and completion passed where content was available.
- [ ] Managed Paused/Revoked enforcement passed.
- [ ] Portrait/landscape and touch passed.
- [ ] TV/remote focus and dialog restoration passed.
- [ ] English, French and Arabic/RTL shared surfaces passed.
- [ ] Sync & health remained readable and privacy-safe.

## Collaborator approval

- [ ] Tester 1 approved this exact commit.
- [ ] Tester 2 approved this exact commit.
- [ ] Both approvals and device notes are recorded in `docs/release-acceptance-record.md` or the PR conversation.

## Distribution boundary

- [ ] Internal unsigned validation only, or
- [ ] Signed personal-player distribution, or
- [ ] Signed managed-provider distribution with a real independently tested portal/backend.
