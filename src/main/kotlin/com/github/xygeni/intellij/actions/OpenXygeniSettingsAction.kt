package com.github.xygeni.intellij.actions

import com.github.xygeni.intellij.settings.XygeniSettingsConfigurable
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

/**
 * OpenXygeniSettingsAction
 *
 * @author : Carmendelope 
 * @version : 29/1/26 (Carmendelope)
 **/
class OpenXygeniSettingsAction : AnAction(AllIcons.Actions.Properties) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            XygeniSettingsConfigurable::class.java
        )
    }
}