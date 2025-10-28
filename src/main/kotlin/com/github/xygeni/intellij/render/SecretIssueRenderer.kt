package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.cicd.CicdXygeniIssue
import com.github.xygeni.intellij.model.report.secret.SecretsXygeniIssue
import icons.Icons
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.unsafe

/**
 * SecertIssueRenderer
 *
 * @author : Carmendelope
 * @version : 22/10/25 (Carmendelope)
 **/
class SecretIssueRenderer : BaseHtmlIssueRenderer<SecretsXygeniIssue>() {
    override fun renderCustomHeader(issue: SecretsXygeniIssue): String {
        return createHTML().p {
            unsafe { +"${issue.categoryName}&nbsp;&nbsp;&nbspFamily:&nbsp;&nbsp;${issue.type}&nbsp;&nbsp;&nbsp;" }
        }
    }

    override fun renderCustomIssueDetails(issue: SecretsXygeniIssue): String {
        return createHTML().div {
            id = "tab-content-1"
            table {
                tbody {
                    unsafe { +renderDetailTableLine("Type", issue.type) }
                    unsafe { +renderDetailTableLine("Secret", issue.secret) }
                    unsafe { +renderDetailTableLine("Found by", issue.detector) }
                    unsafe { +renderDetailBranch(issue.branch) }
                    unsafe { +renderDetailTableLine("Resource", issue.resource) }
                    unsafe { +renderDetailTags(issue.tags) }
                }
            }
            unsafe {
                +renderDetectorInfo(issue)
            }
        }
    }

}