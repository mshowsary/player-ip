import java.net.URI
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

val configuredPortalBaseUrl = providers.gradleProperty("novaplayPortalBaseUrl")
    .orElse(providers.environmentVariable("NOVAPLAY_PORTAL_BASE_URL"))
    .orElse("https://portal.example.com")
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
        applicationId = "com.novaplay.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "PORTAL_BASE_URL", configuredPortalBaseUrl.asBuildConfigString())
        buildConfigField("boolean", "PORTAL_CONFIGURED", portalConfigured.toString())
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
            buildConfigField("boolean", "MOCK_ACTIVATION", "true")
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
    inputs.property("applicationId", "com.novaplay.tv")
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
                appendLine("applicationId=com.novaplay.tv")
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
