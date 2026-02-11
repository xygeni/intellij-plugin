package com.github.xygeni.intellij.toolWindow

import com.github.xygeni.intellij.services.InstallerService
import com.github.xygeni.intellij.views.HelpBlockView
import com.github.xygeni.intellij.views.ScanView
import com.github.xygeni.intellij.views.XygeniSettingsView
import com.github.xygeni.intellij.views.report.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * XygeniWindowFactory
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/
class XygeniWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val menuPanel = JPanel()
        menuPanel.layout = BoxLayout(menuPanel, BoxLayout.Y_AXIS)

        // ----------------- configuration panel -----------------
        val settingsView = XygeniSettingsView(project)
        settingsView.initialize()
        menuPanel.add(settingsView)

        // ----------------- Scan -----------------
        val scanView = ScanView(project)
        scanView.initialize()
        menuPanel.add(scanView)

        // ----------------- Reports -----------------

        val sastView = SastScanView(project)
        menuPanel.add(sastView)

        val scaView = ScaScanView(project)
        menuPanel.add(scaView)

        val cicdView = CicdScanView(project)
        menuPanel.add(cicdView)

        val secretsView = SecretsScanView(project)
        menuPanel.add(secretsView)

        val iacView = IacScanView(project)
        menuPanel.add(iacView)

        // ----------------- help panel -----------------
        val help = HelpBlockView()
        help.createUI(false)
        menuPanel.add(help)

        // Add vertical glue to push everything up
        menuPanel.add(javax.swing.Box.createVerticalGlue())

        // ----------------- ToolWindow -----------------
        val scrollPane = com.intellij.ui.components.JBScrollPane(menuPanel).apply {
            border = null
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(scrollPane, "", false)
        contentManager.addContent(content)

    }
}