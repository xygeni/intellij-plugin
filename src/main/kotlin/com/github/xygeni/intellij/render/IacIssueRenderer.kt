package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.iac.IacXygeniIssue
import com.github.xygeni.intellij.model.report.sast.SastXygeniIssue
import com.github.xygeni.intellij.model.report.secret.SecretsXygeniIssue
import icons.Icons
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.unsafe
import kotlin.text.isEmpty
import kotlin.text.lines

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
    override fun renderCustomIssueDetails(issue: IacXygeniIssue): String {
        val svgContent = Icons::class.java.getResource("/icons/branch.svg")
            ?.readText()
        return createHTML().div{
            id = "tab-content-1"
            table {
                tbody {
                    tr { th { +"Explanation" }; td { +issue.explanation } }
                    tr { th { +"Type" }; td { +issue.type } }
                    tr { th { +"Provider" }; td { +issue.provider } }
                    tr { th { +"Framework" }; td { +issue.framework } }
                    tr { th { +"Where" }; td {
                        unsafe{ +svgContent.orEmpty() }
                        +issue.where } }
                    tr { th { +"Location" }; td { +issue.file  } }
                    tr { th { +"Resource" }; td { +issue.resource } }
                    tr { th { +"Found by" }; td { +issue.detector } }
                    tr { th { +"Tags" }; td {  unsafe { +renderTags(issue.tags) } } }
                }
            }
        }
    }


    // override fun renderCustomCodeSnippet(issue: IacXygeniIssue): String {
    fun renderCustomCodeSnippet2(issue: IacXygeniIssue): String {
        if (issue.code.isEmpty()) return ""
        return createHTML().div {
            id = "tab-content-2"
            p (classes ="file" ){
                text(issue.file)
            }
            table {
                tbody {
                    issue.code.lines().mapIndexed { index, line ->
                        tr {
                            val pos = issue.beginLine + index
                            td(classes = "line-number"){+"$pos"}
                            td(classes= "code-line"){unsafe{+line}}
                        }
                    }
                }
            }
        }
    }
}