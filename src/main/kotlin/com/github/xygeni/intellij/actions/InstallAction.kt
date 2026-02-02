package com.github.xygeni.intellij.actions

/**
 * InstallAction
 *
 * @author : Carmendelope
 * @version : 9/10/25 (Carmendelope)
 **/

import com.github.xygeni.intellij.services.InstallerService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager


class InstallAction : AnAction(){
    override fun actionPerformed(e: AnActionEvent) {
        ApplicationManager.getApplication().getService(InstallerService::class.java).installOrUpdate(e.project)
    }
}