package com.github.xygeni.intellij.views.report

import com.github.xygeni.intellij.model.report.sast.SastXygeniIssue
import com.github.xygeni.intellij.render.BaseHtmlIssueRenderer
import com.github.xygeni.intellij.render.SastIssueRenderer
import com.github.xygeni.intellij.services.report.SastService
import com.intellij.openapi.project.Project
import icons.Icons
import javax.swing.tree.DefaultMutableTreeNode

/**
 * SastScanView
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/

class SastScanView (project: Project) : BaseView<SastXygeniIssue>(
    project,
    "SAST",
    project.getService(SastService::class.java),
    Icons.SAST_ICON
) {

    override val renderer = SastIssueRenderer()

    override fun buildNode(item: SastXygeniIssue): DefaultMutableTreeNode {
        return DefaultMutableTreeNode(
            NodeData(
                text = "(${item.kind}) - ${item.file}",
                icon = item.getIcon(),
                tooltip = item.explanation,
                onClick = {
                    openFileInEditor(project, item.file, item.beginLine, item.beginColumn)
                },
                onDoubleClick = {
                    this.openDynamicHtml(project, item)
                }
            ))
    }


}