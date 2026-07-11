package com.novaplay.tv.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeErrorMessageTest {

    @Test
    fun redactsProviderUrlAndCredentials() {
        val error = IllegalStateException(
            "Request failed https://provider.example/player_api.php?username=alice&password=secret",
        )

        val message = SafeErrorMessage.from(error)

        assertFalse(message.contains("alice"))
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("provider.example"))
        assertTrue(message.contains("redacted URL"))
    }

    @Test
    fun keepsUsefulNonSecretErrors() {
        val message = SafeErrorMessage.from(IllegalStateException("M3U download failed: HTTP 503"))

        assertTrue(message.contains("HTTP 503"))
    }
}
