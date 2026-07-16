# Milestone 11A — white-label build system test plan

One codebase, many branded players: a `brands/<slug>/` pack supplies the app
name, package id, launcher icon, TV banner, accent colors, an optional preset
portal and feature toggles. Selected with `-PnovaplayBrand=<slug>` (or
`NOVAPLAY_BRAND`); without it, the default `novaplay` brand builds and nothing
about existing commands, CI or the installed app changes.

## Branch position

```text
develop (…, 10B UI consolidation)
└── milestone/11a-white-label
```

## What changed

- `brands/novaplay/` — the default pack: identity, accents, and the launcher
  icons + TV banner (moved out of `src/main/res`; the manifest references are
  unchanged and resolve from the selected pack).
- `brands/demo/` — a placeholder pack (DemoPlay, `com.novaplay.demo`,
  green/amber accents, personal playlists disabled) used to exercise the
  mechanism.
- Build script: brand loader with fail-fast validation (unknown slug, bad
  package id, bad `#RRGGBB`, missing res overlay all stop the build);
  `applicationId`, generated `app_name`, `BuildConfig` brand fields, brand res
  overlay, portal preset (environment > gradle property > brand > placeholder)
  and release metadata all follow the brand.
- Theme accents (`NovaAccent`/`NovaAccentAlt`) come from the brand pack.
- `brand.allowPersonalPlaylists=false` hides the personal-source panel on the
  activation screen (full playlist-management lockdown arrives with the portal
  milestones).
- The code namespace stays `com.novaplay.tv` — only the installed identity
  changes per brand.

## Build and automated checks

```powershell
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest
./gradlew :app:lintDebug :app:lintRelease
./gradlew :app:assembleDebug :app:assembleRelease
./gradlew :app:assembleDebug -PnovaplayBrand=demo
```

All must pass; the demo assembly proves the mechanism compiles a second brand.

## Default-brand regression (critical: nothing may change)

1. Install the default build over existing data: label still "NovaPlay", same
   icon and banner, same colors, playlists/bookmarks/settings intact.
2. Activation screen still offers the personal-playlist panel.
3. Sync, playback, settings — spot-check unchanged.

## Demo-brand checks

```powershell
./gradlew :app:installDebug -PnovaplayBrand=demo
```

1. Installs **alongside** NovaPlay (different package id) with label
   "DemoPlay".
2. Wordmark shows DEMOPLAY; focus/accent colors are green with an amber
   gradient partner across Home, rails and the player overlay.
3. The activation screen shows pairing only — no personal-playlist panel.
4. Uninstall the demo app afterwards; NovaPlay is untouched.

## Validation failures (fail fast, never a half-branded APK)

```powershell
./gradlew :app:assembleDebug -PnovaplayBrand=doesnotexist   # must fail: unknown brand
```

## Approval record

```text
Tester 1 approved: <SHA>
Tester 2 approved: <same SHA>
CI passed:         <same SHA>
```

## Recovery / rollback

No schema changes. The default brand build is behavior-identical; branded
builds are separate installations.
