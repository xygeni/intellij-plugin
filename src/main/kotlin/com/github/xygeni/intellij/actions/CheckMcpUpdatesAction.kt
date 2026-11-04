package com.github.xygeni.intellij.actions

import com.github.xygeni.intellij.services.InstallerService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager

/** Tools > Xygeni > Check for MCP Server Updates: checks now, whatever the "MCP server updates" setting (#2382). */
class CheckMcpUpdatesAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ApplicationManager.getApplication().getService(InstallerService::class.java).checkMcpUpdate(e.project, force = true)
    }
}
