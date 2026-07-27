package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.quality.QualityXygeniIssue
import com.github.xygeni.intellij.render.XygeniConstants.EXPLANATION_KEY
import com.github.xygeni.intellij.render.XygeniConstants.FOUND_BY_KEY
import com.github.xygeni.intellij.render.XygeniConstants.LOCATION_KEY
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * QualityIssueRenderer
 *
 * Detail renderer for Code Quality findings. Mirrors {@link SastIssueRenderer}
 * but adds the quality rule family ("Category") and omits CWEs / code-flow.
 **/
class QualityIssueRenderer : BaseHtmlIssueRenderer<QualityXygeniIssue>() {

    override fun renderCustomHeader(issue: QualityXygeniIssue): String {
        return createHTML().p {
            unsafe {
                +"${issue.categoryName}&nbsp;&nbsp;&nbsp;${issue.type}"
            }
        }
    }

    override fun renderCustomIssueDetails(issue: QualityXygeniIssue): String {
        return createHTML().div {
            table {
                tbody {
                    unsafe { +renderDetailTableLine(EXPLANATION_KEY, issue.explanation) }
                    unsafe { +renderDetailTableLine("Type", issue.type) }
                    unsafe { +renderDetailTableLine("Category", issue.qualityCategory) }
                    unsafe { +renderDetailTableLine("Language", issue.language) }
                    unsafe { +renderDetailBranch(issue.branch) }
                    unsafe { +renderDetailTableLine(LOCATION_KEY, issue.file) }
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
