package com.novaplay.tv.data.repo

import com.novaplay.tv.data.remote.PairingStatusDto
import com.novaplay.tv.data.remote.PortalPlaylistDto
import com.novaplay.tv.data.remote.PortalPolicyDto
import com.novaplay.tv.data.security.PortalTokens

/**
 * An in-flight pairing attempt. The user-visible [userCode] and the private
 * [sessionSecret] used to poll are intentionally separate values.
 */
data class PortalPairingSession(
    val sessionId: String,
    val userCode: String,
    val verificationUri: String,
    val expiresAtEpochSec: Long,
    val intervalSeconds: Int,
    val sessionSecret: String,
)

/** Outcome of one pairing status poll; Approved carries the issued tokens and assignment. */
sealed interface PortalPairingPoll {
    data class Pending(val retryAfterSeconds: Int) : PortalPairingPoll
    data class Approved(
        val tokens: PortalTokens,
        val playlists: List<PortalPlaylistDto>,
        val policy: PortalPolicyDto?,
    ) : PortalPairingPoll
    data object Denied : PortalPairingPoll
    data object Expired : PortalPairingPoll
    data class SlowDown(val retryAfterSeconds: Int) : PortalPairingPoll
    data class Failure(val message: String) : PortalPairingPoll
}

/**
 * Pure client-side rules of the device_id/user_code/session_secret pairing
 * contract (see docs/portal-pairing-contract.md). Kept free of I/O so the
 * status mapping and interval bounds are unit-testable.
 */
object PortalPairingProtocol {
    private const val MIN_POLL_SECONDS = 3
    private const val MAX_POLL_SECONDS = 30

    /** Canonicalizes a user code to uppercase alphanumerics in dash-separated groups of four. */
    fun normalizeUserCode(raw: String): String = raw
        .uppercase()
        .filter { it.isLetterOrDigit() }
        .chunked(4)
        .joinToString("-")

    /** Clamps a server-suggested interval so a bad value can neither hammer the portal nor stall pairing. */
    fun safePollInterval(seconds: Int): Int =
        seconds.coerceIn(MIN_POLL_SECONDS, MAX_POLL_SECONDS)

    /** Backs off by 5 s after a slow_down/429 response, capped at the maximum interval. */
    fun slowDownInterval(currentSeconds: Int): Int =
        (safePollInterval(currentSeconds) + 5).coerceAtMost(MAX_POLL_SECONDS)

    /**
     * Maps a 2xx status body to a poll result. An "approved" payload without an
     * access token is treated as a Failure rather than a half-paired state;
     * token lifetime gets a 60 s floor and defaults to one hour when omitted.
     * Unknown statuses fail rather than being retried blindly.
     */
    fun mapSuccessfulStatus(
        dto: PairingStatusDto,
        deviceId: String,
        nowEpochSec: Long,
        currentIntervalSeconds: Int,
    ): PortalPairingPoll {
        return when (dto.status.trim().lowercase()) {
            "pending" -> PortalPairingPoll.Pending(safePollInterval(currentIntervalSeconds))
            "slow_down" -> PortalPairingPoll.SlowDown(slowDownInterval(currentIntervalSeconds))
            "denied", "rejected" -> PortalPairingPoll.Denied
            "expired" -> PortalPairingPoll.Expired
            "approved" -> {
                val access = dto.accessToken?.takeIf { it.isNotBlank() }
                    ?: return PortalPairingPoll.Failure(
                        "Portal approval did not include a device token",
                    )
                val expiresIn = dto.expiresInSeconds?.coerceAtLeast(60L) ?: 3_600L
                PortalPairingPoll.Approved(
                    tokens = PortalTokens(
                        deviceId = deviceId,
                        accessToken = access,
                        refreshToken = dto.refreshToken?.takeIf { it.isNotBlank() },
                        accessTokenExpiresAtEpochSec = nowEpochSec + expiresIn,
                    ),
                    playlists = dto.playlists,
                    policy = dto.policy,
                )
            }
            else -> PortalPairingPoll.Failure("Unknown portal pairing status")
        }
    }
}
