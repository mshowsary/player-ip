package com.novaplay.tv.core

/** Removes provider URLs and common credential fields before an error reaches logs or UI. */
object SafeErrorMessage {
    fun from(error: Throwable?, fallback: String = "Operation failed"): String {
        val raw = error?.message?.takeIf { it.isNotBlank() } ?: fallback
        return raw
            .replace(URL_REGEX, "[redacted URL]")
            .replace(CREDENTIAL_QUERY_REGEX) { match ->
                "${match.groupValues[1]}=•••"
            }
            .replace(CREDENTIAL_JSON_REGEX) { match ->
                "${match.groupValues[1]}•••${match.groupValues[3]}"
            }
    }

    private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val CREDENTIAL_QUERY_REGEX = Regex("""(?i)(username|password)=([^&\s]+)""")
    private val CREDENTIAL_JSON_REGEX = Regex("""(?i)(\"?(?:username|password)\"?\s*[:=]\s*\"?)([^\"\s,}]+)(\"?)""")
}
