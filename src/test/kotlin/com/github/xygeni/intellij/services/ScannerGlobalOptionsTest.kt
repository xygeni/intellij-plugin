package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.services.ScannerGlobalOptions.SKIP_SSL_VERIFY
import com.github.xygeni.intellij.services.ScannerGlobalOptions.VERBOSE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scanner global options go before the command (xygeni/tech-support#378). */
class ScannerGlobalOptionsTest {

    @Test
    fun `split honours whitespace and quotes`() {
        assertEquals(emptyList<String>(), ScannerGlobalOptions.split(null))
        assertEquals(emptyList<String>(), ScannerGlobalOptions.split("   "))
        assertEquals(listOf("--skip-update", "-v"), ScannerGlobalOptions.split(" --skip-update\t-v  "))
        assertEquals(listOf("-cop", "a=b c", "-cop", "x=y z"), ScannerGlobalOptions.split("-cop \"a=b c\" -cop 'x=y z'"))
        assertEquals(listOf("-cop", "key="), ScannerGlobalOptions.split("-cop key=\"\""))
    }

    @Test
    fun `build puts the checked options first without duplicates or blocked options`() {
        assertEquals(emptyList<String>(), ScannerGlobalOptions.build(emptyList(), ""))
        assertEquals(listOf(SKIP_SSL_VERIFY), ScannerGlobalOptions.build(listOf(SKIP_SSL_VERIFY), null))
        assertEquals(listOf(SKIP_SSL_VERIFY, VERBOSE, "-cop", "a=b"),
            ScannerGlobalOptions.build(listOf(SKIP_SSL_VERIFY, VERBOSE), "-cop a=b"))
        assertEquals(listOf("--skip-ssl-verify", "-v"), ScannerGlobalOptions.build(listOf(SKIP_SSL_VERIFY), "--skip-ssl-verify -v"))
        assertEquals(listOf("--skip-update"), ScannerGlobalOptions.build(emptyList(), "-q --skip-update --quiet"))
        assertEquals(listOf("-q", "--quiet"), ScannerGlobalOptions.blockedIn("-q --skip-update --quiet"))
    }

    @Test
    fun `the API key never reaches the command line with or without its value`() {
        assertEquals(listOf("--skip-update"), ScannerGlobalOptions.build(emptyList(), "--api-key abc --skip-update"))
        assertEquals(listOf("--skip-update"), ScannerGlobalOptions.build(emptyList(), "--skip-update --api-key=abc"))
        assertEquals(listOf("--api-key", "-q", "--api-key"), ScannerGlobalOptions.blockedIn("--api-key abc -q --api-key=def"))
    }

    @Test
    fun `isCertificateError recognises the JVM TLS validation failures`() {
        assertTrue(ScannerGlobalOptions.isCertificateError("javax.net.ssl.SSLHandshakeException: PKIX path building failed: " +
            "sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target"))
        assertFalse(ScannerGlobalOptions.isCertificateError("Scan finished with exit code 127"))
    }
}
