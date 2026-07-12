package com.novaplay.tv.core

/** Removes provider URLs and common credential fields before an error reaches logs or UI. */
object SafeErrorMessage {
    /**
     * Returns a privacy-safe message with URLs, bearer tokens and common portal/
     * playlist credential fields masked. New lines and repeated whitespace are
     * collapsed so response bodies cannot create oversized or misleading UI.
     */
    fun from(error: Throwable?, fallback: String = "Operation failed"): String =
        sanitize(error?.message, fallback)

    fun sanitize(rawMessage: String?, fallback: String = "Operation failed"): String {
        val raw = rawMessage?.takeIf { it.isNotBlank() } ?: fallback
        return raw
            .replace(URL_REGEX, "[redacted URL]")
            .replace(BEARER_REGEX, "Bearer •••")
            .replace(CREDENTIAL_QUERY_REGEX) { match ->
                "${match.groupValues[1]}=•••"
            }
            .replace(CREDENTIAL_JSON_REGEX) { match ->
                "${match.groupValues[1]}•••${match.groupValues[3]}"
            }
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .take(MAX_MESSAGE_LENGTH)
            .ifBlank { fallback }
    }

    private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val BEARER_REGEX = Regex("""(?i)bearer\s+[a-z0-9._~+/=-]+""")
    private val CREDENTIAL_QUERY_REGEX = Regex(
        """(?i)(username|password|token|access_token|refresh_token|device_id|device_key|key)=([^&\s]+)""",
    )
    private val CREDENTIAL_JSON_REGEX = Regex(
        """(?i)(\"?(?:username|password|token|access_token|refresh_token|device_id|device_key|key)\"?\s*[:=]\s*\"?)([^\"\s,}]+)(\"?)""",
    )
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private const val MAX_MESSAGE_LENGTH = 500
}
