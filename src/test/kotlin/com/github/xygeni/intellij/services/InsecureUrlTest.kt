package com.github.xygeni.intellij.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain-http server URLs send the token in clear (xygeni/xygeni-product-backlog#1438). */
class InsecureUrlTest {

    @Test
    fun `loopback hosts never leave the machine`() {
        for (host in listOf("localhost", "LOCALHOST", "app.localhost", "127.0.0.1", "127.1.2.3", "::1", "[::1]", "0:0:0:0:0:0:0:1")) {
            assertTrue(host, InsecureUrl.isLoopbackHost(host))
        }
        for (host in listOf("xygeni.local", "10.0.0.5", "api.xygeni.io", "localhost.evil.com", "", null)) {
            assertFalse(host ?: "null", InsecureUrl.isLoopbackHost(host))
        }
    }

    @Test
    fun `https and http to loopback are fine, http to any other host is insecure`() {
        assertFalse(InsecureUrl.isInsecureRemote("https://api.xygeni.io"))
        assertFalse("our own dev backend", InsecureUrl.isInsecureRemote("http://localhost:8086/deps-doctor-service"))
        assertFalse(InsecureUrl.isInsecureRemote("http://127.0.0.1:8086"))
        assertFalse(InsecureUrl.isInsecureRemote("http://[::1]:8086"))
        assertTrue(InsecureUrl.isInsecureRemote("http://xygeni.corp.local/deps-doctor-service"))
        assertTrue("a missing s is the typical typo", InsecureUrl.isInsecureRemote("http://api.xygeni.io"))
        assertFalse(InsecureUrl.isInsecureRemote(null))
        assertFalse("a malformed URL is somebody else's error", InsecureUrl.isInsecureRemote("not a url"))
    }

    @Test
    fun `the problem is reported unless the user allowed insecure http`() {
        assertEquals(InsecureUrl.REFUSED, InsecureUrl.problem("http://xygeni.corp.local", false))
        assertNull(InsecureUrl.problem("http://xygeni.corp.local", true))
        assertNull(InsecureUrl.problem("http://localhost:8086", false))
        assertNull(InsecureUrl.problem("https://api.xygeni.io", false))
    }
}
