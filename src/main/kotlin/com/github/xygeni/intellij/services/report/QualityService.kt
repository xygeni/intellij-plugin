package com.github.xygeni.intellij.services.report

import com.github.xygeni.intellij.model.report.quality.QualityXygeniIssue
import com.github.xygeni.intellij.model.report.quality.parseQualityReport
import com.github.xygeni.intellij.model.report.quality.toIssue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * QualityService — reads `quality.<suffix>.json` into [QualityXygeniIssue]s.
 * Mirrors {@link SastService}.
 **/
@Service(Service.Level.PROJECT)
class QualityService(project: Project) : BaseReportService<QualityXygeniIssue>(
    project,
    "quality") {

    override fun processReport(jsonString: String): List<QualityXygeniIssue> {
        val report = parseQualityReport(jsonString)
        val toolName = report.metadata.reportProperties["tool.name"]
        val branch = report.currentBranch
        return report.vulnerabilities.map { raw ->
            raw.toIssue(toolName, branch)
        }
    }
}
