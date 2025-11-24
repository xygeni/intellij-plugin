package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.render.XygeniConstants.EXPLANATION_KEY
import com.github.xygeni.intellij.render.XygeniConstants.FOUND_BY_KEY
import com.github.xygeni.intellij.render.XygeniConstants.LOCATION_KEY
import com.github.xygeni.intellij.model.report.iac.IacXygeniIssue
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * IadIssueRenderer
 *
 * @author : Carmendelope
 * @version : 22/10/25 (Carmendelope)
 **/
class IacIssueRenderer : BaseHtmlIssueRenderer<IacXygeniIssue>() {

    override fun renderCustomIssueDetails(issue: IacXygeniIssue): String {
        return createHTML().div{
            table {
                tbody {
                    unsafe { +renderDetailTableLine(EXPLANATION_KEY, issue.explanation) }
                    unsafe { +renderDetailTableLine("Type", issue.type) }
                    unsafe { +renderDetailTableLine("Provider", issue.provider) }
                    unsafe { +renderDetailTableLine("Framework", issue.framework) }
                    unsafe { +renderDetailBranch(issue.where) }
                    unsafe { +renderDetailTableLine(LOCATION_KEY, issue.file) }
                    unsafe { +renderDetailTableLine("Resource", issue.resource) }
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