package com.github.xygeni.intellij.views.report

import com.github.xygeni.intellij.model.report.sca.ScaXygeniIssue
import com.github.xygeni.intellij.render.ScaIssueRenderer
import com.github.xygeni.intellij.services.report.ScaService
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.jcef.JBCefApp
import icons.Icons
import javax.swing.tree.DefaultMutableTreeNode

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
/*
    override fun buildNode(item: ScaXygeniIssue): DefaultMutableTreeNode {
        return DefaultMutableTreeNode(
            NodeData(
                text = "(${item.type}) - ${item.file}",
                icon = item.getIcon(),
                tooltip = item.explanation,
                onClick = {
                    // openFileInEditor(project, item.file, item.beginLine, item.beginColumn, item.endLine, item.endColumn)
                    openFileInEditor(project, item.file, item.beginLine, item.beginColumn)
                },
                onDoubleClick = {
                    this.openDynamicHtml(project, item)
                }
            ))
    }
*/
}