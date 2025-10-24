package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.sast.SastXygeniIssue
import icons.Icons
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
                +"SAST&nbsp;&nbsp;&nbsp;${issue.type}"
            }
            unsafe { +"&nbsp;&nbsp;&nbsp;" }

            issue.cwes?.forEach { cve ->
                val value = cve.substringAfterLast("-")
                unsafe { +renderLink("https://cwe.mitre.org/data/definitions/$value.html", cve)
                    "&nbsp;&nbsp;&nbsp;" }
            }
        }
    }

    override fun renderCustomIssueDetails(issue: SastXygeniIssue): String {
        val svgContent = Icons::class.java.getResource("/icons/branch.svg")
            ?.readText()

        val tags = renderTags(issue.tags)
        return createHTML().div {
            id = "tab-content-1"
            table {
                tbody {
                    tr { th { +"Explanation" }; td { +issue.explanation } }
                    tr { th { +"Type" }; td { +issue.kind } }
                    tr { th { +"Where" }; td {
                        unsafe{ +svgContent.orEmpty() }
                        +issue.branch } }
                    tr { th { +"Language" }; td { +issue.language } }
                    tr { th { +"Location" }; td { +issue.file } }
                    tr { th { +"Found by" }; td { +(issue.detector ?: "") } }
                    tr { th { +"Tags" }; td {  unsafe { +tags } } }
                }
            }

        }
    }

}