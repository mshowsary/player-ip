package com.novaplay.tv.ui.player

import java.util.concurrent.TimeUnit

/**
 * License enforcement for self-service installs, applied at playback — the
 * moment of highest intent, where an activation prompt converts best.
 *
 * Provider-managed playlists are never gated (the provider pays, not the
 * viewer) and unknown status strings never block (forward compatibility).
 * Uncertainty, however, is bounded instead of open-ended: an installation
 * that cannot reach the portal keeps playing through a grace window computed
 * from its last verified state, after which personal playlists ask for one
 * successful check-in. That closes the "block the portal and keep the trial
 * forever" hole while a verified lifetime license stays valid offline
 * indefinitely and a paying yearly customer rides out any portal outage
 * inside their remaining days plus the grace.
 *
 * Known limitation: the device clock is trusted between check-ins, so
 * rolling the clock back stretches the grace locally. The portal remains the
 * source of truth the moment the device comes back online.
 */
object PlaybackGatePolicy {

    /** How long past its computed expiry a verified state keeps playing offline. */
    val OFFLINE_GRACE_MS: Long = TimeUnit.HOURS.toMillis(72)

    /** How long a never-verified install may play before its first check-in. */
    val BOOTSTRAP_GRACE_MS: Long = TimeUnit.HOURS.toMillis(72)

    private val DAY_MS = TimeUnit.DAYS.toMillis(1)

    /** Non-null = playback is blocked and this is the message to show. */
    fun blockMessage(
        isPersonalPlaylist: Boolean,
        licenseStatus: String?,
        daysLeftAtVerification: Int,
        verifiedAtMs: Long?,
        firstAttemptAtMs: Long?,
        nowMs: Long,
        deviceCode: String?,
    ): String? {
        if (!isPersonalPlaylist) return null
        // Licensing never engaged on this build (managed-only brand, mock
        // activation, or no portal configured): nothing to enforce.
        if (firstAttemptAtMs == null && verifiedAtMs == null) return null
        val code = deviceCode?.let { " Device code: $it." } ?: ""

        if (verifiedAtMs == null) {
            // The portal has never answered: bounded benefit of the doubt,
            // then ask for a single successful check-in.
            return if (nowMs - (firstAttemptAtMs ?: nowMs) <= BOOTSTRAP_GRACE_MS) {
                null
            } else {
                "Connect this player to the internet once so it can start its free trial.$code"
            }
        }

        return when (licenseStatus) {
            "revoked" -> "This player's license was moved to another device.$code"
            "trial_expired" ->
                "Your free trial has ended. Activate this player to keep watching.$code"
            "expired" ->
                "Your activation has expired. Renew it to keep watching.$code"
            "trial" -> expiredMessage(
                allowedMs = daysLeftAtVerification * DAY_MS + OFFLINE_GRACE_MS,
                elapsedMs = nowMs - verifiedAtMs,
                message = "Your free trial has ended. Activate this player to keep watching.$code",
            )
            "licensed" ->
                if (daysLeftAtVerification <= 0) {
                    // Lifetime: verified once, then valid offline forever.
                    null
                } else {
                    expiredMessage(
                        allowedMs = daysLeftAtVerification * DAY_MS + OFFLINE_GRACE_MS,
                        elapsedMs = nowMs - verifiedAtMs,
                        message = "Your activation has expired. Renew it to keep watching.$code",
                    )
                }
            else -> null
        }
    }

    private fun expiredMessage(allowedMs: Long, elapsedMs: Long, message: String): String? =
        if (elapsedMs <= allowedMs) null else message
}
