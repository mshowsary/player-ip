package com.novaplay.tv.data.repo

import android.os.Build
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.core.DeviceIdentity
import com.novaplay.tv.core.PortalEndpointPolicy
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.remote.CreateDeliveryRequest
import com.novaplay.tv.data.remote.PortalApi
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of one delivery status poll. */
sealed interface DeliveryPoll {
    data class Pending(val retryAfterSeconds: Int) : DeliveryPoll
    data class SlowDown(val retryAfterSeconds: Int) : DeliveryPoll
    data class Ready(val draft: PlaylistDraft) : DeliveryPoll
    data object Expired : DeliveryPoll
    data class Failure(val message: String) : DeliveryPoll
}

/**
 * Phone-to-TV playlist entry: the TV shows a short code and QR, the viewer
 * types the playlist on the portal's public page, and the poll returns it
 * exactly once. Reuses the pairing session shape and interval rules.
 */
@Singleton
class PlaylistDeliveryRepository @Inject constructor(
    private val api: PortalApi,
    private val deviceIdentity: DeviceIdentity,
) {
    val available: Boolean
        get() = PortalEndpointPolicy
            .assess(BuildConfig.PORTAL_BASE_URL, BuildConfig.DEBUG)
            .let { it.configured && it.transportAllowed }

    suspend fun createSession(): Result<PortalPairingSession> = runCatching {
        PortalEndpointPolicy.requireConfigured(BuildConfig.PORTAL_BASE_URL, BuildConfig.DEBUG)
        val identity = deviceIdentity.get()
        val response = api.createDeliverySession(
            CreateDeliveryRequest(
                deviceId = identity.deviceId,
                deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "Android device" }
                    .take(80),
            ),
        )
        check(response.isSuccessful) { "Portal request failed: HTTP ${response.code()}" }
        val body = response.body() ?: error("Portal returned an empty delivery session")
        PortalPairingSession(
            sessionId = body.sessionId,
            userCode = PortalPairingProtocol.normalizeUserCode(body.userCode),
            verificationUri = body.verificationUri,
            expiresAtEpochSec = body.expiresAtEpochSec,
            intervalSeconds = PortalPairingProtocol.safePollInterval(body.intervalSeconds),
            sessionSecret = body.sessionSecret,
        )
    }

    suspend fun poll(session: PortalPairingSession): DeliveryPoll {
        if (session.expiresAtEpochSec <= System.currentTimeMillis() / 1_000) {
            return DeliveryPoll.Expired
        }
        val response = try {
            api.getDeliveryStatus(session.sessionId, session.sessionSecret)
        } catch (error: Exception) {
            return DeliveryPoll.Failure(error.message ?: "Portal network error")
        }
        if (!response.isSuccessful) {
            return when (response.code()) {
                404, 410 -> DeliveryPoll.Expired
                429 -> DeliveryPoll.SlowDown(
                    PortalPairingProtocol.slowDownInterval(session.intervalSeconds),
                )
                else -> DeliveryPoll.Failure("Portal error: HTTP ${response.code()}")
            }
        }
        val body = response.body()
            ?: return DeliveryPoll.Failure("Portal returned an empty delivery status")
        return when (body.status.trim().lowercase()) {
            "pending" -> DeliveryPoll.Pending(
                PortalPairingProtocol.safePollInterval(session.intervalSeconds),
            )
            "slow_down" -> DeliveryPoll.SlowDown(
                PortalPairingProtocol.slowDownInterval(session.intervalSeconds),
            )
            "expired" -> DeliveryPoll.Expired
            "ready" -> {
                val playlist = body.playlist
                    ?: return DeliveryPoll.Failure("Portal delivered an empty playlist")
                DeliveryPoll.Ready(
                    PlaylistDraft(
                        name = playlist.name,
                        type = if (playlist.type.equals("m3u", ignoreCase = true)) {
                            Playlist.TYPE_M3U
                        } else {
                            Playlist.TYPE_XTREAM
                        },
                        server = playlist.server.orEmpty(),
                        username = playlist.username.orEmpty(),
                        password = playlist.password.orEmpty(),
                        url = playlist.url.orEmpty(),
                    ),
                )
            }
            else -> DeliveryPoll.Failure("Unknown delivery status")
        }
    }
}
