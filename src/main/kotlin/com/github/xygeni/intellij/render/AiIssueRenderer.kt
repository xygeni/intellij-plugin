package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.ai.AiXygeniIssue
import com.github.xygeni.intellij.render.XygeniConstants.EXPLANATION_KEY
import com.github.xygeni.intellij.render.XygeniConstants.FOUND_BY_KEY
import com.github.xygeni.intellij.render.XygeniConstants.LOCATION_KEY
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * AiIssueRenderer
 *
 * Detail renderer for AI Security findings: the AI asset, the taxonomy controls it maps to
 * and the red-team vectors that exploit it.
 **/
class AiIssueRenderer : BaseHtmlIssueRenderer<AiXygeniIssue>() {

    override fun renderCustomHeader(issue: AiXygeniIssue): String {
        return createHTML().p {
            unsafe {
                +"${issue.categoryName}&nbsp;&nbsp;&nbsp;${issue.type}"
            }
        }
    }

    override fun renderCustomIssueDetails(issue: AiXygeniIssue): String {
        return createHTML().div {
            table {
                tbody {
                    unsafe { +renderDetailTableLine(EXPLANATION_KEY, issue.explanation) }
                    unsafe { +renderDetailTableLine("Type", issue.type) }
                    unsafe { +renderDetailTableLine("AI Asset", issue.assetKind) }
                    unsafe { +renderDetailTableLine("Standards", issue.standards.joinToString(", ")) }
                    unsafe { +renderDetailTableLine("Red Team Vectors", issue.redTeamVectors.joinToString(", ")) }
                    unsafe { +renderDetailBranch(issue.branch) }
                    unsafe { +renderDetailTableLine(LOCATION_KEY, issue.file) }
                    unsafe { +renderDetailTableLine(FOUND_BY_KEY, issue.detector) }
                    unsafe { +renderDetailTableMarkdownLine("Remediation", issue.remediationHint) }
                    unsafe { +renderDetailTags(issue.tags) }
                }
            }
            unsafe {
                +renderDetectorInfo(issue)
            }
        }
    }

}
