package com.github.xygeni.intellij.services.report

import com.github.xygeni.intellij.model.report.apisec.ApisecXygeniIssue
import com.github.xygeni.intellij.model.report.apisec.parseApisecReport
import com.github.xygeni.intellij.model.report.apisec.toIssue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * ApisecService — reads `apisec.<suffix>.json` into [ApisecXygeniIssue]s.
 **/
@Service(Service.Level.PROJECT)
class ApisecService(project: Project) : BaseReportService<ApisecXygeniIssue>(
    project,
    "apisec") {

    override fun processReport(jsonString: String): List<ApisecXygeniIssue> {
        val report = parseApisecReport(jsonString)
        val toolName = report.metadata.reportProperties["tool.name"]
        val branch = report.currentBranch
        return report.flaws.map { raw ->
            raw.toIssue(toolName, branch, report.locations)
        }
    }
}
