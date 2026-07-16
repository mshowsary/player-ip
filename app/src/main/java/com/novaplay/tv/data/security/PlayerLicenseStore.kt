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

/** The installation's registered player identity plus its last known state. */
data class PlayerLicense(
    val deviceCode: String,
    val secret: String,
    val status: String,
    val trialDaysLeft: Int,
)

/**
 * Persists the self-service player identity. The device code is a public
 * identity (shown in Account, read out on support calls); only the portal
 * secret is sealed with a non-exportable Keystore key, mirroring
 * [PortalTokenStore]'s fail-closed pattern under its own alias.
 */
@Singleton
class PlayerLicenseStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun save(license: PlayerLicense) {
        preferences.edit()
            .putString(KEY_DEVICE_CODE, license.deviceCode)
            .putString(KEY_SECRET, encrypt(license.secret))
            .putString(KEY_STATUS, license.status)
            .putInt(KEY_DAYS_LEFT, license.trialDaysLeft)
            .apply()
    }

    /** Updates the cached status without touching the sealed secret. */
    @Synchronized
    fun updateStatus(status: String, trialDaysLeft: Int) {
        preferences.edit()
            .putString(KEY_STATUS, status)
            .putInt(KEY_DAYS_LEFT, trialDaysLeft)
            .apply()
    }

    /** Returns the stored identity; decryption failure wipes and returns null. */
    @Synchronized
    fun load(): PlayerLicense? {
        val code = preferences.getString(KEY_DEVICE_CODE, null) ?: return null
        val sealed = preferences.getString(KEY_SECRET, null) ?: return null
        return runCatching {
            PlayerLicense(
                deviceCode = code,
                secret = decrypt(sealed),
                status = preferences.getString(KEY_STATUS, "trial") ?: "trial",
                trialDaysLeft = preferences.getInt(KEY_DAYS_LEFT, 0),
            )
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        cipher.iv.copyInto(payload, destinationOffset = 0)
        encrypted.copyInto(payload, destinationOffset = cipher.iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_SIZE_BYTES) { "Encrypted license secret is truncated" }
        val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
        val ciphertext = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

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
        const val PREFS_NAME = "player_license"
        const val KEY_DEVICE_CODE = "device_code"
        const val KEY_SECRET = "secret"
        const val KEY_STATUS = "status"
        const val KEY_DAYS_LEFT = "days_left"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "novaplay_player_license_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }
}
