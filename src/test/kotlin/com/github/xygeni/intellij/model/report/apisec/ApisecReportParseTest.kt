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
        // `title` (the human label) wins over the machine `flawType`.
        assertTrue(
            "type must map to the flaw title",
            issue.type.startsWith("Response DTO returns sensitive fields"),
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
        val issues = report.flaws.map { it.toIssue("Xygeni", report.currentBranch) }

        // Every flaw in the real report is endpoint-/module-/service-scoped with NO location:
        // that must yield well-formed issues with an empty file, never a crash or a drop.
        assertTrue("no location -> empty file", issues.all { it.file.isEmpty() })
        assertTrue("no location -> line 0", issues.all { it.beginLine == 0 })
        // A module-scoped flaw (no endpoint) must still be listed.
        assertTrue(
            "module-/service-scoped flaws must not be dropped",
            issues.any { it.endpointPath.isEmpty() },
        )
    }

    @Test
    fun unknownFindingsKeyYieldsEmptyWithoutThrowing() {
        // The API inventory alone (services / dataObjects) is not a findings stream.
        val json = """{"metadata":{},"services":[{"name":"x"}]}"""
        val report = parseApisecReport(json)
        assertTrue(report.flaws.isEmpty())
    }
}
