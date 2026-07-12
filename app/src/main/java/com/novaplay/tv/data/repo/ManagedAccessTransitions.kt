package com.novaplay.tv.data.repo

import com.novaplay.tv.data.remote.PortalPolicyDto

/**
 * Pure managed-access state transitions shared by portal repositories and unit tests.
 *
 * Personal mode is entered only by an explicit local disconnect. Any response that
 * belongs to a managed portal session either applies a server policy, preserves the
 * last known managed decision, or fails closed when no managed decision has ever
 * been stored.
 */
object ManagedAccessTransitions {

    /** Applies a policy returned by the portal without silently unlocking a managed session. */
    fun fromPortalResponse(
        current: ManagedAccessPolicy,
        dto: PortalPolicyDto?,
        nowEpochSec: Long,
    ): ManagedAccessPolicy = when {
        dto != null -> ManagedAccessPolicy.fromPortal(dto, nowEpochSec)
        current.isManaged -> current
        else -> ManagedAccessPolicy(
            state = ManagedAccessState.SUSPENDED,
            allowLive = false,
            allowMovies = false,
            allowSeries = false,
            message = MISSING_POLICY_MESSAGE,
            updatedAtEpochSec = nowEpochSec,
        )
    }

    /**
     * Converts token revocation, installation mismatch, or an unusable refresh
     * session into a durable REVOKED policy. Cached managed content remains on
     * disk but every guarded streaming route stays blocked.
     */
    fun sessionRevoked(
        current: ManagedAccessPolicy,
        nowEpochSec: Long,
        message: String? = null,
        supportCode: String? = null,
    ): ManagedAccessPolicy = ManagedAccessPolicy(
        state = ManagedAccessState.REVOKED,
        allowLive = false,
        allowMovies = false,
        allowSeries = false,
        message = sanitize(message, MAX_MESSAGE_LENGTH) ?: REVOKED_MESSAGE,
        supportCode = sanitize(supportCode, MAX_SUPPORT_CODE_LENGTH) ?: current.supportCode,
        revision = current.revision,
        updatedAtEpochSec = nowEpochSec,
    )

    /** Explicit user disconnect is the only transition that returns to personal access. */
    fun explicitDisconnect(): ManagedAccessPolicy = ManagedAccessPolicy()

    private fun sanitize(value: String?, maxLength: Int): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(maxLength)

    private const val MAX_MESSAGE_LENGTH = 240
    private const val MAX_SUPPORT_CODE_LENGTH = 48
    private const val MISSING_POLICY_MESSAGE =
        "Managed access could not be verified. Refresh the device or contact your provider."
    private const val REVOKED_MESSAGE =
        "Managed access is no longer authorized on this device. Pair it again or contact your provider."
}
