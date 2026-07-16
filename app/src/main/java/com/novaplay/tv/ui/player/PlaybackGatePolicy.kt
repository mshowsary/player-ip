package com.novaplay.tv.ui.player

/**
 * Trial enforcement for self-service installs, applied at playback — the
 * moment of highest intent, where an activation prompt converts best.
 *
 * Fails open on uncertainty by design: provider-managed playlists are never
 * gated (the provider pays, not the viewer), an offline/stale license state
 * never blocks a paying user, and unknown statuses do not block. The portal
 * remains the source of truth on its next successful refresh.
 */
object PlaybackGatePolicy {

    /** Non-null = playback is blocked and this is the message to show. */
    fun blockMessage(
        isPersonalPlaylist: Boolean,
        licenseStatus: String?,
        stale: Boolean,
        deviceCode: String?,
    ): String? {
        if (!isPersonalPlaylist || stale || licenseStatus == null) return null
        val code = deviceCode?.let { " Device code: $it." } ?: ""
        return when (licenseStatus) {
            "trial_expired" ->
                "Your free trial has ended. Activate a lifetime license to keep watching.$code"
            "revoked" ->
                "This player's license was moved to another device.$code"
            else -> null
        }
    }
}
