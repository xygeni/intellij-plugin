package com.github.xygeni.intellij.services.report

import com.github.xygeni.intellij.model.JsonConfig
import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.github.xygeni.intellij.model.report.sast.SastReport
import com.github.xygeni.intellij.model.report.sast.SastXygeniIssue
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
        val report = JsonConfig.relaxed.decodeFromString<SastReport>(jsonString)
        val toolName = report.metadata.reportProperties["tool.name"]

        val parsedIssues = report.vulnerabilities.map { raw ->
            raw.toIssue(toolName)
        }
        return parsedIssues
    }
}