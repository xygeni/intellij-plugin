package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.cicd.CicdXygeniIssue
import com.github.xygeni.intellij.model.report.sca.ScaXygeniIssue
import icons.Icons
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
                +"Misconfiguration&nbsp;&nbsp;&nbsp;${issue.type}"
            }
        }
    }

    override fun renderCustomIssueDetails(issue: CicdXygeniIssue): String {
        val svgContent = Icons::class.java.getResource("/icons/branch.svg")
            ?.readText()

        return createHTML().div {
            id = "tab-content-1"
            table {
                tbody {
                    tr { th { +"Explanation" }; td { +issue.explanation } }
                    tr { th { +"Where" }; td {
                        unsafe{ +svgContent.orEmpty() }
                        +issue.where } }
                    tr { th { +"Found by" }; td { +(issue.detector ?: "") } }
                    tr { th { +"Tags" }; td {  unsafe { +renderTags(issue.tags) } } }
                }
            }
        }
    }
}