package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.sast.SastXygeniIssue
import kotlinx.html.a
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.title
import kotlinx.html.unsafe

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
                +"SAST&nbsp;&nbsp;&nbsp;${issue.type}"
            }
            unsafe { +"&nbsp;&nbsp;&nbsp;" }

            issue.cwes?.forEach { cve ->
                val value = cve.substringAfterLast("-")
                a (href = "https://cwe.mitre.org/data/definitions/$value.html", target = "_blank") {
                    text(cve)
                }
                unsafe { +"&nbsp;&nbsp;&nbsp;" }
            }
        }
    }
}