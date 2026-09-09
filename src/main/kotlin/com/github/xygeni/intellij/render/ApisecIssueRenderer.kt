package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.apisec.ApisecXygeniIssue
import com.github.xygeni.intellij.render.XygeniConstants.EXPLANATION_KEY
import com.github.xygeni.intellij.render.XygeniConstants.FOUND_BY_KEY
import com.github.xygeni.intellij.render.XygeniConstants.LOCATION_KEY
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * ApisecIssueRenderer
 *
 * Detail renderer for API Security flaws: the affected endpoint / module / service, the
 * OWASP API Top 10 and CWE mappings, and the detector's remediation advice.
 **/
class ApisecIssueRenderer : BaseHtmlIssueRenderer<ApisecXygeniIssue>() {

    override fun renderCustomHeader(issue: ApisecXygeniIssue): String {
        return createHTML().p {
            unsafe {
                +"${issue.categoryName}&nbsp;&nbsp;&nbsp;${issue.type}"
            }
        }
    }

    override fun renderCustomIssueDetails(issue: ApisecXygeniIssue): String {
        return createHTML().div {
            table {
                tbody {
                    unsafe { +renderDetailTableLine(EXPLANATION_KEY, issue.explanation) }
                    unsafe { +renderDetailTableLine("Type", issue.type) }
                    unsafe { +renderDetailTableLine("Endpoint", issue.endpoint) }
                    unsafe { +renderDetailTableLine("Module", issue.moduleName) }
                    unsafe { +renderDetailTableLine("Service", issue.serviceName) }
                    unsafe { +renderDetailTableLine("OWASP API Top 10", issue.owaspApiTop10.joinToString(", ")) }
                    unsafe { +renderDetailTableLine("CWE", issue.cwes.joinToString(", ")) }
                    unsafe { +renderDetailBranch(issue.branch) }
                    unsafe { +renderDetailTableLine(LOCATION_KEY, issue.file) }
                    unsafe { +renderDetailTableLine(FOUND_BY_KEY, issue.detector) }
                    unsafe { +renderDetailTableLine("Remediation", issue.remediation) }
                    unsafe { +renderDetailTags(issue.tags) }
                }
            }
            unsafe {
                +renderDetectorInfo(issue)
            }
        }
    }

}
