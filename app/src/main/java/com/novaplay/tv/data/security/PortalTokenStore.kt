package com.novaplay.tv.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Revocable credentials issued only after a portal pairing session is approved. */
data class PortalTokens(
    val deviceId: String,
    val accessToken: String,
    val refreshToken: String?,
    val accessTokenExpiresAtEpochSec: Long,
)

/**
 * Persists portal tokens encrypted with a non-exportable Android Keystore key.
 * The human-readable pairing code and legacy MAC/device key are never stored as
 * bearer credentials.
 */
@Singleton
class PortalTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Encrypts and persists the session. Only the token values are sealed; deviceId
     * and the expiry timestamp are not secrets and stay readable for cheap checks.
     */
    @Synchronized
    fun save(tokens: PortalTokens) {
        preferences.edit()
            .putString(KEY_DEVICE_ID, tokens.deviceId)
            .putString(KEY_ACCESS_TOKEN, encrypt(tokens.accessToken))
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken?.let(::encrypt))
            .putLong(KEY_ACCESS_EXPIRES_AT, tokens.accessTokenExpiresAtEpochSec)
            .apply()
    }

    /**
     * True when a paired-session envelope exists, even if its ciphertext can no
     * longer be decrypted. This prevents a damaged managed session from falling
     * back to the legacy activation path and appearing as personal access.
     */
    @Synchronized
    fun hasStoredEnvelope(): Boolean =
        preferences.contains(KEY_DEVICE_ID) || preferences.contains(KEY_ACCESS_TOKEN)

    /**
     * Returns the stored session, or null when signed out. Fails closed: any decryption
     * failure wipes the store and returns null instead of crashing or retrying.
     */
    @Synchronized
    fun load(): PortalTokens? {
        val deviceId = preferences.getString(KEY_DEVICE_ID, null) ?: return null
        val encryptedAccess = preferences.getString(KEY_ACCESS_TOKEN, null) ?: return null
        return runCatching {
            PortalTokens(
                deviceId = deviceId,
                accessToken = decrypt(encryptedAccess),
                refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)?.let(::decrypt),
                accessTokenExpiresAtEpochSec = preferences.getLong(KEY_ACCESS_EXPIRES_AT, 0L),
            )
        }.getOrElse {
            // A restored backup can contain ciphertext without its device-bound
            // Keystore key. Treat it as an invalid managed session, not a crash.
            clear()
            null
        }
    }

    /** Drops the whole session, signing this installation out of the portal. */
    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
    }

    // AES-GCM with a fresh random IV per token; encoded as base64(iv || ciphertext).
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        cipher.iv.copyInto(payload, destinationOffset = 0)
        encrypted.copyInto(payload, destinationOffset = cipher.iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    // Splits iv || ciphertext and decrypts; throws on truncated or tampered payloads,
    // which load() converts into the fail-closed signed-out state.
    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_SIZE_BYTES) { "Encrypted portal token is truncated" }
        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    // Same lazily-created, non-exportable Keystore AES-GCM key pattern as PlaylistSecrets,
    // under its own alias so tokens and playlist credentials can be revoked independently.
    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFS_NAME = "portal_device_session"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "novaplay_portal_tokens_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }
}
