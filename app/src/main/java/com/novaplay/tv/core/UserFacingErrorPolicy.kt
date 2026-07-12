package com.novaplay.tv.core

/** Stable categories that the UI can translate without exposing provider internals. */
enum class UserErrorCategory {
    OFFLINE,
    TIMEOUT,
    UNAUTHORIZED,
    NOT_FOUND,
    RATE_LIMITED,
    PROVIDER_UNAVAILABLE,
    STORAGE,
    UNSUPPORTED_MEDIA,
    CONFIGURATION,
    CONTENT_UNAVAILABLE,
    USER_MESSAGE,
    UNKNOWN,
}

/**
 * Privacy-safe error presentation decision. [safeDetail] is populated only when
 * the input already looks like a short, human-written message rather than a raw
 * exception, URL, response body or stack-trace fragment.
 */
data class UserFacingError(
    val category: UserErrorCategory,
    val retryable: Boolean,
    val safeDetail: String? = null,
)

/** Pure classification rules shared by Compose error states and unit tests. */
object UserFacingErrorPolicy {

    fun from(error: Throwable?, fallback: String = "Something went wrong"): UserFacingError =
        from(SafeErrorMessage.from(error, fallback), fallback)

    fun from(rawMessage: String?, fallback: String = "Something went wrong"): UserFacingError {
        val safe = SafeErrorMessage.sanitize(rawMessage, fallback)
        val normalized = safe.lowercase()
        val httpCode = HTTP_CODE.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return when {
            normalized.contains("unknownhost") ||
                normalized.contains("unable to resolve host") ||
                normalized.contains("network is unreachable") ||
                normalized.contains("no internet") ||
                normalized.contains("connection failed") ->
                UserFacingError(UserErrorCategory.OFFLINE, retryable = true)

            normalized.contains("timeout") || normalized.contains("timed out") ->
                UserFacingError(UserErrorCategory.TIMEOUT, retryable = true)

            httpCode == 401 || httpCode == 403 ||
                normalized.contains("not authorized") || normalized.contains("unauthorized") ||
                normalized.contains("session was revoked") ->
                UserFacingError(UserErrorCategory.UNAUTHORIZED, retryable = false)

            httpCode == 404 || normalized.contains("not found") ->
                UserFacingError(UserErrorCategory.NOT_FOUND, retryable = false)

            httpCode == 429 || normalized.contains("too many requests") ||
                normalized.contains("rate limit") ->
                UserFacingError(UserErrorCategory.RATE_LIMITED, retryable = true)

            httpCode != null && httpCode in 500..599 ->
                UserFacingError(UserErrorCategory.PROVIDER_UNAVAILABLE, retryable = true)

            normalized.contains("no space") || normalized.contains("not enough storage") ||
                normalized.contains("disk full") || normalized.contains("enospc") ->
                UserFacingError(UserErrorCategory.STORAGE, retryable = false)

            normalized.contains("codec") || normalized.contains("decoder") ||
                normalized.contains("unsupported format") || normalized.contains("media codec") ->
                UserFacingError(UserErrorCategory.UNSUPPORTED_MEDIA, retryable = false)

            normalized.contains("portal") &&
                (normalized.contains("not configured") || normalized.contains("configuration") ||
                    normalized.contains("must use https")) ->
                UserFacingError(UserErrorCategory.CONFIGURATION, retryable = false)

            normalized.contains("no stream available") ||
                normalized.contains("content unavailable") ||
                normalized.contains("provider did not make") ->
                UserFacingError(UserErrorCategory.CONTENT_UNAVAILABLE, retryable = true)

            looksHumanWritten(safe) ->
                UserFacingError(UserErrorCategory.USER_MESSAGE, retryable = true, safeDetail = safe)

            else -> UserFacingError(UserErrorCategory.UNKNOWN, retryable = true)
        }
    }

    private fun looksHumanWritten(message: String): Boolean {
        if (message.length !in 4..180) return false
        val normalized = message.lowercase()
        return TECHNICAL_MARKERS.none(normalized::contains) &&
            !normalized.contains("[redacted") &&
            !message.contains('{') &&
            !message.contains('}')
    }

    private val HTTP_CODE = Regex("""(?:http\s*)?([1-5]\d{2})""", RegexOption.IGNORE_CASE)
    private val TECHNICAL_MARKERS = listOf(
        "exception",
        "java.",
        "kotlin.",
        "okhttp",
        "retrofit",
        "stacktrace",
        "caused by",
        "errno",
        "failed to execute",
    )
}
