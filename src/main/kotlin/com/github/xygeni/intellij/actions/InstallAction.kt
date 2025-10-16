package com.github.xygeni.intellij.plugin.actions

/**
 * InstallAction
 *
 * @author : Carmendelope
 * @version : 9/10/25 (Carmendelope)
 **/

import com.github.xygeni.intellij.services.InstallerService
import com.github.xygeni.intellij.settings.XygeniSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages


class InstallAction : AnAction(AllIcons.Actions.Suspend){
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showMessageDialog(
            e.project,
            "¡Hola desde mi plugin!",
            "Saludo",
            Messages.getInformationIcon()
        )

        val project = e.project ?: return

        val settings = XygeniSettings.getInstance()
        val config = settings.toPluginConfig()
        val installer = ApplicationManager.getApplication().getService(InstallerService::class.java)
        installer.checkAndInstall(project, config)
    }

}