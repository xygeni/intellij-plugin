package com.github.xygeni.intellij.services.report

import com.github.xygeni.intellij.model.JsonConfig
import com.github.xygeni.intellij.model.report.sca.ScaReport
import com.github.xygeni.intellij.model.report.sca.ScaXygeniIssue
import com.github.xygeni.intellij.model.report.sca.toIssue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * SacService
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/

@Service(Service.Level.PROJECT)
class ScaService(project: Project) : BaseReportService<ScaXygeniIssue>(
    project,
    "deps"
) {
    override fun processReport(jsonString: String): List<ScaXygeniIssue> {
        val report = JsonConfig.relaxed.decodeFromString<ScaReport>(jsonString)
        val toolName = report.metadata.reportProperties["tool.name"]
        val branch = report.currentBranch
        val parsedIssues = report.dependencies.flatMap { raw ->
            raw.toIssue(toolName, branch)
        }
        return parsedIssues
    }
}