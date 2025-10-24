package com.github.xygeni.intellij.views.report

import com.github.xygeni.intellij.model.report.sca.ScaXygeniIssue
import com.github.xygeni.intellij.model.report.secret.SecretsXygeniIssue
import com.github.xygeni.intellij.render.ScaIssueRenderer
import com.github.xygeni.intellij.render.SecretIssueRenderer
import com.github.xygeni.intellij.services.report.SecretService
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.jcef.JBCefApp
import icons.Icons
import javax.swing.tree.DefaultMutableTreeNode

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
/*
    override fun buildNode(item: SecretsXygeniIssue): DefaultMutableTreeNode {
        return DefaultMutableTreeNode(
            NodeData(
                text = "(${item.type}) - ${item.file}",
                icon = item.getIcon(),
                tooltip = "secret of type '${item.type}' detected by '${item.detector}' ",
                onClick = {
                    openFileInEditor(project, item.file, item.beginLine, item.beginColumn)
                },
                onDoubleClick = {
                    this.openDynamicHtml(project, item)
                }
            ))
    }
*/
}