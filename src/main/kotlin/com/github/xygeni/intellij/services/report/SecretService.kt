package com.github.xygeni.intellij.services.report

import com.github.xygeni.intellij.model.JsonConfig
import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.github.xygeni.intellij.model.report.secret.SecretsReport
import com.github.xygeni.intellij.model.report.secret.SecretsXygeniIssue
import com.github.xygeni.intellij.model.report.secret.toIssue
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * SecretService
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/
@Service(Service.Level.PROJECT)
class SecretService(project: Project) : BaseReportService<SecretsXygeniIssue>(
    project,
    "secrets"
) {

    override fun processReport(jsonString: String): List<SecretsXygeniIssue> {
        val report = JsonConfig.relaxed.decodeFromString<SecretsReport>(jsonString)
        val toolName = report.metadata.reportProperties["tool.name"]
        val branch = report.currentBranch

        val parsedIssues = report.secrets.map { raw ->
            raw.toIssue(toolName, branch)
        }
        return parsedIssues
    }

}