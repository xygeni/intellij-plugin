package com.github.xygeni.intellij.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-project scan configuration (xygeni/xygeni-product-backlog#415). */
class ScanConfigTest {

    @Test
    fun `defaults are what the plugin always ran`() {
        val config = ScanConfig.defaults()
        assertEquals(listOf("deps", "secrets", "misconf", "iac", "suspectdeps", "sast", "malware", "quality", "apisec", "ai"), config.scans)
        assertEquals(listOf("secrets", "iac", "sast", "malware"), config.onSave)
        assertTrue(config.excludes.isEmpty())
        assertTrue(config.isDefault)
    }

    @Test
    fun `a missing, empty or broken file gives the defaults`() {
        assertEquals(ScanConfig.defaults(), ScanConfig.parse(null))
        assertEquals(ScanConfig.defaults(), ScanConfig.parse(""))
        assertEquals(ScanConfig.defaults(), ScanConfig.parse("{ not json"))
        assertEquals(ScanConfig.defaults(), ScanConfig.parse("[1, 2]"))
        assertEquals("a key that is not a list counts as empty, so the full scan falls back", ScanConfig.defaults(), ScanConfig.parse("{\"scans\": \"sast\"}"))
    }

    @Test
    fun `normalize keeps CLI order, drops unknown types and ties suspect deps to SCA`() {
        assertEquals(listOf("deps", "secrets", "suspectdeps", "ai"), ScanConfig.normalize(listOf("ai", "secrets", "bogus", "deps"), null, null).scans)
        assertEquals(listOf("sast"), ScanConfig.normalize(listOf("suspectdeps", "sast"), null, null).scans)
    }

    @Test
    fun `normalize limits on-save to the file-scoped types that are in the full scan`() {
        assertEquals(listOf("sast"), ScanConfig.normalize(listOf("sast", "deps"), listOf("sast", "deps", "secrets", "quality"), null).onSave)
        assertEquals(listOf("quality"), ScanConfig.normalize(listOf("quality"), listOf("quality"), null).onSave)
    }

    @Test
    fun `an empty full scan falls back to the defaults and excludes are trimmed and deduplicated`() {
        val config = ScanConfig.normalize(emptyList(), null, listOf(" vendor/** ", "vendor/**", ""))
        assertEquals(ScanConfig.DEFAULT_FULL_SCAN_TYPES, config.scans)
        assertEquals(listOf("vendor/**"), config.excludes)
    }

    @Test
    fun `toggles keep the configuration consistent`() {
        var config = ScanConfig.defaults().withScanType("sast", false)
        assertFalse("sast" in config.scans)
        assertFalse("on-save follows the full scan", "sast" in config.onSave)
        config = config.withScanType("sast", true)
        assertTrue("sast" in config.scans)
        assertFalse("re-enabling the full scan does not re-enable on-save on its own", "sast" in config.onSave)
        config = config.withOnSaveType("sast", true)
        assertTrue("sast" in config.onSave)
        assertEquals("SCA cannot run on save", config.onSave, config.withOnSaveType("deps", true).onSave)
        assertEquals(ScanConfig.DEFAULT_FULL_SCAN_TYPES, config.withAllScanTypes(true).scans)
        assertEquals("clearing never leaves the scanner without types", ScanConfig.DEFAULT_FULL_SCAN_TYPES, config.withAllScanTypes(false).scans)
    }

    @Test
    fun `excludes - folder patterns, add and remove, and the --exclude-add value`() {
        val root = java.io.File(System.getProperty("java.io.tmpdir"), "xygeni-project")
        assertEquals("vendor/**", ScanConfig.excludePatternForFolder(root, java.io.File(root, "vendor")))
        assertEquals("src/legacy/**", ScanConfig.excludePatternForFolder(root, java.io.File(root, "src/legacy")))
        assertEquals("the root itself cannot be excluded", null, ScanConfig.excludePatternForFolder(root, root))
        assertEquals("outside the project", null, ScanConfig.excludePatternForFolder(root, java.io.File(root.parentFile, "other")))

        var config = ScanConfig.defaults().withExclude("vendor/**").withExclude(" **/fixtures/** ").withExclude("vendor/**")
        assertEquals(listOf("vendor/**", "**/fixtures/**"), config.excludes)
        assertEquals("vendor/**,**/fixtures/**", config.excludeAddValue())
        config = config.withoutExclude("vendor/**")
        assertEquals(listOf("**/fixtures/**"), config.excludes)
        assertEquals("no excludes, no argument: the scanner defaults apply", null, config.withoutExclude("**/fixtures/**").excludeAddValue())
    }

    @Test
    fun `the file round-trips and is shared with the other plugins`() {
        val config = ScanConfig.defaults().withScanType("quality", false).copy(excludes = listOf("vendor/**"))
        val json = config.toJson()
        assertTrue(json.contains("\"scans\""))
        assertEquals(config, ScanConfig.parse(json))
        // What VS Code writes (key order and formatting may differ) is read the same way.
        assertEquals(config, ScanConfig.parse("""{"excludes":["vendor/**"],"onSave":["secrets","iac","sast","malware"],"scans":${config.scans.joinToString(",", "[", "]") { "\"$it\"" }}}"""))
    }

    @Test
    fun `keys this version does not know survive a save`() {
        val existing = """{"scans":["sast"],"future":{"x":1},"note":"from another plugin"}"""
        val json = ScanConfig.defaults().withScanType("quality", false).toJson(existing)
        assertTrue(json, json.contains("\"future\"") && json.contains("\"note\": \"from another plugin\""))
        assertFalse("the known keys are replaced", ScanConfig.parse(json).scans == listOf("sast"))
        assertEquals("not JSON: the known keys only", ScanConfig.defaults().toJson(), ScanConfig.defaults().toJson("not json"))
    }

    @Test
    fun `a symlinked xygeni folder or ide json is not written through`() {
        val root = java.nio.file.Files.createTempDirectory("xygeni-scan-config")
        val outside = java.nio.file.Files.createTempDirectory("xygeni-outside")
        try {
            val victim = outside.resolve("victim.txt").also { java.nio.file.Files.writeString(it, "keep me") }
            val dir = java.nio.file.Files.createDirectory(root.resolve(ScanConfig.CONFIG_DIR))
            val file = java.nio.file.Files.createSymbolicLink(dir.resolve(ScanConfig.CONFIG_FILE), victim).toFile()
            val refused = runCatching { ScanConfig.checkNotLinked(file) }.exceptionOrNull()
            assertTrue(refused?.message.orEmpty(), refused is java.io.IOException && refused.message!!.contains("symbolic link"))

            java.nio.file.Files.delete(file.toPath())
            ScanConfig.checkNotLinked(file) // a plain path is fine
        } finally {
            root.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }
}
