package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.secret.SecretsXygeniIssue
import com.github.xygeni.intellij.render.XygeniConstants.FOUND_BY_KEY
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.unsafe

/**
 * SecertIssueRenderer
 *
 * @author : Carmendelope
 * @version : 22/10/25 (Carmendelope)
 **/
class SecretIssueRenderer : BaseHtmlIssueRenderer<SecretsXygeniIssue>() {

    override fun renderCustomIssueDetails(issue: SecretsXygeniIssue): String {
        return createHTML().div {
            table {
                tbody {
                    unsafe { +renderDetailTableLine("Type", issue.type) }
                    unsafe { +renderDetailTableLine("Secret", issue.secret) }
                    unsafe { +renderDetailTableLine(FOUND_BY_KEY, issue.detector) }
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