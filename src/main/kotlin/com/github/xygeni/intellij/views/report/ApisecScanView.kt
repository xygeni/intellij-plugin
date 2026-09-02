package com.github.xygeni.intellij.views.report

import com.github.xygeni.intellij.model.report.apisec.ApisecXygeniIssue
import com.github.xygeni.intellij.render.ApisecIssueRenderer
import com.github.xygeni.intellij.services.report.ApisecService
import com.intellij.openapi.project.Project
import icons.Icons

/**
 * ApisecScanView — "API Security" panel in the Xygeni tool window.
 **/
class ApisecScanView(project: Project) : BaseView<ApisecXygeniIssue>(
    project,
    "API Security",
    project.getService(ApisecService::class.java),
    Icons.APISEC_ICON
) {

    override val renderer = ApisecIssueRenderer()
}
