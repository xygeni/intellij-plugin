package com.github.xygeni.intellij.model.report.apisec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture-backed contract test for [parseApisecReport] / [RawApiFlaw.toIssue].
 *
 * Pins the two things that make this report different from the SAST family: the findings
 * live under `flaws` (not `vulnerabilities`), and a flaw carries no location when it is
 * module- or service-scoped. Fixture: a REAL apisec report,
 * `src/test/resources/apisec.report.json`.
 */
class ApisecReportParseTest {

    private fun fixture(): String =
        javaClass.getResource("/apisec.report.json")!!.readText()

    @Test
    fun parsesRealApisecReportUnderFlawsKey() {
        val report = parseApisecReport(fixture())

        // Must resolve from `flaws` (7 items). Reading `vulnerabilities` would give zero.
        assertEquals(7, report.flaws.size)

        val first = report.flaws[0]
        assertEquals("API-excessive_data_exposure_python-POST /users/v1/login", first.issueId)
        assertEquals("excessive_data_exposure_python", first.detector)
        assertEquals("excessive_data_exposure", first.flawType)
        assertEquals("high", first.severity)
        assertEquals("POST", first.endpointMethod)
        assertEquals("/users/v1/login", first.endpointPath)
        assertEquals(listOf("API3:2023"), first.owaspApiTop10)
        assertEquals(listOf("CWE-213", "CWE-200"), first.cwes)
        assertEquals("origin/master", report.currentBranch)

        val issue = first.toIssue("Xygeni", report.currentBranch)
        assertEquals("apisec", issue.category)
        assertEquals("api_flaw", issue.kind)
        // The tree label is the machine `flawType`; the human `title` is kept for the details panel.
        assertEquals("excessive_data_exposure", issue.type)
        assertTrue(
            "title must be mapped for the details panel",
            issue.title.startsWith("Response DTO returns sensitive fields"),
        )
        assertEquals("POST /users/v1/login", issue.endpoint)
        assertNotEquals("explanation must not default to empty", "", issue.explanation)
        assertNotEquals("remediation must be mapped", "", issue.remediation)
        // No `util rectify --apisec`: the Fix action must stay hidden.
        assertEquals("NONE", issue.remediableLevel)
    }

    @Test
    fun toleratesFlawsWithoutLocation() {
        val report = parseApisecReport(fixture())
        // Without the inventory only `properties.handler_file` can resolve: endpoint-scoped flaws keep an
        // empty file and line 0, and nothing crashes or gets dropped.
        val issues = report.flaws.map { it.toIssue("Xygeni", report.currentBranch) }
        val endpointScoped = issues.filter { it.endpointPath.isNotEmpty() }
        assertTrue("no inventory -> endpoint flaws keep an empty file", endpointScoped.all { it.file.isEmpty() })
        assertTrue("no location -> line 0", issues.all { it.beginLine == 0 })
        // A module-scoped flaw (no endpoint) must still be listed.
        assertTrue(
            "module-/service-scoped flaws must not be dropped",
            issues.any { it.endpointPath.isEmpty() },
        )
    }

    @Test
    fun resolvesLocationsFromTheApiInventory() {
        val report = parseApisecReport(fixture())
        val issues = report.flaws.map { it.toIssue("Xygeni", report.currentBranch, report.locations) }
        // 6 of the 7 real flaws name an endpoint whose handler the inventory locates.
        val endpointScoped = issues.filter { it.endpointPath.isNotEmpty() }
        assertEquals(6, endpointScoped.size)
        assertTrue("endpoint flaws get the handler file", endpointScoped.all { it.file.isNotEmpty() && it.beginLine > 0 })
        val login = issues.single { it.endpoint == "POST /users/v1/login" }
        assertEquals("api_views/users.py", login.file)
        assertEquals(85, login.beginLine)
        // The module-scoped flaw falls back to `properties.handler_file` (no line).
        val moduleScoped = issues.single { it.endpointPath.isEmpty() }
        assertEquals("api_views/users.py", moduleScoped.file)
        assertEquals(0, moduleScoped.beginLine)
    }

    @Test
    fun unknownFindingsKeyYieldsEmptyWithoutThrowing() {
        // The API inventory alone (services / dataObjects) is not a findings stream.
        val json = """{"metadata":{},"services":[{"name":"x"}]}"""
        val report = parseApisecReport(json)
        assertTrue(report.flaws.isEmpty())
    }
}
