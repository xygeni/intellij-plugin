package com.github.xygeni.intellij.toolWindow

/**
 * InstallerToolWindowFactory
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/
import com.github.xygeni.intellij.services.ConsoleService
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JPanel

class ConsoleWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())
        val console: ConsoleView = ConsoleViewImpl(project, true)
        panel.add(console.component, BorderLayout.CENTER)
        panel.add(console.component)

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        // Guardamos el console en tu Installer
        val consleSvc = ApplicationManager.getApplication().getService(ConsoleService::class.java)
        consleSvc.setConsoleView(console)
    }
}