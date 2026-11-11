package com.github.xygeni.intellij.services

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Plain-http server URLs (xygeni/xygeni-product-backlog#1438). The plugin sends the API token as a bearer header on
 * every request, so over `http://` it travels in clear. Rule shared by the four Xygeni IDE plugins: `https://` is the
 * normal case; `http://` to a loopback host (localhost, 127.0.0.1, ::1) is allowed silently, the traffic never leaves
 * the machine; `http://` to any other host is refused unless the user opts in with "Allow insecure HTTP", and then
 * the connection status keeps warning that the token travels in clear.
 */
object InsecureUrl {
    const val WARNING = "Connected over insecure http: the API token travels in clear"
    const val REFUSED = "Insecure URL: the server URL uses http and the API token would travel in clear. " +
        "Use https, or enable \"Allow insecure HTTP\" in the Xygeni settings if this server really has no TLS"

    /** The whole 127/8 block, as an address: a DNS name such as 127.example.com is not loopback. */
    private val LOOPBACK_V4 = Regex("""127\.\d{1,3}\.\d{1,3}\.\d{1,3}""")

    fun isLoopbackHost(hostname: String?): Boolean {
        val host = hostname?.trim()?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return false
        return host == "localhost" || host.endsWith(".localhost") || LOOPBACK_V4.matches(host) ||
            host == "::1" || host == "0:0:0:0:0:0:0:1"
    }

    /**
     * True for an `http://` URL whose host is not loopback: the token would leave the machine in clear. Parsed the way
     * OkHttp sends it (it percent-encodes spaces, `|`, `{` that `java.net.URI` rejects), and an `http:` string nothing
     * can parse counts as insecure: the check must not fail open.
     */
    fun isInsecureRemote(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        trimmed.toHttpUrlOrNull()?.let { return !it.isHttps && !isLoopbackHost(it.host) }
        return trimmed.startsWith("http:", ignoreCase = true)
    }

    /** Why the URL cannot be used, or null when it can (https, loopback, or insecure but allowed). */
    fun problem(url: String?, allowInsecureHttp: Boolean): String? =
        if (isInsecureRemote(url) && !allowInsecureHttp) REFUSED else null
}
