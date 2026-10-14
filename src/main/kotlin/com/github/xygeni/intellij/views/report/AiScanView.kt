package com.github.xygeni.intellij.views.report

import com.github.xygeni.intellij.model.report.ai.AiXygeniIssue
import com.github.xygeni.intellij.render.AiIssueRenderer
import com.github.xygeni.intellij.services.report.AiService
import com.intellij.openapi.project.Project
import icons.Icons

/**
 * AiScanView — "AI Security" panel in the Xygeni tool window.
 **/
class AiScanView(project: Project) : BaseView<AiXygeniIssue>(
    project,
    "AI Security",
    project.getService(AiService::class.java),
    Icons.AI_ICON
) {

    override val renderer = AiIssueRenderer()
}
