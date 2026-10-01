package com.github.xygeni.intellij.model.report.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture-backed contract test for [parseAiReport] / [RawAiVulnerability.toIssue].
 *
 * Pins the field names, which are NOT the SAST ones: `severityFloor`, `id`, `detectorId`
 * and `description`. Reading `severity` / `issueId` / `detector` / `explanation` would
 * silently yield empty values, leaving the AI view populated but blank. Fixture: a REAL
 * report produced by the scanner, `src/test/resources/ai.report.json`.
 */
class AiReportParseTest {

    private fun fixture(): String =
        javaClass.getResource("/ai.report.json")!!.readText()

    @Test
    fun parsesRealAiReportWithItsOwnFieldNames() {
        val report = parseAiReport(fixture())

        assertEquals(9, report.vulnerabilities.size)

        val first = report.vulnerabilities[0]
        assertEquals("prompt-pinned-to-mutable-label:ai/prompt_registry.py:31", first.id)
        assertEquals("prompt-pinned-to-mutable-label", first.detectorId)
        // The severity travels as `severityFloor` — this is the assertion that catches a
        // copy of the SAST mapper.
        assertEquals("high", first.severityFloor)
        assertEquals("medium", first.confidence)
        assertEquals("ai_prompt", first.assetKind)
        assertEquals("ai/prompt_registry.py", first.location?.filepath)
        assertEquals(31, first.location?.beginLine)

        val issue = first.toIssue("Xygeni", report.currentBranch)
        assertEquals("ai", issue.category)
        assertEquals("ia_vulnerability", issue.kind)
        assertEquals("high", issue.severity)
        // No kind/type in this report: the detector id is the label.
        assertEquals("prompt-pinned-to-mutable-label", issue.type)
        assertEquals("prompt-pinned-to-mutable-label", issue.detector)
        assertNotEquals("explanation must come from `description`", "", issue.explanation)
        // `standards` is a list of {std, version, controlId}: the control id is what we show.
        assertTrue("standards must resolve to control ids", issue.standards.contains("LLM01"))
        assertTrue(issue.redTeamVectors.contains("PromptInjection"))
        assertNotEquals("remediationHint must be mapped", "", issue.remediationHint)
        // `util rectify --ai` exists (RectifyCommand.java): the Fix action must render and map to `--ai`.
        assertEquals("AUTO", issue.remediableLevel)
        assertEquals("ai", issue.toRemediationData().kind)
        assertEquals(issue.detector, issue.toRemediationData().detector)
    }

    @Test
    fun unknownFindingsKeyYieldsEmptyWithoutThrowing() {
        val json = """{"metadata":{},"somethingElse":[{"id":"x"}]}"""
        val report = parseAiReport(json)
        assertTrue(report.vulnerabilities.isEmpty())
    }
}
