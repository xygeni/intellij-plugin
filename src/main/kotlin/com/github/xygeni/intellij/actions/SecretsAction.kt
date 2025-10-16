package com.github.xygeni.intellij.actions

import com.github.xygeni.intellij.services.report.SecretService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * SecretsAction
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/
class SecretsAction: AnAction(AllIcons.Actions.Suspend){

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.getService(SecretService::class.java).read()
    }


}