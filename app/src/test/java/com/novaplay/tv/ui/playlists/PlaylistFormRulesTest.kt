package com.novaplay.tv.ui.playlists

import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.repo.PlaylistDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFormRulesTest {

    @Test
    fun validXtreamDraftPasses() {
        val errors = validatePlaylistDraft(
            PlaylistDraft(
                name = "Living room",
                type = Playlist.TYPE_XTREAM,
                server = "https://provider.example:8080",
                username = "user",
                password = "pass",
            ),
        )

        assertTrue(errors.isValid)
    }

    @Test
    fun incompleteXtreamDraftReportsEveryMissingField() {
        val errors = validatePlaylistDraft(PlaylistDraft(type = Playlist.TYPE_XTREAM))

        assertFalse(errors.isValid)
        assertTrue(errors.name != null)
        assertTrue(errors.server != null)
        assertTrue(errors.username != null)
        assertTrue(errors.password != null)
    }

    @Test
    fun importedM3uFileDoesNotRequireHttpUrl() {
        val errors = validatePlaylistDraft(
            PlaylistDraft(
                name = "Imported",
                type = Playlist.TYPE_M3U,
                url = "file:/data/user/0/com.novaplay.tv/files/list.m3u",
            ),
        )

        assertTrue(errors.isValid)
        assertNull(errors.url)
    }

    @Test
    fun incompleteM3uUrlIsRejected() {
        val errors = validatePlaylistDraft(
            PlaylistDraft(
                name = "Remote list",
                type = Playlist.TYPE_M3U,
                url = "provider.example/list.m3u",
            ),
        )

        assertFalse(errors.isValid)
        assertTrue(errors.url != null)
    }
}
