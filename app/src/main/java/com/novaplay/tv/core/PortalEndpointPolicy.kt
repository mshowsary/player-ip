package com.novaplay.tv.core

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.URI

/** Result shown in diagnostics and used to fail closed before portal traffic. */
data class PortalEndpointAssessment(
    val configured: Boolean,
    val transportAllowed: Boolean,
    val displayHost: String,
    val issue: String? = null,
)

/**
 * The app intentionally continues to support HTTP for user-supplied IPTV
 * streams, because many providers still expose MPEG-TS that way. The provider
 * control plane is a separate trust boundary and must use HTTPS in production.
 */
object PortalEndpointPolicy {
    private val placeholderHosts = setOf("portal.example.com", "example.invalid")
    private val localDebugHosts = setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")

    fun assess(rawBaseUrl: String, debug: Boolean): PortalEndpointAssessment {
        val uri = runCatching { URI(rawBaseUrl.trim()) }.getOrNull()
            ?: return invalid("Invalid portal address")
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase().orEmpty()
        if (scheme.isNullOrBlank() || host.isBlank()) return invalid("Invalid portal address")
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) {
            return PortalEndpointAssessment(
                configured = false,
                transportAllowed = false,
                displayHost = host,
                issue = "Portal address cannot contain credentials, a query, or a fragment",
            )
        }

        val localDebugHttp = debug && scheme == "http" && host in localDebugHosts
        val transportAllowed = scheme == "https" || localDebugHttp
        if (!transportAllowed) {
            return PortalEndpointAssessment(
                configured = false,
                transportAllowed = false,
                displayHost = host,
                issue = "Provider portal must use HTTPS",
            )
        }

        val configured = host !in placeholderHosts
        return PortalEndpointAssessment(
            configured = configured,
            transportAllowed = true,
            displayHost = host,
            issue = if (configured) null else "Provider portal is not configured in this build",
        )
    }

    fun requireConfigured(rawBaseUrl: String, debug: Boolean) {
        val assessment = assess(rawBaseUrl, debug)
        require(assessment.transportAllowed && assessment.configured) {
            assessment.issue ?: "Provider portal is unavailable"
        }
    }

    private fun invalid(message: String) = PortalEndpointAssessment(
        configured = false,
        transportAllowed = false,
        displayHost = "Not configured",
        issue = message,
    )
}

/**
 * Prevents redirects or accidental absolute URLs from moving portal tokens to
 * a different host. This client is used only by PortalApi, never stream traffic.
 */
class PortalRequestGuard(
    rawBaseUrl: String,
    private val debug: Boolean,
) : Interceptor {
    private val baseUri = URI(rawBaseUrl.trim())
    private val expectedHost = requireNotNull(baseUri.host) { "Portal host is missing" }.lowercase()
    private val expectedPort = effectivePort(baseUri.scheme, baseUri.port)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val localDebugHttp = debug && url.scheme == "http" && url.host in LOCAL_DEBUG_HOSTS
        val secureTransport = url.isHttps || localDebugHttp
        val sameAuthority = url.host.equals(expectedHost, ignoreCase = true) &&
            effectivePort(url.scheme, url.port) == expectedPort

        if (!secureTransport || !sameAuthority) {
            throw IOException("Blocked unsafe provider portal request")
        }
        return chain.proceed(request)
    }

    private fun effectivePort(scheme: String?, port: Int): Int = when {
        port >= 0 -> port
        scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    private companion object {
        val LOCAL_DEBUG_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")
    }
}
