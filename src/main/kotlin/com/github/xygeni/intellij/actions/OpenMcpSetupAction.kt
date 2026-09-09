package com.github.xygeni.intellij.actions

import com.github.xygeni.intellij.views.McpSetupView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class OpenMcpSetupAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        McpSetupView.show(project)
    }
}
