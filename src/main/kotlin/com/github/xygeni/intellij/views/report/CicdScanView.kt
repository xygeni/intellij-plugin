package com.github.xygeni.intellij.views.report

import com.github.xygeni.intellij.model.report.cicd.CicdXygeniIssue
import com.github.xygeni.intellij.render.BaseHtmlIssueRenderer
import com.github.xygeni.intellij.render.CicdIssueRenderer
import com.github.xygeni.intellij.render.SecretIssueRenderer
import com.github.xygeni.intellij.services.report.CicdService
import com.intellij.openapi.project.Project
import icons.Icons
import javax.swing.tree.DefaultMutableTreeNode

/**
 * CicdScanView
 *
 * @author : Carmendelope
 * @version : 16/10/25 (Carmendelope)
 **/
class CicdScanView(project: Project) : BaseView<CicdXygeniIssue>(
    project,
    "CI/CD",
    project.getService(CicdService::class.java),
    Icons.CICD_ICON
) {

    override val renderer = CicdIssueRenderer()

    override fun buildNode(item: CicdXygeniIssue): DefaultMutableTreeNode {
        return DefaultMutableTreeNode(
            NodeData(
                text = "(${item.type}) - ${item.file}",
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