package com.novaplay.tv.core

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Rules for the parental-control PIN and category lock keys. Pure and
 * stateless so every decision is unit-testable.
 *
 * The PIN is stored as "v1:<salt>:<sha256(salt:pin)>" — never plaintext. A
 * 4-digit PIN is not a credential (it gates content visibility on a shared
 * living-room device, nothing more), so a salted hash in app-private
 * DataStore is the appropriate strength; real secrets stay in data.security.
 *
 * Verification fails closed: absent, malformed or tampered stored values
 * never verify.
 */
object ParentalPinPolicy {
    const val PIN_LENGTH = 4
    private const val VERSION = "v1"

    /** A PIN is exactly four ASCII digits — matching every TV remote's keypad. */
    fun isValidPin(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    /** Encodes a PIN for storage; throws on invalid input so callers validate first. */
    fun encode(pin: String, salt: String = newSalt()): String {
        require(isValidPin(pin)) { "PIN must be exactly $PIN_LENGTH digits" }
        return "$VERSION:$salt:${hash(salt, pin)}"
    }

    /** True when a stored value is a well-formed encoded PIN. */
    fun isConfigured(stored: String?): Boolean = parse(stored) != null

    /** Whether [pin] matches the stored encoding; false for any absent or malformed value. */
    fun verify(pin: String, stored: String?): Boolean {
        val (salt, digest) = parse(stored) ?: return false
        if (!isValidPin(pin)) return false
        return constantTimeEquals(hash(salt, pin), digest)
    }

    /**
     * Stable identity of a lockable category. Local category row ids are wiped
     * and regenerated on every sync, so locks key on the provider's category id
     * (Xtream category_id / M3U group-title) scoped to type and playlist.
     */
    fun lockKey(type: String, playlistId: Long, remoteId: String): String =
        "$type:$playlistId:$remoteId"

    private fun parse(stored: String?): Pair<String, String>? {
        val parts = stored?.split(':') ?: return null
        if (parts.size != 3 || parts[0] != VERSION) return null
        val (_, salt, digest) = parts
        if (salt.isEmpty() || digest.length != 64) return null
        return salt to digest
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$salt:$pin".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
