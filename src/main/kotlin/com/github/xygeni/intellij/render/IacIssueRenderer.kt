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
                +"${issue.category}&nbsp;&nbsp;&nbsp;${issue.type}"
            }
        }
    }
    override fun renderCustomIssueDetails(issue: IacXygeniIssue): String {
        return createHTML().div{
            id = "tab-content-1"
            table {
                tbody {
                    unsafe { +renderDetailTableLine("Explanation", issue.explanation) }
                    unsafe { +renderDetailTableLine("Type", issue.type) }
                    unsafe { +renderDetailTableLine("Provider", issue.provider) }
                    unsafe { +renderDetailTableLine("Framework", issue.framework) }
                    unsafe { +renderDetailBranch(issue.where) }
                    unsafe { +renderDetailTableLine("Location", issue.file) }
                    unsafe { +renderDetailTableLine("Resource", issue.resource) }
                    unsafe { +renderDetailTableLine("Found by", issue.detector) }
                    unsafe { +renderDetailTags(issue.tags) }
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