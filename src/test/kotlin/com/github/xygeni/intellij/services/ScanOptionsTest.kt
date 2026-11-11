package com.github.xygeni.intellij.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scanner options the user may add from the IDE (xygeni/xygeni-product-backlog#415). */
class ScanOptionsTest {

    private fun ok(text: String): String {
        val result = ScanOptions.validate(text)
        assertTrue("$text: ${(result as? ScanOptions.Validation.Error)?.message}", result is ScanOptions.Validation.Ok)
        return (result as ScanOptions.Validation.Ok).option
    }

    private fun bad(text: String, part: String) {
        val result = ScanOptions.validate(text)
        assertTrue("$text should be refused", result is ScanOptions.Validation.Error)
        val message = (result as ScanOptions.Validation.Error).message
        assertTrue("$text: $message", message.contains(part))
    }

    @Test
    fun `only options of xygeni scan are offered, the language options belong to sast and quality`() {
        // ScanCommand in DepsDoctor-CLI has no LanguageOptions group: `scan --run-languages=java` answers "Unknown option", exit 2.
        for (name in listOf("--run-languages", "--skip-languages", "--languages")) {
            assertTrue("$name is not a xygeni scan option", ScanOptions.SPECS.none { it.name == name })
        }
    }

    @Test
    fun `the offered options and the managed ones never overlap`() {
        for (spec in ScanOptions.SPECS) {
            assertNull("${spec.name} is both offered and managed", ScanOptions.MANAGED[spec.name])
            assertTrue(spec.name.startsWith("--"))
            if (spec.kind == ScanOptions.Kind.ENUM) assertTrue(spec.values.size > 1)
            if (spec.closed) assertTrue(spec.values.isNotEmpty())
        }
    }

    @Test
    fun `parse - name, name=value and name value`() {
        assertNull(ScanOptions.parse("--ai-review")!!.value)
        assertEquals("all", ScanOptions.parse("--ai-review=all")!!.value)
        assertEquals("all", ScanOptions.parse("  --ai-review  all ")!!.value)
        assertEquals("src, lib", ScanOptions.parse("--include = src, lib")!!.value)
        assertNull(ScanOptions.parse("java"))
        assertNull(ScanOptions.parse(""))
        assertEquals("--mute", ScanOptions.nameOf("--mute=.xygeni.mute.yml"))
    }

    @Test
    fun `validate - canonical form for flags, enums, lists, text and paths`() {
        assertEquals("--include-license-analysis", ok("--include-license-analysis"))
        bad("--include-license-analysis=yes", "takes no value")
        assertEquals("--ai-review=all", ok("--ai-review all"))
        bad("--ai-review=sometimes", "is one of off, all, ai-detected")
        bad("--ai-review", "needs a value")
        assertEquals("--include=src/**,lib/**", ok("--include = src/**, lib/**"))
        bad("--include=src,,lib", "none empty")
        bad("--ai-mode=sometimes", "is one of off, auto, stored, rebuild")
        assertEquals("an open list accepts other items", "--ai-review-filter=high,!sql_injection", ok("--ai-review-filter=high,!sql_injection"))
        assertEquals("the first = splits name and value", "--detectors=secrets=high|iac=all", ok("--detectors=secrets=high|iac=all"))
        bad("--standard=owasp top10", "no spaces")
        assertEquals("a path may have spaces", "--mute=mutes/my profile.yml", ok("--mute=mutes/my profile.yml"))
    }

    @Test
    fun `validate - what the plugin manages and what the scanner does not have are refused with a reason`() {
        for ((name, reason) in ScanOptions.MANAGED) bad("$name=x", reason)
        bad("--turbo", "--turbo")
        bad("java", "--")
    }

    @Test
    fun `normalize keeps the valid entries in canonical form, one per option, the last one winning`() {
        assertEquals(emptyList<String>(), ScanOptions.normalize(null))
        assertEquals(
            listOf("--ai-review=off", "--include-license-analysis"),
            ScanOptions.normalize(listOf("--ai-review=all", "--run=secrets", "-o out.json", "--ai-review off", "--include-license-analysis", "--nope"))
        )
    }

    @Test
    fun `suggestions - option names first, then the values of an enum or a closed list`() {
        assertEquals(ScanOptions.SPECS.size, ScanOptions.suggestions("--").size)
        assertEquals(listOf("--ai-review=", "--ai-review-filter="), ScanOptions.suggestions("--ai-r"))
        assertEquals(listOf("--include=", "--include-license-analysis"), ScanOptions.suggestions("--inc"))
        assertEquals(listOf("--ai-review=off", "--ai-review=all", "--ai-review=ai-detected"), ScanOptions.suggestions("--ai-review="))
        assertEquals(listOf("--ai-review=all", "--ai-review=ai-detected"), ScanOptions.suggestions("--ai-review=a"))
        assertEquals(listOf("--ai-review-filter=high,low"), ScanOptions.suggestions("--ai-review-filter=high,lo"))
        assertFalse("already chosen items are not offered again", ScanOptions.suggestions("--ai-review-filter=high,").contains("--ai-review-filter=high,high"))
        assertEquals(emptyList<String>(), ScanOptions.suggestions("--mute="))
        assertEquals(emptyList<String>(), ScanOptions.suggestions("--turbo="))
    }

    @Test
    fun `scan configuration - options are part of ide json, validated on read, and the defaults have none`() {
        val parsed = ScanConfig.parse("""{"options": ["--ai-review=all", "--run=secrets", "--dir=/tmp"]}""")
        assertEquals("a hand-edited file cannot bring managed options in", listOf("--ai-review=all"), parsed.options)
        assertFalse(parsed.isDefault)
        assertTrue(ScanConfig.normalize(listOf("sast"), null, null).options.isEmpty())

        var config = ScanConfig.defaults().withScanOption("--ai-review=all").withScanOption("--ai-review=off").withScanOption("--include=src")
        assertEquals("in the order of the option list, replaced by name", listOf("--include=src", "--ai-review=off"), config.options)
        config = config.withoutScanOption("--ai-review")
        assertEquals(listOf("--include=src"), config.options)
        assertTrue(config.toJson().contains("\"--include=src\""))
        assertEquals(config, ScanConfig.parse(config.toJson()))
        assertTrue(config.withoutScanOption("--include").isDefault)
    }

    @Test
    fun `a value from ide json cannot break out of its argument or reach outside the project`() {
        // On Windows xygeni.ps1 forwards its arguments to java without escaping an embedded quote.
        assertFalse(ScanOptions.validate("--mute=x\" \"--set-baseline") is ScanOptions.Validation.Ok)
        assertFalse(ScanOptions.validate("--standard=a\u0001b") is ScanOptions.Validation.Ok)
        assertEquals(listOf("vendor/**"), ScanConfig.normalize(null, null, listOf("vendor/**", "x\" --upload \"y")).excludes)
        for (path in listOf("/etc/detectors", "\\\\server\\share", "C:\\conf", "~/detectors", "../other", "conf/../../x")) {
            assertFalse(path, ScanOptions.isPathInsideProject(path))
            assertFalse(path, ScanOptions.validate("--custom-detectors-dir=$path") is ScanOptions.Validation.Ok)
        }
        assertTrue(ScanOptions.validate("--conf=conf/custom.yml") is ScanOptions.Validation.Ok)
        assertTrue("a named mute profile", ScanOptions.validate("--mute=#team-profile") is ScanOptions.Validation.Ok)
    }
}
