package com.github.xygeni.intellij.views.report

import com.github.xygeni.intellij.model.report.secret.SecretsXygeniIssue
import com.github.xygeni.intellij.render.SecretIssueRenderer
import com.github.xygeni.intellij.services.report.SecretService
import com.intellij.openapi.project.Project
import icons.Icons

/**
 * SecretsScanView
 *
 * @author : Carmendelope
 * @version : 14/10/25 (Carmendelope)
 **/
class SecretsScanView(project: Project) : BaseView<SecretsXygeniIssue>(
    project,
    "SECRETS",
    project.getService(SecretService::class.java),
    Icons.SECRET_ICON
) {

    override val renderer = SecretIssueRenderer()
}