package com.novaplay.tv.data.repo

import com.novaplay.tv.data.remote.PairingStatusDto
import com.novaplay.tv.data.remote.PortalPlaylistDto
import com.novaplay.tv.data.remote.PortalPolicyDto
import com.novaplay.tv.data.security.PortalTokens

data class PortalPairingSession(
    val sessionId: String,
    val userCode: String,
    val verificationUri: String,
    val expiresAtEpochSec: Long,
    val intervalSeconds: Int,
    val sessionSecret: String,
)

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

object PortalPairingProtocol {
    private const val MIN_POLL_SECONDS = 3
    private const val MAX_POLL_SECONDS = 30

    fun normalizeUserCode(raw: String): String = raw
        .uppercase()
        .filter { it.isLetterOrDigit() }
        .chunked(4)
        .joinToString("-")

    fun safePollInterval(seconds: Int): Int =
        seconds.coerceIn(MIN_POLL_SECONDS, MAX_POLL_SECONDS)

    fun slowDownInterval(currentSeconds: Int): Int =
        (safePollInterval(currentSeconds) + 5).coerceAtMost(MAX_POLL_SECONDS)

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
