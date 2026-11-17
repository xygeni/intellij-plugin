package com.github.xygeni.intellij.services

import com.intellij.util.net.JdkProxyProvider
import com.intellij.util.net.ssl.CertificateManager
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import com.github.xygeni.intellij.settings.XygeniSettings
import java.io.IOException
import java.net.Authenticator.RequestorType
import java.util.concurrent.TimeUnit

/**
 * Builds the OkHttp clients used by the plugin so every request goes through the IDE's
 * network stack instead of the bare JVM defaults.
 *
 * - TLS trust comes from [CertificateManager]: JBR cacerts + the certificates the user accepted
 *   in Settings > Tools > Server Certificates (corporate proxies such as Zscaler). A bare
 *   `OkHttpClient()` only sees the JBR cacerts and fails with `PKIX path building failed`
 *   behind an intercepting proxy (xygeni/tech-support#375).
 * - Proxy and proxy credentials come from the IDE HTTP proxy settings via [JdkProxyProvider].
 * - Plain http to a non-loopback host is refused by [InsecureHttpInterceptor] unless "Allow insecure HTTP" is on
 *   (xygeni/xygeni-product-backlog#1438): every request of the plugin goes through here, so no caller can forget it.
 */
object XygeniHttpClient {

    fun create(connectTimeoutSeconds: Long = 15, readTimeoutSeconds: Long = 30): OkHttpClient {
        val certificateManager = CertificateManager.getInstance()
        val proxyProvider = JdkProxyProvider.getInstance()
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .sslSocketFactory(certificateManager.sslContext.socketFactory, certificateManager.trustManager)
            .proxySelector(proxyProvider.proxySelector)
            .proxyAuthenticator(IdeProxyAuthenticator(proxyProvider))
            .addInsecureHttpGuard { XygeniSettings.getInstance().allowInsecureHttp }
            .build()
    }

    /** As an application interceptor (before any connection) and a network one (each redirect hop). */
    fun OkHttpClient.Builder.addInsecureHttpGuard(allowInsecureHttp: () -> Boolean): OkHttpClient.Builder {
        val guard = InsecureHttpInterceptor(allowInsecureHttp)
        return addInterceptor(guard).addNetworkInterceptor(guard)
    }

    /**
     * Refuses a request to `http://` on a non-loopback host unless the user allowed insecure http. It reads the URL
     * as OkHttp will send it, so a string `java.net.URI` cannot parse is still caught here.
     */
    class InsecureHttpInterceptor(private val allowInsecureHttp: () -> Boolean) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url
            if (!url.isHttps && !InsecureUrl.isLoopbackHost(url.host) && !allowInsecureHttp()) {
                throw IOException("${InsecureUrl.REFUSED} (${url.scheme}://${url.host})")
            }
            return chain.proceed(chain.request())
        }
    }

    /** Answers HTTP 407 with the credentials configured in the IDE proxy settings, if any. */
    private class IdeProxyAuthenticator(private val proxyProvider: JdkProxyProvider) : Authenticator {

        override fun authenticate(route: Route?, response: Response): okhttp3.Request? {
            if (response.request.header("Proxy-Authorization") != null) return null

            val proxyAddress = route?.proxy?.address() as? java.net.InetSocketAddress ?: return null
            val requestUrl = response.request.url
            val passwordAuthentication = proxyProvider.authenticator.requestPasswordAuthenticationInstance(
                proxyAddress.hostString,
                proxyAddress.address,
                proxyAddress.port,
                requestUrl.scheme,
                "Proxy authentication required",
                "Basic",
                requestUrl.toUrl(),
                RequestorType.PROXY
            ) ?: return null

            val credential = Credentials.basic(passwordAuthentication.userName, String(passwordAuthentication.password))
            return response.request.newBuilder().header("Proxy-Authorization", credential).build()
        }
    }
}
