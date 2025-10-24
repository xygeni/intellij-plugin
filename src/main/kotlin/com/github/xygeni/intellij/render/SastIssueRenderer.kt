package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.sast.SastXygeniIssue
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * SastIssueRenderer
 *
 * @author : Carmendelope
 * @version : 22/10/25 (Carmendelope)
 **/
class SastIssueRenderer : BaseHtmlIssueRenderer<SastXygeniIssue>() {
    override fun renderCustomHeader(issue: SastXygeniIssue): String {
        return createHTML().p {
            unsafe {
                +"${issue.categoryName}&nbsp;&nbsp;&nbsp;${issue.type}&nbsp;&nbsp;&nbsp;"
            }
            issue.cwes?.forEach { cve ->
                val value = cve.substringAfterLast("-")
                unsafe {
                    +renderLink("https://cwe.mitre.org/data/definitions/$value.html", cve)
                    "&nbsp;&nbsp;&nbsp;"
                }
            }
        }
    }

    override fun renderCustomIssueDetails(issue: SastXygeniIssue): String {
        val tags = renderTags(issue.tags)
        return createHTML().div {
            id = "tab-content-1"
            table {
                tbody {
                    unsafe { +renderDetailTableLine("Explanation", issue.explanation) }
                    unsafe { +renderDetailTableLine("Type", issue.kind) }
                    unsafe { +renderDetailBranch(issue.branch) }
                    unsafe { +renderDetailTableLine("Language", issue.language) }
                    unsafe { +renderDetailTableLine("Location", issue.file) }
                    unsafe { +renderDetailTableLine("Found by", issue.detector) }
                    unsafe { +renderDetailTags(issue.tags) }
                }
            }

        }
    }

}