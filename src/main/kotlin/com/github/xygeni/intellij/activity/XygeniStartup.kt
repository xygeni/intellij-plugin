package com.github.xygeni.intellij.activity

import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.InstallerService
import com.github.xygeni.intellij.settings.XygeniSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * XygeniStartup
 *
 * @author : Carmendelope
 * @version : 14/11/25 (Carmendelope)
 **/

class XygeniStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        Logger.log("Welcome to Xygeni scanner", project)
        ApplicationManager.getApplication().executeOnPooledThread {
            val installer = ApplicationManager.getApplication().getService(InstallerService::class.java)
            val settings = XygeniSettings.getInstance()
            // install xygeni
            installer.install(project)
            // check connection
            installer.validateConnection(settings.apiUrl, settings.apiToken, project, {
                urlOk, tokenOk -> installer.publishConnectionState(project, urlOk, tokenOk)
            })
        }
    }
}