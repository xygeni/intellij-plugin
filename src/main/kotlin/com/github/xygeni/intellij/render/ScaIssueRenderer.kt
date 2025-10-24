package com.github.xygeni.intellij.render

import com.github.markusbernhardt.proxy.util.Logger
import com.github.xygeni.intellij.model.report.sast.SastXygeniIssue
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
            unsafe { +renderLink(issue.url, issue.id) }
            unsafe { +"&nbsp;&nbsp;&nbsp;" }
            issue.weakness?.forEach { cve ->
                val value = cve.substringAfterLast("-")
                unsafe { +renderLink("https://cwe.mitre.org/data/definitions/$value.html", cve)}
                unsafe { +"&nbsp;&nbsp;&nbsp;" }
            }
            if (issue.baseScore != null && issue.baseScore > 0) {
                span(classes = "xy-slide-${issue.severity}") { +issue.baseScore.toString() }
            }
        }
    }

    override fun renderCustomIssueDetails(issue: ScaXygeniIssue): String {
        return createHTML().div {
            id = "tab-content-1"
            table {
                tbody {
                    tr { th { +"Published" }; td { +issue.publicationDate } }
                    tr { th { +"Affecting" }; td { +issue.file } }
                    tr { th { +"Versions" }; td { +issue.versions } }
                    tr { th { +"File" }; td { +( issue.dependencyPaths?.firstOrNull() ?: "") } }
                    tr { th { +"Direct dependency" }; td { +(if (issue.directDependency) "true" else "false") } }
                    tr { th { +"Vector" }; td { +issue.vector } }
                    tr { th { +"Tags" }; td {  unsafe { +renderTags(issue.tags) } } }
                }
            }
            div{
                p{ text("References:")}
                issue.references?.forEach {ref ->
                    val spl = splitReference(ref)
                    if (spl != null ) {
                        val (name, link) = spl
                        p{ unsafe { +renderLink( link, name.uppercase()  ) }}
                    }
                }
            }

        }
    }

    // splitReference splits a vuln reference [Name]Link into a Name-Link pair
    private fun splitReference(reference : String?): Pair<String, String>? {
        if (reference.isNullOrBlank()) return null
        val regex = """\[(.+?)\]\((.+)\)""".toRegex()
        val match = regex.matchEntire(reference)
        return if (match != null) {
            val name = match.groupValues[1]
            val link = match.groupValues[2]
            Pair(name, link)
        } else {
            null
        }
    }
}