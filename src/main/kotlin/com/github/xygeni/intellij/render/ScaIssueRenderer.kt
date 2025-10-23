package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.sca.ScaXygeniIssue
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * CsagIssueRenderer
 *
 * @author : Carmendelope
 * @version : 22/10/25 (Carmendelope)
 **/
class ScaIssueRenderer: BaseHtmlIssueRenderer<ScaXygeniIssue>() {
    override fun renderCustomHeader(issue: ScaXygeniIssue): String {
        return createHTML().p {
            unsafe {
                +"Vulnerability&nbsp;&nbsp;&nbsp;"
            }
            a(href = "${issue.url}", target = "_blank") {
                title="${issue.id}"
                text(issue.id)
            }
            unsafe { +"&nbsp;&nbsp;&nbsp;" }

            issue.weakness?.forEach { cve ->
                val value = cve.substringAfterLast("-")
                a (href = "https://cwe.mitre.org/data/definitions/$value.html", target = "_blank") {
                    text(cve)
                }
                unsafe { +"&nbsp;&nbsp;&nbsp;" }
            }
            if (issue.baseScore != null && issue.baseScore > 0) {
                span(classes = "xy-slide-${issue.severity}") { +issue.baseScore.toString() }
            }
        }
    }
}