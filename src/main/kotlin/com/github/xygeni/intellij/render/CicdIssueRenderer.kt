package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.cicd.CicdXygeniIssue
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.unsafe

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
                +"Misconfiguration&nbsp;&nbsp;&nbsp;${issue.type}"
            }
        }
    }
}