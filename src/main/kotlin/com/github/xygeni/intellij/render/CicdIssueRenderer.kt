package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.cicd.CicdXygeniIssue
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * CicdIssueRenderer
 *
 * @author : Carmendelope
 * @version : 22/10/25 (Carmendelope)
 **/
class CicdIssueRenderer : BaseHtmlIssueRenderer<CicdXygeniIssue>() {
    override fun renderCustomHeader(issue: CicdXygeniIssue): String {
        return createHTML().p {
            unsafe {
                +"${issue.category}&nbsp;&nbsp;&nbsp;${issue.type}"
            }
        }
    }

    override fun renderCustomIssueDetails(issue: CicdXygeniIssue): String {
        return createHTML().div {
            id = "tab-content-1"
            table {
                tbody {
                    unsafe { +renderDetailTableLine("Explanation", issue.explanation) }
                    unsafe { +renderDetailBranch(issue.where) }
                    unsafe { +renderDetailTableLine("Found by", issue.detector) }
                    unsafe { +renderDetailTags(issue.tags) }
                }
            }
        }
    }
}