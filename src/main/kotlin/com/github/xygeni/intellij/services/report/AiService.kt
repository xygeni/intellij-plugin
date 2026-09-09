package com.github.xygeni.intellij.services.report

import com.github.xygeni.intellij.model.report.ai.AiXygeniIssue
import com.github.xygeni.intellij.model.report.ai.parseAiReport
import com.github.xygeni.intellij.model.report.ai.toIssue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * AiService — reads `ai.<suffix>.json` into [AiXygeniIssue]s.
 **/
@Service(Service.Level.PROJECT)
class AiService(project: Project) : BaseReportService<AiXygeniIssue>(
    project,
    "ai") {

    override fun processReport(jsonString: String): List<AiXygeniIssue> {
        val report = parseAiReport(jsonString)
        val toolName = report.metadata.reportProperties["tool.name"]
        val branch = report.currentBranch
        return report.vulnerabilities.map { raw ->
            raw.toIssue(toolName, branch)
        }
    }
}
