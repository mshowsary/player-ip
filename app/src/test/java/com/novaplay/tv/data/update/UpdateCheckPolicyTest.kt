package com.novaplay.tv.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {

    private val current = 1_000_001L

    // A real RSA pair generated per test run: the signing contract is
    // exercised end to end, not against canned strings.
    private val keyPair = java.security.KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()
    private val pinnedKey: String =
        java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun signed(dto: UpdateManifestDto): UpdateManifestDto {
        val signature = java.security.Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(UpdateCheckPolicy.signingPayload(dto))
            sign()
        }
        return dto.copy(signature = java.util.Base64.getEncoder().encodeToString(signature))
    }

    @Test
    fun pinnedKeyAcceptsAProperlySignedManifest() {
        val decision = UpdateCheckPolicy.evaluate(
            current, signed(manifest()), allowLocalHttp = false, pinnedPublicKey = pinnedKey,
        )
        assertTrue(decision is UpdateDecision.Available)
    }

    @Test
    fun pinnedKeyRejectsUnsignedManifests() {
        assertEquals(
            UpdateDecision.Invalid,
            UpdateCheckPolicy.evaluate(current, manifest(), false, pinnedKey),
        )
    }

    @Test
    fun pinnedKeyRejectsTamperedManifests() {
        val tampered = signed(manifest()).copy(apkUrl = "https://evil.example.com/malware.apk")
        assertEquals(
            UpdateDecision.Invalid,
            UpdateCheckPolicy.evaluate(current, tampered, false, pinnedKey),
        )
    }

    @Test
    fun pinnedKeyRejectsGarbageSignaturesWithoutCrashing() {
        val garbage = manifest().copy(signature = "not-base64!!!")
        assertEquals(
            UpdateDecision.Invalid,
            UpdateCheckPolicy.evaluate(current, garbage, false, pinnedKey),
        )
    }

    @Test
    fun withoutAPinnedKeyUnsignedManifestsStillWork() {
        val decision = UpdateCheckPolicy.evaluate(current, manifest(), false, pinnedPublicKey = "")
        assertTrue(decision is UpdateDecision.Available)
    }

    private fun manifest(
        code: Long = 1_000_002L,
        name: String? = "1.0.1",
        apk: String? = "https://updates.example-provider.com/player.apk",
    ) = UpdateManifestDto(versionCode = code, versionName = name, apkUrl = apk)

    @Test
    fun newerVersionIsAvailable() {
        val decision = UpdateCheckPolicy.evaluate(current, manifest(), allowLocalHttp = false)
        assertTrue(decision is UpdateDecision.Available)
        assertEquals("1.0.1", (decision as UpdateDecision.Available).versionName)
    }

    @Test
    fun sameOrOlderVersionIsUpToDate() {
        assertEquals(
            UpdateDecision.UpToDate,
            UpdateCheckPolicy.evaluate(current, manifest(code = current), false),
        )
        assertEquals(
            UpdateDecision.UpToDate,
            UpdateCheckPolicy.evaluate(current, manifest(code = current - 1), false),
        )
    }

    @Test
    fun malformedManifestsFailClosed() {
        assertEquals(UpdateDecision.Invalid, UpdateCheckPolicy.evaluate(current, null, false))
        assertEquals(UpdateDecision.Invalid, UpdateCheckPolicy.evaluate(current, manifest(code = 0), false))
        assertEquals(UpdateDecision.Invalid, UpdateCheckPolicy.evaluate(current, manifest(name = " "), false))
        assertEquals(UpdateDecision.Invalid, UpdateCheckPolicy.evaluate(current, manifest(apk = null), false))
    }

    @Test
    fun insecureDownloadLinksFailClosed() {
        val httpRemote = manifest(apk = "http://updates.example-provider.com/player.apk")
        assertEquals(UpdateDecision.Invalid, UpdateCheckPolicy.evaluate(current, httpRemote, false))
        // Even in debug, remote HTTP stays rejected — only local hosts pass.
        assertEquals(UpdateDecision.Invalid, UpdateCheckPolicy.evaluate(current, httpRemote, true))
        val credentialed = manifest(apk = "https://user:pass@updates.example.com/player.apk")
        assertEquals(UpdateDecision.Invalid, UpdateCheckPolicy.evaluate(current, credentialed, false))
    }

    @Test
    fun localHttpIsAllowedOnlyWhenPermitted() {
        val local = manifest(apk = "http://10.0.2.2:8899/direct/update.apk")
        assertTrue(UpdateCheckPolicy.evaluate(current, local, true) is UpdateDecision.Available)
        assertEquals(UpdateDecision.Invalid, UpdateCheckPolicy.evaluate(current, local, false))
    }

    @Test
    fun urlRuleMatchesManifestTransport() {
        assertTrue(UpdateCheckPolicy.isAllowedUrl("https://updates.example.com/manifest.json", false))
        assertTrue(UpdateCheckPolicy.isAllowedUrl("http://localhost:8899/updates.json", true))
        assertFalse(UpdateCheckPolicy.isAllowedUrl("http://localhost:8899/updates.json", false))
        assertFalse(UpdateCheckPolicy.isAllowedUrl("ftp://updates.example.com/x", true))
        assertFalse(UpdateCheckPolicy.isAllowedUrl("not a url", true))
    }
}
