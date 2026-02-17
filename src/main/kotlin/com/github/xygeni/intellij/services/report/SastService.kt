package com.github.xygeni.intellij.services.report

import com.github.xygeni.intellij.model.report.sast.SastXygeniIssue
import com.github.xygeni.intellij.model.report.sast.parseSastReport
import com.github.xygeni.intellij.model.report.sast.toIssue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * SastService2
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/
@Service(Service.Level.PROJECT)
class SastService(project: Project) : BaseReportService<SastXygeniIssue>(
    project,
    "sast") {

    override fun processReport(jsonString : String): List<SastXygeniIssue> {
        // val report = JsonConfig.relaxed.decodeFromString<SastReport>(jsonString)
        val report = parseSastReport(jsonString)
        val toolName = report.metadata.reportProperties["tool.name"]
        val branch = report.currentBranch
        val parsedIssues = report.vulnerabilities.map { raw ->
            raw.toIssue(toolName, branch)
        }
        return parsedIssues
    }
}