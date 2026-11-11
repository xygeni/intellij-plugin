package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.services.XygeniHttpClient.addInsecureHttpGuard
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** The http guard sits in the transport, so no request of the plugin can send the token in clear (#1438). */
class InsecureHttpInterceptorTest {

    private fun client(allow: Boolean) = OkHttpClient.Builder().addInsecureHttpGuard { allow }.build()

    @Test
    fun `http to a remote host is refused before any connection`() {
        val request = Request.Builder().url("http://example.com/deps-doctor-service/language")
            .header("Authorization", "Bearer secret").build()
        try {
            client(allow = false).newCall(request).execute().close()
            fail("the request must be refused")
        } catch (e: IOException) {
            assertTrue(e.message!!, e.message!!.startsWith(InsecureUrl.REFUSED))
        }
    }

    @Test
    fun `loopback http and an explicit opt-in go through the guard`() {
        // Port 9 (discard) is closed on a CI box: a connection error, not the guard, proves the request was let out.
        for ((url, allow) in listOf("http://127.0.0.1:9/x" to false, "http://localhost:9/x" to false, "http://127.0.0.1:9/x" to true)) {
            try {
                client(allow).newCall(Request.Builder().url(url).build()).execute().close()
            } catch (e: IOException) {
                assertTrue("$url: ${e.message}", e.message?.startsWith(InsecureUrl.REFUSED) != true)
            }
        }
    }
}
