package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.iac.IacXygeniIssue
import kotlinx.html.a
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.title
import kotlinx.html.unsafe

/**
 * IadIssueRenderer
 *
 * @author : Carmendelope
 * @version : 22/10/25 (Carmendelope)
 **/
class IacIssueRenderer : BaseHtmlIssueRenderer<IacXygeniIssue>() {
    override fun renderCustomHeader(issue: IacXygeniIssue): String {
        return createHTML().p {
            unsafe {
                +"IaC&nbsp;&nbsp;&nbsp;${issue.type}"
            }
        }
    }
}