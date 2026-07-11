package com.novaplay.tv.data.repo

import android.content.Context
import com.novaplay.tv.data.remote.PortalPolicyDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Lifecycle of a portal-managed device; UNMANAGED means no portal governs it. */
enum class ManagedAccessState {
    UNMANAGED,
    ACTIVE,
    SUSPENDED,
    REVOKED,
}

/** Content sections a managed policy can allow or block individually. */
enum class ManagedFeature {
    LIVE,
    MOVIES,
    SERIES,
}

/** Effective access policy for this device; the default value is fully open (unmanaged). */
data class ManagedAccessPolicy(
    val state: ManagedAccessState = ManagedAccessState.UNMANAGED,
    val allowLive: Boolean = true,
    val allowMovies: Boolean = true,
    val allowSeries: Boolean = true,
    val message: String? = null,
    val supportCode: String? = null,
    val revision: Long = 0L,
    val updatedAtEpochSec: Long = 0L,
) {
    val isManaged: Boolean get() = state != ManagedAccessState.UNMANAGED
    val isBlocked: Boolean get() = state == ManagedAccessState.SUSPENDED || state == ManagedAccessState.REVOKED

    /**
     * Whether a feature is currently permitted. Fails closed: any managed state
     * other than ACTIVE denies everything, regardless of the per-feature flags.
     */
    fun allows(feature: ManagedFeature): Boolean {
        if (state == ManagedAccessState.UNMANAGED) return true
        if (state != ManagedAccessState.ACTIVE) return false
        return when (feature) {
            ManagedFeature.LIVE -> allowLive
            ManagedFeature.MOVIES -> allowMovies
            ManagedFeature.SERIES -> allowSeries
        }
    }

    /** Short user-facing label for the current state, shown in settings. */
    fun statusLabel(): String = when (state) {
        ManagedAccessState.UNMANAGED -> "Personal access"
        ManagedAccessState.ACTIVE -> "Managed access active"
        ManagedAccessState.SUSPENDED -> "Managed access paused"
        ManagedAccessState.REVOKED -> "Managed access revoked"
    }

    companion object {
        /**
         * Maps the portal DTO to a policy. Unknown status strings fail closed
         * to SUSPENDED rather than granting access; message and support code
         * are trimmed and length-capped for safe display.
         */
        fun fromPortal(dto: PortalPolicyDto?, nowEpochSec: Long): ManagedAccessPolicy {
            if (dto == null) return ManagedAccessPolicy()
            val state = when (dto.status.trim().lowercase()) {
                "active" -> ManagedAccessState.ACTIVE
                "suspended", "paused" -> ManagedAccessState.SUSPENDED
                "revoked", "disabled" -> ManagedAccessState.REVOKED
                else -> ManagedAccessState.SUSPENDED
            }
            return ManagedAccessPolicy(
                state = state,
                allowLive = dto.allowLive,
                allowMovies = dto.allowMovies,
                allowSeries = dto.allowSeries,
                message = dto.message?.trim()?.takeIf { it.isNotEmpty() }?.take(240),
                supportCode = dto.supportCode?.trim()?.takeIf { it.isNotEmpty() }?.take(48),
                revision = dto.revision.coerceAtLeast(0L),
                updatedAtEpochSec = nowEpochSec,
            )
        }
    }
}

/**
 * Persists the last-known managed access policy in SharedPreferences and
 * exposes it as a StateFlow, so enforcement keeps working offline with the
 * most recent portal decision.
 */
@Singleton
class ManagedAccessRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _policy = MutableStateFlow(load())
    val policy: StateFlow<ManagedAccessPolicy> = _policy.asStateFlow()

    /**
     * Applies a policy fresh from the portal. A null DTO is ignored on purpose:
     * an omitted policy keeps the last-known one instead of silently unlocking.
     */
    @Synchronized
    fun applyPortalPolicy(dto: PortalPolicyDto?) {
        if (dto == null) return
        apply(ManagedAccessPolicy.fromPortal(dto, nowEpochSec()))
    }

    /** Persists the policy and emits it to [policy] collectors in one step. */
    @Synchronized
    fun apply(policy: ManagedAccessPolicy) {
        preferences.edit()
            .putString(KEY_STATE, policy.state.name)
            .putBoolean(KEY_ALLOW_LIVE, policy.allowLive)
            .putBoolean(KEY_ALLOW_MOVIES, policy.allowMovies)
            .putBoolean(KEY_ALLOW_SERIES, policy.allowSeries)
            .putString(KEY_MESSAGE, policy.message)
            .putString(KEY_SUPPORT_CODE, policy.supportCode)
            .putLong(KEY_REVISION, policy.revision)
            .putLong(KEY_UPDATED_AT, policy.updatedAtEpochSec)
            .apply()
        _policy.value = policy
    }

    /** Drops the stored policy and reverts to unmanaged, e.g. on portal disconnect. */
    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
        _policy.value = ManagedAccessPolicy()
    }

    /** Installs a canned policy for debug builds so each managed state can be exercised by hand. */
    fun setDebugPreset(preset: DebugManagedPolicyPreset) {
        val now = nowEpochSec()
        val next = when (preset) {
            DebugManagedPolicyPreset.FULL_ACCESS -> ManagedAccessPolicy(
                state = ManagedAccessState.ACTIVE,
                revision = DEBUG_REVISION,
                updatedAtEpochSec = now,
                message = "All managed services are available.",
            )
            DebugManagedPolicyPreset.LIVE_ONLY -> ManagedAccessPolicy(
                state = ManagedAccessState.ACTIVE,
                allowMovies = false,
                allowSeries = false,
                revision = DEBUG_REVISION,
                updatedAtEpochSec = now,
                message = "This test policy allows Live TV only.",
            )
            DebugManagedPolicyPreset.SUSPENDED -> ManagedAccessPolicy(
                state = ManagedAccessState.SUSPENDED,
                allowLive = false,
                allowMovies = false,
                allowSeries = false,
                revision = DEBUG_REVISION,
                updatedAtEpochSec = now,
                message = "Managed access is temporarily paused for this test.",
                supportCode = "TEST-PAUSED",
            )
            DebugManagedPolicyPreset.REVOKED -> ManagedAccessPolicy(
                state = ManagedAccessState.REVOKED,
                allowLive = false,
                allowMovies = false,
                allowSeries = false,
                revision = DEBUG_REVISION,
                updatedAtEpochSec = now,
                message = "Managed access was revoked for this test.",
                supportCode = "TEST-REVOKED",
            )
            DebugManagedPolicyPreset.UNMANAGED -> ManagedAccessPolicy()
        }
        apply(next)
    }

    // Restores the persisted policy at startup; unknown or missing state falls
    // back to UNMANAGED, matching a fresh install.
    private fun load(): ManagedAccessPolicy {
        val state = preferences.getString(KEY_STATE, null)
            ?.let { stored -> ManagedAccessState.entries.firstOrNull { it.name == stored } }
            ?: ManagedAccessState.UNMANAGED
        return ManagedAccessPolicy(
            state = state,
            allowLive = preferences.getBoolean(KEY_ALLOW_LIVE, true),
            allowMovies = preferences.getBoolean(KEY_ALLOW_MOVIES, true),
            allowSeries = preferences.getBoolean(KEY_ALLOW_SERIES, true),
            message = preferences.getString(KEY_MESSAGE, null),
            supportCode = preferences.getString(KEY_SUPPORT_CODE, null),
            revision = preferences.getLong(KEY_REVISION, 0L),
            updatedAtEpochSec = preferences.getLong(KEY_UPDATED_AT, 0L),
        )
    }

    // Wall-clock seconds, matching the portal's updated-at resolution.
    private fun nowEpochSec(): Long = System.currentTimeMillis() / 1_000L

    private companion object {
        const val PREFS_NAME = "managed_access_policy"
        const val KEY_STATE = "state"
        const val KEY_ALLOW_LIVE = "allow_live"
        const val KEY_ALLOW_MOVIES = "allow_movies"
        const val KEY_ALLOW_SERIES = "allow_series"
        const val KEY_MESSAGE = "message"
        const val KEY_SUPPORT_CODE = "support_code"
        const val KEY_REVISION = "revision"
        const val KEY_UPDATED_AT = "updated_at"
        const val DEBUG_REVISION = 9_999L
    }
}

/** Debug-menu presets that map onto representative managed policies. */
enum class DebugManagedPolicyPreset(val label: String) {
    FULL_ACCESS("Full access"),
    LIVE_ONLY("Live only"),
    SUSPENDED("Paused"),
    REVOKED("Revoked"),
    UNMANAGED("Personal"),
}
