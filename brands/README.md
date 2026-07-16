# White-label brand packs

One codebase, many branded players. A brand pack is a directory under
`brands/<slug>/` holding everything that differs per provider; the code never
changes per brand.

## Building a brand

```bash
./gradlew :app:assembleRelease -PnovaplayBrand=<slug>
# or via environment: NOVAPLAY_BRAND=<slug>
```

Without the property, the default `novaplay` brand builds — task names, CI and
all existing commands are unchanged.

## Pack layout

```text
brands/<slug>/
├── brand.properties     required — see brands/novaplay/brand.properties
└── res/                 required — Android res overlay for this brand
    ├── mipmap-*/ic_launcher.png     launcher icon, all densities
    └── drawable-xhdpi/tv_banner.png Android TV banner (320×180)
```

`brand.properties` keys:

| Key | Required | Meaning |
| --- | --- | --- |
| `brand.appName` | yes | Launcher label / product name |
| `brand.applicationId` | yes | Unique package id per brand |
| `brand.accentColor` | no | Interaction accent `#RRGGBB` (default cyan) |
| `brand.accentColorAlt` | no | Gradient partner `#RRGGBB` (default violet) |
| `brand.allowPersonalPlaylists` | no | `false` hides the personal-source entry on the activation screen (default `true`) |
| `brand.portalBaseUrl` | no | Preset managed portal; release still enforces HTTPS and non-reserved hosts |

## Rules

- The build fails fast on a missing pack, malformed application id, malformed
  color, or missing `res/` directory — never a half-branded APK.
- Pick vivid mid-tone accents: text on the accent stays dark, so very dark
  accent colors will not read.
- Brand packs must never contain provider credentials, tokens or customer
  data. A portal URL is configuration, not a secret; everything else about a
  provider belongs in the portal backend.
- `allowPersonalPlaylists=false` currently hides the personal-source entry at
  activation; a full lockdown of playlist management arrives with the portal
  integration milestones.
- Signing stays per-provider via the existing `NOVAPLAY_SIGNING_*` environment
  variables; version overrides via `NOVAPLAY_VERSION_*` apply per build.
