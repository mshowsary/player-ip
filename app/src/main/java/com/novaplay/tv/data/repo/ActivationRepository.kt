package com.novaplay.tv.data.repo

import androidx.room.withTransaction
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.core.DeviceIdentity
import com.novaplay.tv.data.db.NovaDatabase
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.remote.MockPortal
import com.novaplay.tv.data.remote.PortalApi
import com.novaplay.tv.data.remote.PortalPlaylistDto
import com.novaplay.tv.data.security.PlaylistSecrets
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ActivationCheck {
    data class Activated(val playlistCount: Int) : ActivationCheck
    data object NotRegistered : ActivationCheck
    data object KeyMismatch : ActivationCheck
    data class Failure(val message: String) : ActivationCheck
}

@Singleton
class ActivationRepository @Inject constructor(
    private val portalApi: PortalApi,
    private val portalPairingRepository: PortalPairingRepository,
    private val deviceIdentity: DeviceIdentity,
    private val db: NovaDatabase,
    private val playlistSecrets: PlaylistSecrets,
) {
    suspend fun hasPlaylists(): Boolean = db.playlistDao().count() > 0

    suspend fun checkAndAttach(): ActivationCheck {
        if (BuildConfig.MOCK_ACTIVATION) {
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
                val playlists = response.body()?.playlists.orEmpty()
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
