package com.github.xygeni.intellij.views.report

import com.github.xygeni.intellij.model.report.iac.IacXygeniIssue
import com.github.xygeni.intellij.services.report.IacService
import com.intellij.openapi.project.Project
import icons.Icons
import javax.swing.tree.DefaultMutableTreeNode

/**
 * IacScanView
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/
class IacScanView(project: Project) : BaseView<IacXygeniIssue>(
    project,
    "IAC",
    project.getService(IacService::class.java),
    Icons.IAC_ICON
) {

    override fun buildNode(item: IacXygeniIssue): DefaultMutableTreeNode {
        return DefaultMutableTreeNode(
            NodeData(
                text = "(${item.type}) - ${item.file}",
                icon = item.getIcon(),
                tooltip = item.explanation,
                onClick = {
                    openFileInEditor(project, item.file, item.beginLine, item.beginColumn)
                }
            ))
    }

}