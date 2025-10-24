package com.github.xygeni.intellij.services.report

import com.github.xygeni.intellij.model.JsonConfig
import com.github.xygeni.intellij.model.report.cicd.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * CicdService
 *
 * @author : Carmendelope
 * @version : 16/10/25 (Carmendelope)
 **/
@Service(Service.Level.PROJECT)
class CicdService (project: Project) : BaseReportService<CicdXygeniIssue>(
    project,
    "misconf"
){
    override fun processReport(jsonString: String): List<CicdXygeniIssue> {
        val report = JsonConfig.relaxed.decodeFromString<CicdReport>(jsonString)
        val toolName = report.metadata.reportProperties["tool.name"]

        val parsedIssues = report.misconfigurations.map { raw ->
            val t = raw.toIssue(toolName, report.currentBranch)
            //println(t)
            t
        }
        return parsedIssues
    }
}