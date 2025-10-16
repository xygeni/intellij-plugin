package com.github.xygeni.intellij.actions

import com.github.xygeni.intellij.services.ScanService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * TestingAction2
 *
 * @author : Carmendelope
 * @version : 9/10/25 (Carmendelope)
 **/
class ScanAction: AnAction(AllIcons.Actions.Suspend){
    override fun actionPerformed(e: AnActionEvent) {
        Messages.showMessageDialog(
            e.project,
            "¡Lanzamos un scan",
            "Saludo",
            Messages.getInformationIcon()
        )

        val project = e.project ?: return
        project.getService(ScanService::class.java).scan(project)
    }

}