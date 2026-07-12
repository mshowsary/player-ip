package com.novaplay.tv.data.repo

import androidx.room.withTransaction
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.core.DeviceIdentity
import com.novaplay.tv.core.PortalEndpointPolicy
import com.novaplay.tv.data.db.NovaDatabase
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.remote.MockPortal
import com.novaplay.tv.data.remote.PortalApi
import com.novaplay.tv.data.remote.PortalPlaylistDto
import com.novaplay.tv.data.security.PlaylistSecrets
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of asking the portal whether this device has playlists assigned. */
sealed interface ActivationCheck {
    data class Activated(val playlistCount: Int) : ActivationCheck
    data object NotRegistered : ActivationCheck
    data object KeyMismatch : ActivationCheck
    data class Failure(val message: String) : ActivationCheck
}

/**
 * Resolves whether this device is activated against the portal and mirrors the
 * assigned playlists into Room. Prefers the paired bearer session and only
 * falls back to the legacy MAC + key contract while migration is in flight.
 */
@Singleton
class ActivationRepository @Inject constructor(
    private val portalApi: PortalApi,
    private val portalPairingRepository: PortalPairingRepository,
    private val deviceIdentity: DeviceIdentity,
    private val db: NovaDatabase,
    private val playlistSecrets: PlaylistSecrets,
    private val managedAccessRepository: ManagedAccessRepository,
) {
    /** True once any playlist (portal-managed or personal) exists locally. */
    suspend fun hasPlaylists(): Boolean = db.playlistDao().count() > 0

    /**
     * Queries the portal for this device's assignment and attaches the returned
     * playlists plus access policy. Network and configuration errors are
     * reported as [ActivationCheck.Failure] rather than thrown.
     */
    suspend fun checkAndAttach(): ActivationCheck {
        if (BuildConfig.MOCK_ACTIVATION) {
            managedAccessRepository.applyPortalPolicy(MockPortal.policy)
            attachManagedPlaylists(MockPortal.playlists)
            return ActivationCheck.Activated(MockPortal.playlists.size)
        }

        // Once device-code pairing has issued a bearer session, never send the
        // legacy MAC + visible key in a query string again.
        if (portalPairingRepository.hasStoredSession()) {
            return portalPairingRepository.authorizedPlaylists().fold(
                onSuccess = { playlists ->
                    if (playlists.isEmpty()) {
                        ActivationCheck.NotRegistered
                    } else {
                        attachManagedPlaylists(playlists)
                        ActivationCheck.Activated(playlists.size)
                    }
                },
                onFailure = { error -> ActivationCheck.Failure(error.message ?: "Portal session failed") },
            )
        }

        val endpoint = PortalEndpointPolicy.assess(BuildConfig.PORTAL_BASE_URL, BuildConfig.DEBUG)
        if (!endpoint.configured || !endpoint.transportAllowed) {
            return ActivationCheck.Failure(endpoint.issue ?: "Provider portal is unavailable")
        }

        // Temporary migration path for the existing portal contract. This will
        // be removed after the pairing UI and server are deployed together.
        val identity = deviceIdentity.get()
        val response = try {
            portalApi.getPlaylists(identity.mac, identity.deviceKey)
        } catch (e: Exception) {
            return ActivationCheck.Failure(e.message ?: "Network error")
        }

        return when {
            response.isSuccessful -> {
                val body = response.body()
                managedAccessRepository.applyPortalPolicy(body?.policy)
                val playlists = body?.playlists.orEmpty()
                if (playlists.isEmpty()) {
                    ActivationCheck.NotRegistered
                } else {
                    attachManagedPlaylists(playlists)
                    ActivationCheck.Activated(playlists.size)
                }
            }
            response.code() == 404 -> ActivationCheck.NotRegistered
            response.code() == 403 -> ActivationCheck.KeyMismatch
            else -> ActivationCheck.Failure("Portal error: HTTP ${response.code()}")
        }
    }

    /**
     * Atomically upserts a managed assignment received during secure pairing.
     * Credentials are sealed before Room sees them and existing synced content
     * or the active selection is preserved.
     */
    suspend fun attachManagedPlaylists(dtos: List<PortalPlaylistDto>): Int {
        if (dtos.isEmpty()) return 0

        val dao = db.playlistDao()
        db.withTransaction {
            for (dto in dtos) {
                val existing = dao.getByPortalId(dto.id)
                val plaintext = if (existing == null) {
                    Playlist(
                        portalId = dto.id,
                        name = dto.name,
                        type = dto.type,
                        server = dto.server,
                        username = dto.username,
                        password = dto.password,
                        url = dto.url,
                    )
                } else {
                    existing.copy(
                        name = dto.name,
                        type = dto.type,
                        server = dto.server,
                        username = dto.username,
                        password = dto.password,
                        url = dto.url,
                    )
                }
                val sealed = playlistSecrets.seal(plaintext)
                if (existing == null) dao.insert(sealed) else dao.update(sealed)
            }
            if (dao.getActive() == null) {
                dao.getByPortalId(dtos.first().id)?.let { dao.setActive(it.id) }
            }
        }
        return dtos.size
    }
}
