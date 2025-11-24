package com.github.xygeni.intellij.services.report

import com.github.xygeni.intellij.model.JsonConfig
import com.github.xygeni.intellij.model.report.iac.IacReport
import com.github.xygeni.intellij.model.report.iac.IacXygeniIssue
import com.github.xygeni.intellij.model.report.iac.toIssue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * IacService
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/

@Service(Service.Level.PROJECT)
class IacService(project: Project) : BaseReportService<IacXygeniIssue>(
    project,
    "iac"
) {

    override fun processReport(jsonString: String): List<IacXygeniIssue> {
        val report = JsonConfig.relaxed.decodeFromString<IacReport>(jsonString)
        val toolName = report.metadata.reportProperties["tool.name"]
        val branch = report.currentBranch

        val parsedIssues = report.flaws.map { raw ->
            raw.toIssue(toolName, branch)
        }
        return parsedIssues
    }
}