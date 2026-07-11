package com.novaplay.tv.core

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Creates deterministic provider-facing identifiers for content that has no
 * numeric provider id. The returned Long is safe to persist in Room and stays
 * stable when an M3U file is reordered.
 */
object StableContentId {

    /**
     * Hashes the tvg-id (case-insensitive) when present, falling back to the stream URL,
     * into a positive Long via SHA-256. Re-importing the same playlist therefore keeps
     * watch state; 0 is remapped to 1 so the id never collides with Room's "unset" key.
     */
    fun forM3u(
        tvgId: String?,
        url: String,
    ): Long {
        val stableSource = tvgId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { "tvg:${it.lowercase(Locale.ROOT)}" }
            ?: "url:${url.trim()}"

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(stableSource.toByteArray(StandardCharsets.UTF_8))
        val value = ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long and Long.MAX_VALUE
        return value.takeIf { it != 0L } ?: 1L
    }
}
