package com.novaplay.tv.ui.playlists

import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.repo.PlaylistDraft

data class PlaylistFormErrors(
    val name: String? = null,
    val server: String? = null,
    val username: String? = null,
    val password: String? = null,
    val url: String? = null,
) {
    val isValid: Boolean
        get() = name == null && server == null && username == null && password == null && url == null
}

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
