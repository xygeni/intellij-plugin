package com.github.xygeni.intellij.activity

import com.github.xygeni.intellij.dynamichtml.browser.JcefSupport
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.InstallerService
import com.github.xygeni.intellij.services.LicenseService
import com.github.xygeni.intellij.settings.XygeniSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.jcef.JBCefBrowser

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
            installer.validateConnection(settings.apiUrl, settings.apiToken, project) { urlOk, tokenOk ->
                installer.publishConnectionState(project, urlOk, tokenOk)
                if (urlOk && tokenOk) {
                    LicenseService.getInstance().register(project)
                }
            }
        }

        try {
            preloadJcef(project)
        } catch (throwable: Throwable) {
            // JCEF must never break the startup activity (#1688): on IDEs where the
            // JCEF classes are missing, the preload is skipped and the plugin keeps working.
            Logger.warn("JCEF preload skipped: ${throwable.message}")
        }
    }

    private fun preloadJcef(project: Project) {
        // Crash-safe gate (#1688): JcefSupport resolves JBCefApp inside a try, so a missing
        // JCEF module reads as "not available" instead of throwing NoClassDefFoundError here.
        if (!JcefSupport.isAvailable) return

        val start = System.currentTimeMillis()
        Logger.log("JCEF preload: starting...", project)

        try {
            JBCefBrowser("about:blank").dispose()
            val elapsed = System.currentTimeMillis() - start
            Logger.log("JCEF preload: done in $elapsed ms", project)
        } catch (t: Throwable) {
            Logger.warn("JCEF preload failed: ${t.message}")
        }
    }
}