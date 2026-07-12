package com.novaplay.tv.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.novaplay.tv.data.db.NovaDatabase
import com.novaplay.tv.data.db.Playlist
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts provider credentials before they are written to Room.
 *
 * Existing plaintext rows are supported for a safe upgrade and are sealed once
 * during launch. Values carrying [PREFIX] are never encrypted a second time.
 */
@Singleton
class PlaylistSecrets @Inject constructor(
    private val db: NovaDatabase,
) {

    /**
     * Returns a copy with server/username/password/url encrypted for storage.
     * Idempotent: values already carrying the prefix marker pass through untouched,
     * so double-sealing (and thus double-encryption) is impossible.
     */
    fun seal(playlist: Playlist): Playlist = playlist.copy(
        server = sealValue(playlist.server),
        username = sealValue(playlist.username),
        password = sealValue(playlist.password),
        url = sealValue(playlist.url),
        epgUrl = sealValue(playlist.epgUrl),
    )

    /**
     * Returns a copy with the credential fields decrypted for use. Legacy plaintext
     * values (no prefix) pass through unchanged; see [openValue] for failure behavior.
     */
    fun open(playlist: Playlist): Playlist = playlist.copy(
        server = openValue(playlist.server),
        username = openValue(playlist.username),
        password = openValue(playlist.password),
        url = openValue(playlist.url),
        epgUrl = openValue(playlist.epgUrl),
    )

    /**
     * Launch-time migration: seals any rows still holding plaintext credentials.
     * Safe to run on every startup — rows already sealed are filtered out first.
     */
    suspend fun migrateStoredPlaylists() {
        val dao = db.playlistDao()
        dao.getAll()
            .filter(::needsProtection)
            .forEach { dao.update(seal(it)) }
    }

    // A row needs sealing when any credential field is non-empty and not yet prefix-marked.
    private fun needsProtection(playlist: Playlist): Boolean = listOf(
        playlist.server,
        playlist.username,
        playlist.password,
        playlist.url,
        playlist.epgUrl,
    ).any { value -> !value.isNullOrEmpty() && !value.startsWith(PREFIX) }

    // AES-GCM with a fresh random IV per value; stored as PREFIX + base64(iv || ciphertext).
    // The prefix check up front is what makes sealing idempotent. Public so callers
    // updating a single credential column (e.g. the discovered guide URL) can seal
    // just that value without rewriting the whole row.
    fun sealValue(value: String?): String? {
        if (value.isNullOrEmpty() || value.startsWith(PREFIX)) return value

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        cipher.iv.copyInto(payload, destinationOffset = 0)
        encrypted.copyInto(payload, destinationOffset = cipher.iv.size)
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    // Reverses sealValue. Decryption failure (e.g. Keystore key lost after a backup restore
    // to another device) surfaces as a user-actionable message, never raw cipher errors.
    private fun openValue(value: String?): String? {
        if (value.isNullOrEmpty() || !value.startsWith(PREFIX)) return value

        return try {
            val payload = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            require(payload.size > IV_SIZE_BYTES) { "Encrypted playlist value is truncated" }
            val iv = payload.copyOfRange(0, IV_SIZE_BYTES)
            val ciphertext = payload.copyOfRange(IV_SIZE_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (error: Exception) {
            throw IllegalStateException(
                "Stored playlist credentials could not be decrypted. Edit or re-add this playlist.",
                error,
            )
        }
    }

    // Lazily creates the non-exportable Keystore AES key; synchronized so concurrent
    // first-time callers cannot race to generate two keys under the same alias.
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
        const val PREFIX = "enc:v1:"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "novaplay_playlist_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }
}
