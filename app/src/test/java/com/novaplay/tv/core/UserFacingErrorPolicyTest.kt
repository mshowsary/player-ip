package com.novaplay.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingErrorPolicyTest {

    @Test
    fun classifiesNetworkTimeoutAndProviderFailures() {
        assertEquals(
            UserErrorCategory.OFFLINE,
            UserFacingErrorPolicy.from("Unable to resolve host example.test").category,
        )
        assertEquals(
            UserErrorCategory.TIMEOUT,
            UserFacingErrorPolicy.from("connection timed out").category,
        )
        assertEquals(
            UserErrorCategory.PROVIDER_UNAVAILABLE,
            UserFacingErrorPolicy.from("HTTP 503").category,
        )
    }

    @Test
    fun authorizationFailuresAreNotPresentedAsGenericRetryableErrors() {
        val result = UserFacingErrorPolicy.from("Portal authorization failed: HTTP 403")

        assertEquals(UserErrorCategory.UNAUTHORIZED, result.category)
        assertFalse(result.retryable)
        assertNull(result.safeDetail)
    }

    @Test
    fun keepsShortHumanWrittenMessages() {
        val result = UserFacingErrorPolicy.from("No stream available for this channel")

        assertEquals(UserErrorCategory.CONTENT_UNAVAILABLE, result.category)
        assertTrue(result.retryable)
    }

    @Test
    fun hidesTechnicalExceptionMessagesBehindUnknownCategory() {
        val result = UserFacingErrorPolicy.from(
            "java.lang.IllegalStateException: okhttp failed to execute",
        )

        assertEquals(UserErrorCategory.UNKNOWN, result.category)
        assertNull(result.safeDetail)
    }

    @Test
    fun redactsUrlsBearerTokensAndCredentialFields() {
        val safe = SafeErrorMessage.sanitize(
            "GET https://provider.test/player?username=alice&password=secret " +
                "Authorization: Bearer abc.def.ghi access_token=topsecret device_key=1234",
        )

        assertFalse(safe.contains("provider.test"))
        assertFalse(safe.contains("alice"))
        assertFalse(safe.contains("secret"))
        assertFalse(safe.contains("abc.def.ghi"))
        assertFalse(safe.contains("topsecret"))
        assertFalse(safe.contains("1234"))
        assertTrue(safe.contains("[redacted URL]"))
    }

    @Test
    fun sanitizationCollapsesWhitespaceAndBoundsLength() {
        val safe = SafeErrorMessage.sanitize("line one\n\n" + "x".repeat(900))

        assertFalse(safe.contains('\n'))
        assertTrue(safe.length <= 500)
    }
}
