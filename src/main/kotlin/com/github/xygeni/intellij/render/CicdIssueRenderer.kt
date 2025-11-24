package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.render.XygeniConstants.EXPLANATION_KEY
import com.github.xygeni.intellij.render.XygeniConstants.FOUND_BY_KEY
import com.github.xygeni.intellij.model.report.cicd.CicdXygeniIssue
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * CicdIssueRenderer
 *
 * @author : Carmendelope
 * @version : 22/10/25 (Carmendelope)
 **/
class CicdIssueRenderer : BaseHtmlIssueRenderer<CicdXygeniIssue>() {

    override fun renderCustomIssueDetails(issue: CicdXygeniIssue): String {
        return createHTML().div {
            //id = "tab-content-1"
            table {
                tbody {
                    unsafe { +renderDetailTableLine(EXPLANATION_KEY, issue.explanation) }
                    unsafe { +renderDetailBranch(issue.where) }
                    unsafe { +renderDetailTableLine(FOUND_BY_KEY, issue.detector) }
                    unsafe { +renderDetailTags(issue.tags) }
                }
            }
            unsafe {
                +renderDetectorInfo(issue)
            }
        }
    }
}