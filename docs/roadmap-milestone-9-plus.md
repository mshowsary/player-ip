# NovaPlay roadmap — Milestone 9 and beyond

Goal: a commercially polished, fast, feature-rich yet simple IPTV player competitive
with established players, monetized B2B (white-label builds for providers/resellers
plus the management portal program described in the portal contract docs). The app
remains content-neutral: it ships zero content and no provider endpoints.

This roadmap continues the Android milestone program after 8B. Each milestone keeps
the established workflow: stacked branch, draft PR, pure-policy JUnit tests,
`docs/milestone-*-testing.md` checklist, CI plus both physical-device approvals on
the same commit.

## Phase 1 — Competitive core

| Milestone | Scope | Status |
| --- | --- | --- |
| 9A EPG foundation | XMLTV + Xtream EPG data layer: schema v4, streaming parser, sync integration, now/next on channel rows and player overlay | Integrated into develop (PR #24) |
| 9B EPG guide UI | TV-style grid guide (D-pad first), touch guide, programme details | **Shelved** — built and physically tested (PR #26, closed unmerged); the target audience's providers generally don't supply EPG data, so a top-level Guide would sit unused. Revivable for white-label markets that want it. |
| 9C Live player UX | In-player channel list panel, previous-channel recall, in-player digit zapping, video scale modes (fit/stretch/zoom) | In progress |
| Deferred: Catch-up | Xtream `tv_archive` archive playback — depends on EPG programme data, deferred with 9B for the same market reason | Shelved |
| Deferred: Live track selection | Audio/subtitle picker for live streams (VOD already has one) | Candidate for a later milestone |

Rationale (updated): the 9A data layer stays — now/next lines cost no screen
space and simply don't render for providers without EPG. Guide-shaped UI waits
for a market that has guide data; day-to-day zapping ergonomics come first.

## Phase 2 — Performance and polish

| Milestone | Scope |
| --- | --- |
| 10A Low-end performance | Baseline Profile, cold-start and zap-time budgets, buffer tuning by device RAM class, jank pass on physical TV box |
| 10B UI consolidation | Remove dead screen generations, unify design tokens, Material 3 touch chrome, motion/empty-state polish |
| 10C Device matrix | Fire OS quirks, HDMI/audio-focus behavior, remote-button edge cases, second physical round |

## Phase 3 — Distribution and monetization

| Milestone | Scope |
| --- | --- |
| 11A White-label build system | Config-driven branding: name, icon, colors, preset portal endpoint, feature toggles — one codebase, per-provider builds |
| 11B Release channels | Signing, Play Store packaging and data-safety declarations, Amazon Appstore, direct-APK channel with in-app update check |
| 11C Management portal | Portal P0–P11 program (separate repository; see `README_AI_HANDOFF_NOVAPLAY.md` and `docs/portal-pairing-contract.md`) |

Phases may interleave where dependencies allow; integration slices into `develop`
follow the same rules as milestones 1A–8B.
