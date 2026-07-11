package com.novaplay.tv.core

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.novaplay.tv.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Identity triple presented to the portal: installation UUID, display MAC, and pairing key. */
data class DeviceIdentityInfo(
    val deviceId: String,
    val mac: String,
    val deviceKey: String,
)

/**
 * Resolves and caches this installation's portal identity. Each value is generated
 * at most once, persisted in [AppPreferences], and reused for the app's lifetime.
 */
@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: DeviceIdentityInfo? = null

    /**
     * Returns the identity, generating and persisting it on first call. Double-checked
     * under a mutex so concurrent callers can never mint two identities; generation
     * runs on Dispatchers.IO because it touches DataStore, network interfaces, and sysfs.
     */
    suspend fun get(): DeviceIdentityInfo = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            // The installation UUID is the primary portal identity. It avoids
            // treating a hardware MAC address as an authentication secret and
            // remains stable across app upgrades on this installation.
            val deviceId = prefs.deviceId()
                ?: UUID.randomUUID().toString().also { prefs.setDeviceId(it) }
            val mac = prefs.deviceMac() ?: resolveMac().also { prefs.setDeviceMac(it) }
            val key = prefs.deviceKey() ?: generateDeviceKey().also { prefs.setDeviceKey(it) }
            DeviceIdentityInfo(deviceId = deviceId, mac = mac, deviceKey = key)
        }.also { cached = it }
    }

    // Legacy display/support identifier. New portal pairing uses deviceId and
    // a server-issued one-time code, but old installations keep their MAC value
    // so the migration does not break the current mock/legacy portal contract.
    // Falls through: eth0 -> wlan0 -> /sys/class/net/eth0/address -> ANDROID_ID pseudo-MAC.
    private fun resolveMac(): String {
        for (ifaceName in listOf("eth0", "wlan0")) {
            runCatching {
                val bytes = NetworkInterface.getByName(ifaceName)?.hardwareAddress
                if (bytes != null && bytes.size == 6) {
                    val mac = bytes.joinToString(":") { "%02X".format(it) }
                    if (isUsableMac(mac)) return mac
                }
            }
        }

        runCatching {
            val text = File("/sys/class/net/eth0/address").readText().trim().uppercase()
            if (isUsableMac(text)) return text
        }

        return pseudoMacFromAndroidId()
    }

    // Rejects malformed values, all-zeros, and Android's privacy placeholder 02:00:00:00:00:00.
    private fun isUsableMac(mac: String): Boolean =
        MAC_REGEX.matches(mac) &&
            mac != "00:00:00:00:00:00" &&
            mac != "02:00:00:00:00:00"

    // Last-resort MAC: first six bytes of SHA-256(ANDROID_ID), formatted like a real one.
    // Stable per device without ever exposing the raw ANDROID_ID to the portal.
    @SuppressLint("HardwareIds")
    private fun pseudoMacFromAndroidId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "novaplay-fallback"
        val digest = MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray())
        return digest.take(6).joinToString(":") { "%02X".format(it) }
    }

    // Human-readable pairing key from SecureRandom, using an alphabet without 0/O/1/I
    // so users can read it off a TV screen or over the phone without ambiguity.
    private fun generateDeviceKey(): String {
        val random = SecureRandom()
        return buildString(KEY_LENGTH) {
            repeat(KEY_LENGTH) { append(KEY_CHARSET[random.nextInt(KEY_CHARSET.length)]) }
        }
    }

    private companion object {
        val MAC_REGEX = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
        const val KEY_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val KEY_LENGTH = 6
    }
}
