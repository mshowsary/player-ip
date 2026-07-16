import java.net.URI
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun isReservedPortalHost(rawHost: String): Boolean {
    val host = rawHost.lowercase().trimEnd('.')
    return host == "localhost" ||
        host == "example.com" || host.endsWith(".example.com") ||
        host == "example.net" || host.endsWith(".example.net") ||
        host == "example.org" || host.endsWith(".example.org") ||
        host == "invalid" || host.endsWith(".invalid") ||
        host == "test" || host.endsWith(".test") ||
        host == "example" || host.endsWith(".example")
}

// ---- White-label brand pack (brands/<slug>/) ----
// The brand supplies identity (name, package id), accents, feature toggles and
// an optional preset portal. Everything validates at configuration time so a
// half-branded APK can never be produced.
val brandSlug = providers.gradleProperty("novaplayBrand")
    .orElse(providers.environmentVariable("NOVAPLAY_BRAND"))
    .orElse("novaplay")
    .get()
    .trim()
    .lowercase()
require(Regex("""^[a-z][a-z0-9]{1,23}$""").matches(brandSlug)) {
    "Brand slug '$brandSlug' must be 2-24 lowercase letters/digits starting with a letter"
}
val brandDir = rootProject.file("brands/$brandSlug")
require(File(brandDir, "brand.properties").isFile) {
    "Unknown brand '$brandSlug' — expected brands/$brandSlug/brand.properties (see brands/README.md)"
}
require(File(brandDir, "res").isDirectory) {
    "Brand '$brandSlug' is missing its res/ overlay (launcher icons and TV banner)"
}
val brandProperties = Properties().apply {
    File(brandDir, "brand.properties").inputStream().use(::load)
}

fun brandValue(key: String): String? =
    brandProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

val brandAppName = brandValue("brand.appName")
    ?: error("Brand '$brandSlug' must define brand.appName")
val brandApplicationId = brandValue("brand.applicationId")
    ?: error("Brand '$brandSlug' must define brand.applicationId")
require(Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$""").matches(brandApplicationId)) {
    "Brand '$brandSlug' application id '$brandApplicationId' is not a valid Android package name"
}
val brandAccent = brandValue("brand.accentColor") ?: "#22D3EE"
val brandAccentAlt = brandValue("brand.accentColorAlt") ?: "#8B5CF6"
for ((key, value) in listOf("brand.accentColor" to brandAccent, "brand.accentColorAlt" to brandAccentAlt)) {
    require(Regex("""^#[0-9a-fA-F]{6}$""").matches(value)) {
        "Brand '$brandSlug' $key '$value' must be #RRGGBB"
    }
}
val brandAllowPersonalText = brandValue("brand.allowPersonalPlaylists") ?: "true"
require(brandAllowPersonalText in setOf("true", "false")) {
    "Brand '$brandSlug' brand.allowPersonalPlaylists must be true or false"
}
val brandAllowPersonal = brandAllowPersonalText.toBoolean()
val brandPortalBaseUrl = brandValue("brand.portalBaseUrl")

// Optional sideload update channel: environment > gradle property > brand pack.
// Validated for shape here; the app additionally enforces HTTPS (or local
// debug HTTP) before any request leaves the device.
val configuredUpdateUrl = providers.gradleProperty("novaplayUpdateUrl")
    .orElse(providers.environmentVariable("NOVAPLAY_UPDATE_URL"))
    .orElse(brandValue("brand.updateUrl") ?: "")
    .get()
    .trim()
if (configuredUpdateUrl.isNotEmpty()) {
    val updateUri = runCatching { URI(configuredUpdateUrl) }.getOrNull()
    require(
        updateUri != null &&
            updateUri.scheme?.lowercase() in setOf("http", "https") &&
            !updateUri.host.isNullOrBlank() &&
            updateUri.userInfo == null,
    ) {
        "Update URL '$configuredUpdateUrl' must be a plain http(s) URL without credentials"
    }
}

// Portal resolution order: environment > gradle property > brand pack > placeholder.
val configuredPortalBaseUrl = providers.gradleProperty("novaplayPortalBaseUrl")
    .orElse(providers.environmentVariable("NOVAPLAY_PORTAL_BASE_URL"))
    .orElse(brandPortalBaseUrl ?: "https://portal.example.com")
    .get()
val configuredPortalUri = runCatching { URI(configuredPortalBaseUrl.trim()) }.getOrNull()
val configuredPortalHost = configuredPortalUri?.host?.lowercase().orEmpty()
val configuredPortalScheme = configuredPortalUri?.scheme?.lowercase().orEmpty()
val portalAddressHasSafeShape = configuredPortalUri != null &&
    configuredPortalHost.isNotBlank() &&
    configuredPortalUri.userInfo == null &&
    configuredPortalUri.query == null &&
    configuredPortalUri.fragment == null
val localDebugPortal = configuredPortalScheme == "http" &&
    configuredPortalHost in setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")
val portalConfigured = portalAddressHasSafeShape &&
    ((configuredPortalScheme == "https" && !isReservedPortalHost(configuredPortalHost)) || localDebugPortal)
// Release metadata is stricter than debug configuration: only a non-reserved
// HTTPS authority can satisfy the managed-provider publication gate.
val releasePortalConfigured = portalAddressHasSafeShape &&
    configuredPortalScheme == "https" &&
    !isReservedPortalHost(configuredPortalHost)

// A protected release environment can override the repository defaults without
// changing source files. Environment variables intentionally take precedence.
val appVersionCodeText = providers.environmentVariable("NOVAPLAY_VERSION_CODE")
    .orElse(providers.gradleProperty("novaplayVersionCode"))
    .orElse("1000001")
    .get()
    .trim()
val appVersionName = providers.environmentVariable("NOVAPLAY_VERSION_NAME")
    .orElse(providers.gradleProperty("novaplayVersionName"))
    .orElse("1.0.0-rc.1")
    .get()
    .trim()
val appVersionCode = appVersionCodeText.toIntOrNull()
    ?: error("NovaPlay version code must be a positive integer")
require(appVersionCode in 1..2_100_000_000) {
    "NovaPlay version code must be between 1 and 2100000000"
}
require(Regex("""\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?""").matches(appVersionName)) {
    "NovaPlay version name must use a semantic form such as 1.0.0 or 1.0.0-rc.1"
}

// Signing material is accepted only from the process environment. Partial
// configuration fails early, and no key path, alias or password is committed.
val signingStorePath = providers.environmentVariable("NOVAPLAY_SIGNING_STORE_FILE")
    .orElse("")
    .get()
    .trim()
val signingStorePassword = providers.environmentVariable("NOVAPLAY_SIGNING_STORE_PASSWORD")
    .orElse("")
    .get()
val signingKeyAlias = providers.environmentVariable("NOVAPLAY_SIGNING_KEY_ALIAS")
    .orElse("")
    .get()
    .trim()
val signingKeyPassword = providers.environmentVariable("NOVAPLAY_SIGNING_KEY_PASSWORD")
    .orElse("")
    .get()
val signingValues = listOf(
    signingStorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
)
val signingConfigured = signingValues.all { it.isNotBlank() }
val signingPartiallyConfigured = signingValues.any { it.isNotBlank() } && !signingConfigured
require(!signingPartiallyConfigured) {
    "Release signing is partially configured. Supply all NOVAPLAY_SIGNING_* variables or none."
}
if (signingConfigured) {
    require(file(signingStorePath).isFile) {
        "Release signing keystore does not exist at the configured path"
    }
}

android {
    namespace = "com.novaplay.tv"
    compileSdk = 35

    defaultConfig {
        // The namespace (code/R package) is fixed; only the installed identity
        // follows the brand.
        applicationId = brandApplicationId
        minSdk = 23
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        resValue("string", "app_name", brandAppName.replace("\"", ""))
        buildConfigField("String", "BRAND_SLUG", brandSlug.asBuildConfigString())
        buildConfigField("String", "BRAND_ACCENT", brandAccent.asBuildConfigString())
        buildConfigField("String", "BRAND_ACCENT_ALT", brandAccentAlt.asBuildConfigString())
        buildConfigField("boolean", "ALLOW_PERSONAL_PLAYLISTS", brandAllowPersonal.toString())
        buildConfigField("String", "UPDATE_MANIFEST_URL", configuredUpdateUrl.asBuildConfigString())
        buildConfigField("String", "PORTAL_BASE_URL", configuredPortalBaseUrl.asBuildConfigString())
        buildConfigField("boolean", "PORTAL_CONFIGURED", portalConfigured.toString())
    }

    sourceSets {
        // Launcher icons and the TV banner come from the selected brand pack.
        getByName("main") {
            res.srcDir(brandDir.resolve("res"))
        }
    }

    signingConfigs {
        if (signingConfigured) {
            create("externalRelease") {
                storeFile = file(signingStorePath)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Mock activation stays the debug default so the app remains
            // testable with zero infrastructure. Building against a real
            // development portal: -PnovaplayMockActivation=false (or the
            // NOVAPLAY_MOCK_ACTIVATION env var) together with
            // -PnovaplayPortalBaseUrl=http://127.0.0.1:8000. Release builds
            // ignore this switch entirely.
            val debugMockActivation = providers.gradleProperty("novaplayMockActivation")
                .orElse(providers.environmentVariable("NOVAPLAY_MOCK_ACTIVATION"))
                .getOrElse("true")
                .trim()
                .lowercase() != "false"
            buildConfigField("boolean", "MOCK_ACTIVATION", debugMockActivation.toString())
            buildConfigField("String", "BUILD_CHANNEL", "\"debug\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "MOCK_ACTIVATION", "false")
            buildConfigField("String", "BUILD_CHANNEL", "\"production\"")
            if (signingConfigured) {
                signingConfig = signingConfigs.getByName("externalRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

val releaseMetadataFile = layout.buildDirectory.file("release-candidate/release-metadata.properties")

tasks.register("writeReleaseMetadata") {
    group = "build"
    description = "Writes privacy-safe metadata for release-candidate packaging."
    inputs.property("applicationId", brandApplicationId)
    inputs.property("versionCode", appVersionCode)
    inputs.property("versionName", appVersionName)
    inputs.property("portalConfigured", releasePortalConfigured)
    inputs.property("signingConfigured", signingConfigured)
    outputs.file(releaseMetadataFile)

    doLast {
        val output = releaseMetadataFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            buildString {
                appendLine("applicationId=$brandApplicationId")
                appendLine("versionCode=$appVersionCode")
                appendLine("versionName=$appVersionName")
                appendLine("buildChannel=production")
                appendLine("portalConfigured=$releasePortalConfigured")
                appendLine("signingConfigured=$signingConfigured")
            },
        )
    }
}

// One local/CI command exercises every variant and creates both distribution
// formats before the external packaging script computes immutable checksums.
tasks.register("verifyReleaseCandidate") {
    group = "verification"
    description = "Tests, lints and builds the complete NovaPlay release candidate."
    dependsOn(
        "testDebugUnitTest",
        "testReleaseUnitTest",
        "assembleDebug",
        "assembleRelease",
        "bundleRelease",
        "lintDebug",
        "lintRelease",
        "writeReleaseMetadata",
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    implementation(libs.coil.compose)
    // QR rendering for pairing and phone-entry codes; core encoder only, no camera.
    implementation(libs.zxing.core)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    // Compiles the baseline profiles shipped inside Compose/Media3 at install
    // time on sideloaded devices (no Play optimization pass on IPTV boxes).
    implementation(libs.profileinstaller)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
}
