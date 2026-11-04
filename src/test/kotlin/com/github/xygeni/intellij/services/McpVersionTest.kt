package com.github.xygeni.intellij.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MCP server update detection (xygeni/xygeni-product-backlog#2382). */
class McpVersionTest {

    @Test
    fun `parse accepts plain versions and ignores prefix and suffix`() {
        assertEquals(listOf(1, 2, 3), McpVersion.parse("1.2.3"))
        assertEquals(listOf(1, 2, 3), McpVersion.parse(" v1.2.3\n"))
        assertEquals(listOf(1, 2, 3), McpVersion.parse("1.2.3-SNAPSHOT"))
        assertEquals(listOf(1, 2), McpVersion.parse("1.2+build.7"))
        assertNull(McpVersion.parse(null))
        assertNull(McpVersion.parse(""))
        assertNull(McpVersion.parse("unknown"))
        assertNull(McpVersion.parse("<?xml version=\"1.0\"?><Error>AccessDenied</Error>"))
    }

    @Test
    fun `compare is numeric and pads missing segments`() {
        assertTrue(McpVersion.compare("1.10.0", "1.9.9") > 0)
        assertTrue(McpVersion.compare("1.2", "1.2.0") == 0)
        assertTrue(McpVersion.compare("1.2.1", "1.2") > 0)
        assertTrue(McpVersion.compare("unknown", "1.0.0") < 0)
    }

    @Test
    fun `isNewer treats an unknown installed version as outdated and an invalid remote as no update`() {
        assertTrue(McpVersion.isNewer("1.2.3", "1.2.2"))
        assertFalse(McpVersion.isNewer("1.2.2", "1.2.2"))
        assertFalse(McpVersion.isNewer("1.2.1", "1.2.2"))
        assertTrue(McpVersion.isNewer("1.2.3", null))
        assertTrue(McpVersion.isNewer("1.2.3", "unknown"))
        assertFalse(McpVersion.isNewer(null, "1.2.2"))
        assertFalse(McpVersion.isNewer("AccessDenied", null))
    }

    @Test
    fun `version and checksum files are parsed in the published formats`() {
        assertEquals("1.2.2", McpVersion.fromVersionFile("1.2.2\n"))
        assertEquals("1.2.2", McpVersion.fromVersionFile("\n  v1.2.2  \n"))
        assertNull(McpVersion.fromVersionFile("<Error><Code>AccessDenied</Code></Error>"))
        assertNull(McpVersion.fromVersionFile(null))
        val hash = "A".repeat(64)
        assertEquals(hash.lowercase(), McpVersion.fromChecksumFile("$hash  xygeni-mcp-server.jar\n"))
        assertEquals(hash.lowercase(), McpVersion.fromChecksumFile(hash))
        assertNull(McpVersion.fromChecksumFile("not a checksum"))
        assertNull(McpVersion.fromChecksumFile("<Error>AccessDenied</Error>"))
    }
}
