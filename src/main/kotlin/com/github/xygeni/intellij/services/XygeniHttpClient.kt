package com.github.xygeni.intellij.services

import com.intellij.util.net.JdkProxyProvider
import com.intellij.util.net.ssl.CertificateManager
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
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
            .build()
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
