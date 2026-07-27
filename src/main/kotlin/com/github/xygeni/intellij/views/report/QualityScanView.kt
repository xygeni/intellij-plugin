package com.github.xygeni.intellij.views.report

import com.github.xygeni.intellij.model.report.quality.QualityXygeniIssue
import com.github.xygeni.intellij.render.QualityIssueRenderer
import com.github.xygeni.intellij.services.report.QualityService
import com.intellij.openapi.project.Project
import icons.Icons

/**
 * QualityScanView — "Code Quality" panel in the Xygeni tool window.
 * Mirrors {@link SastScanView}.
 **/
class QualityScanView(project: Project) : BaseView<QualityXygeniIssue>(
    project,
    "Code Quality",
    project.getService(QualityService::class.java),
    Icons.QUALITY_ICON
) {

    override val renderer = QualityIssueRenderer()
}
