package com.github.xygeni.intellij.views.report

import com.github.xygeni.intellij.model.report.sca.ScaXygeniIssue
import com.github.xygeni.intellij.render.ScaIssueRenderer
import com.github.xygeni.intellij.services.report.ScaService
import com.intellij.openapi.project.Project
import icons.Icons

/**
 * ScaScanView
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/


class ScaScanView (project: Project) : BaseView<ScaXygeniIssue>(
    project,
    "SCA",
    project.getService(ScaService::class.java),
    Icons.SCA_ICON
){

    override val renderer = ScaIssueRenderer()

    override fun getToolTipExplanation(item: ScaXygeniIssue): String {
        if (item.displayFileName.isNullOrBlank() ){
            return item.explanation
        }
        return "<b>Affecting: ${item.displayFileName}</b><br><br>${item.explanation}"
    }

}