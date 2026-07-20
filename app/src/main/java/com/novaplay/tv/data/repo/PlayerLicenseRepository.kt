package com.novaplay.tv.data.repo

import android.os.Build
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.core.DeviceIdentity
import com.novaplay.tv.core.PortalEndpointPolicy
import com.novaplay.tv.data.remote.PortalApi
import com.novaplay.tv.data.remote.RegisterPlayerRequest
import com.novaplay.tv.data.security.PlayerLicense
import com.novaplay.tv.data.security.PlayerLicenseStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the Account panel shows about this installation's player license. */
data class LicenseInfo(
    val deviceCode: String,
    val status: String,
    val trialDaysLeft: Int,
    /** True when the value comes from cache because the portal was unreachable. */
    val stale: Boolean = false,
    /** When the portal last confirmed this state; null = never/unknown. */
    val verifiedAtMs: Long? = null,
)

/**
 * Self-service player identity: registers this installation once against the
 * portal (device code + sealed secret) and refreshes the trial/license state.
 * Managed (provider-paired) devices never need this; callers decide when the
 * license is relevant. Offline refreshes fall back to the cached state and
 * are marked stale rather than flipping entitlement.
 */
@Singleton
class PlayerLicenseRepository @Inject constructor(
    private val api: PortalApi,
    private val deviceIdentity: DeviceIdentity,
    private val store: PlayerLicenseStore,
) {
    private val _license = MutableStateFlow(store.load()?.toInfo(stale = true))
    val license: StateFlow<LicenseInfo?> = _license.asStateFlow()

    val available: Boolean
        get() = BuildConfig.ALLOW_PERSONAL_PLAYLISTS && !BuildConfig.MOCK_ACTIVATION &&
            PortalEndpointPolicy
                .assess(BuildConfig.PORTAL_BASE_URL, BuildConfig.DEBUG)
                .let { it.configured && it.transportAllowed }

    /** When this install first tried to reach the portal; the playback
     * gate's bootstrap-grace clock starts here. Null before any attempt. */
    fun firstAttemptAtMs(): Long? = store.firstAttemptAtMs()

    /** Registers on first call, otherwise refreshes; safe to call at launch. */
    suspend fun refresh() {
        if (!available) return
        store.markFirstAttempt()
        val stored = store.load()
        if (stored == null) {
            register()
            return
        }
        val response = runCatching {
            api.playerStatus(deviceIdentity.get().deviceId, stored.secret)
        }.getOrNull()
        if (response == null) {
            _license.value = stored.toInfo(stale = true)
            return
        }
        if (response.code() == 401) {
            // Secret no longer matches (fresh install elsewhere took the id,
            // or server-side reset): drop the identity and start over.
            store.clear()
            register()
            return
        }
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            _license.value = stored.toInfo(stale = true)
            return
        }
        store.updateStatus(body.status, body.trialDaysLeft)
        _license.value = LicenseInfo(
            deviceCode = stored.deviceCode,
            status = body.status,
            trialDaysLeft = body.trialDaysLeft,
            verifiedAtMs = System.currentTimeMillis(),
        )
    }

    private suspend fun register() {
        val identity = deviceIdentity.get()
        val response = runCatching {
            api.registerPlayer(
                RegisterPlayerRequest(
                    deviceId = identity.deviceId,
                    deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { "Android device" }
                        .take(80),
                    brand = BuildConfig.BRAND_SLUG,
                    // Portal adopts the pair the TV already displays, so the
                    // owner's login matches their screen exactly.
                    mac = identity.mac,
                    deviceKey = identity.deviceKey,
                ),
            )
        }.getOrNull() ?: return
        val body = response.body() ?: return
        val secret = body.deviceSecret
        if (!response.isSuccessful || secret.isNullOrBlank()) return
        val license = PlayerLicense(
            deviceCode = body.deviceCode,
            secret = secret,
            status = body.status,
            trialDaysLeft = body.trialDaysLeft,
            verifiedAtMs = System.currentTimeMillis(),
        )
        store.save(license)
        _license.value = license.toInfo(stale = false)
    }

    private fun PlayerLicense.toInfo(stale: Boolean) =
        LicenseInfo(
            deviceCode = deviceCode,
            status = status,
            trialDaysLeft = trialDaysLeft,
            stale = stale,
            verifiedAtMs = verifiedAtMs.takeIf { it > 0L },
        )
}
