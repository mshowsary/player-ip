# NovaPlay release configuration

## Release version

Repository defaults are stored in `gradle.properties`:

```properties
novaplayVersionCode=1000001
novaplayVersionName=1.0.0-rc.1
```

`versionCode` must be a positive Android version code and must increase for every distributed build. `versionName` must use a semantic form such as `1.0.0` or `1.0.0-rc.1`.

A protected build environment may override the defaults without changing source files:

```powershell
$env:NOVAPLAY_VERSION_CODE = "1000002"
$env:NOVAPLAY_VERSION_NAME = "1.0.0-rc.2"
```

Environment values take precedence over the repository defaults. CI and the packaging manifest use the same Gradle-resolved values, preventing an APK label from disagreeing with its checksum package.

## Provider portal URL

The production control-plane URL is supplied at build time. It must be an HTTPS origin and must not contain embedded credentials, query parameters, or a fragment.

Use either a Gradle property:

```powershell
.\gradlew.bat :app:verifyReleaseCandidate -PnovaplayPortalBaseUrl=https://portal.your-domain.example
```

or an environment variable:

```powershell
$env:NOVAPLAY_PORTAL_BASE_URL = "https://portal.your-domain.example"
.\gradlew.bat :app:verifyReleaseCandidate
```

When no real portal URL is supplied, the app still builds and personal Xtream/M3U mode remains available, but managed pairing reports that the provider portal is not configured.

Debug builds may use HTTP only for local development hosts such as `localhost`, `127.0.0.1`, or the Android emulator host bridge. Production portal traffic is always HTTPS-only.

## IPTV stream compatibility

NovaPlay continues to permit user-supplied HTTP playlist and stream sources because many legitimate Xtream and MPEG-TS providers have not migrated all delivery endpoints to HTTPS. This exception does not apply to the provider control plane: portal requests use a dedicated client that rejects another host, an unsafe scheme, and redirects.

## Mock data separation

Mock activation code and development assignments exist only in the debug source set. The release source set contains no mock playlist assignment. CI runs release-specific tests to verify this boundary.

Provider credentials must never be committed as production configuration. Use personal playlist entry for local testing or connect a protected development portal.

## External signing

The repository contains no signing key or password. An unsigned release is produced when no signing environment is present.

To produce a signed APK and AAB, supply all four variables from a protected local environment or secret manager:

```powershell
$env:NOVAPLAY_SIGNING_STORE_FILE = "C:\secure\novaplay-release.jks"
$env:NOVAPLAY_SIGNING_STORE_PASSWORD = "<secret>"
$env:NOVAPLAY_SIGNING_KEY_ALIAS = "novaplay"
$env:NOVAPLAY_SIGNING_KEY_PASSWORD = "<secret>"
```

Then run:

```powershell
.\gradlew.bat :app:verifyReleaseCandidate
python tools\package_release.py --output dist\release-candidate
```

The Gradle configuration fails immediately when only some signing variables are present or the keystore file does not exist. Signing keys, aliases, passwords and keystore files must remain outside the repository. `*.jks`, `*.keystore`, `keystore.properties`, APK/AAB output and `dist/` are ignored by Git.

The packaging manifest contains only `signing_configured: true/false`. It never includes key paths, aliases or passwords.

## Release-candidate verification

Run the complete local verification with one command:

```powershell
.\gradlew.bat :app:verifyReleaseCandidate
```

That task runs:

- Debug and release unit tests
- Debug and minified release compilation
- Debug and release lint
- Debug APK generation
- Release APK generation
- Release Android App Bundle generation
- Privacy-safe release metadata generation

After it succeeds, build the package:

```powershell
python tools\package_release.py --output dist\release-candidate
```

The clean output directory contains:

- A versioned APK
- A versioned AAB
- `release-manifest.json`
- `SHA256SUMS`

The manifest records the application ID, version, source commit, tested build commit, build channel, portal-configured boolean, signing-configured boolean, artifact sizes and SHA-256 hashes. It contains no portal hostname, credentials, device identifiers or playlist data.

For a local build, `commit` and `build_commit` are normally the same Git SHA. In a pull-request workflow, `commit` records the exact head commit collaborators approve, while `build_commit` records GitHub's synthetic merge commit that CI actually compiled against the target branch. Artifact filenames use the approved source commit.

No timestamp is included, so packaging the same artifact bytes with the same source/build commit values produces the same manifest and checksum file.

The packager deliberately fails when it finds zero or multiple APKs/AABs. This prevents accidentally distributing the wrong variant.

## Backup and transfer

Application backup is disabled. Explicit Android backup and device-transfer rules also exclude databases, preferences, files, encrypted tokens, playlist details, viewing history and temporary data.

## CI release verification

Pull requests run the packaging-tool unit tests and the complete release-candidate Gradle task. Successful CI uploads:

- Build log
- Debug APK
- Raw unsigned release APK
- Versioned release-candidate APK and AAB
- Release manifest and SHA-256 checksums
- Private R8 mapping file
- Lint reports
- Room schemas

The CI release candidate remains unsigned because no private signing secrets are provided. A successful unsigned build verifies source-set separation, R8 rules, resource shrinking, release-only compilation, bundle generation and production manifest merging. It is not a distributable signed release.

The R8 mapping file is an internal support artifact. Keep it private and retain it for every distributed version so future crash traces can be de-obfuscated.
