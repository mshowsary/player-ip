package com.novaplay.tv.ui.playlists

import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.repo.PlaylistDraft

/**
 * Per-field validation errors for the playlist editor; a null field means it is valid.
 * Only the fields relevant to the draft's type are ever populated.
 */
data class PlaylistFormErrors(
    val name: String? = null,
    val server: String? = null,
    val username: String? = null,
    val password: String? = null,
    val url: String? = null,
) {
    /** True when no field has an error, i.e. the draft may be tested or saved. */
    val isValid: Boolean
        get() = name == null && server == null && username == null && password == null && url == null
}

/**
 * Validates a playlist draft by type: M3U needs a name and a file:/http(s) URL, Xtream needs
 * a name, http(s) server URL, username and password. Pure Kotlin (no Android URI parsing) so
 * it stays testable with plain JUnit, which is why it lives outside the ViewModel.
 */
fun validatePlaylistDraft(draft: PlaylistDraft): PlaylistFormErrors {
    val nameError = if (draft.name.isBlank()) "Enter a playlist name" else null

    return when (draft.type) {
        Playlist.TYPE_M3U -> PlaylistFormErrors(
            name = nameError,
            url = when {
                draft.url.isBlank() -> "Enter an M3U URL"
                draft.url.startsWith("file:") -> null
                draft.url.startsWith("http://") || draft.url.startsWith("https://") -> null
                else -> "Use a complete http:// or https:// URL"
            },
        )
        else -> PlaylistFormErrors(
            name = nameError,
            server = when {
                draft.server.isBlank() -> "Enter the Xtream server URL"
                draft.server.startsWith("http://") || draft.server.startsWith("https://") -> null
                else -> "Use a complete http:// or https:// URL"
            },
            username = if (draft.username.isBlank()) "Enter the username" else null,
            password = if (draft.password.isBlank()) "Enter the password" else null,
        )
    }
}
