package com.github.xygeni.intellij.model.report.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture-backed contract test for [parseQualityReport] / [RawQuality.toIssue].
 *
 * Pins the scanner→IDE quality report shape (top-level `vulnerabilities` key +
 * field mapping) against a REAL `quality.*.json`, so a format shift can't leave
 * the Code Quality view silently empty. Fixture: `src/test/resources/quality.report.json`.
 */
class QualityReportParseTest {

    private fun fixture(): String =
        javaClass.getResource("/quality.report.json")!!.readText()

    @Test
    fun parsesRealQualityReportUnderVulnerabilitiesKey() {
        val report = parseQualityReport(fixture())

        // Must resolve from the confirmed `vulnerabilities` key (10 items), NOT the fallback.
        assertEquals(10, report.vulnerabilities.size)

        val first = report.vulnerabilities[0]
        assertEquals("SAS.reliability.javascript.strict_equals.quality/smells.js.5", first.issueId)
        assertEquals("reliability", first.kind)
        assertEquals("javascript.strict_equals", first.detector)
        assertEquals("high", first.severity)
        assertEquals("quality/smells.js", first.location?.filepath)
        assertEquals(5, first.location?.beginLine)
        assertEquals(5, first.location?.endLine)
        assertTrue(
            "explanation must be mapped from the real field",
            first.explanation?.startsWith("Loose equality") == true,
        )

        val issue = first.toIssue("Xygeni", report.currentBranch)
        assertEquals("quality", issue.category)
        assertEquals("quality_issue", issue.kind)
        assertEquals("reliability", issue.type)
        // qualityCategory must resolve from `kind` (real reports carry no `category`).
        assertEquals("reliability", issue.qualityCategory)
        assertEquals("javascript.strict_equals", issue.detector)
        assertNotEquals("file must not default to empty", "", issue.file)
    }

    @Test
    fun unknownFindingsKeyYieldsEmptyWithoutThrowing() {
        // A report whose findings key is none of the known ones must not throw and
        // must produce zero quality issues (silent-empty acceptable; a crash is not).
        val json = """{"metadata":{},"somethingElse":[{"issueId":"x"}]}"""
        val report = parseQualityReport(json)
        assertTrue(report.vulnerabilities.isEmpty())
    }
}
