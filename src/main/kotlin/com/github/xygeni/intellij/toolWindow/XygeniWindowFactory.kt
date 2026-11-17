package com.github.xygeni.intellij.toolWindow

import com.github.xygeni.intellij.views.ConfigurationPanel
import com.github.xygeni.intellij.views.HelpBlockView
import com.github.xygeni.intellij.views.ScanConfigView
import com.github.xygeni.intellij.views.ScanView
import com.github.xygeni.intellij.views.XygeniSettingsView
import com.github.xygeni.intellij.views.report.*
import com.intellij.openapi.actionSystem.ActionManager
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
        // Tracks the viewport width (a plain JPanel keeps its preferred width, so a long URL made the column wider
        // than the tool window and clipped what was on the right: the toggles, the ends of the descriptions).
        val menuPanel = object : JPanel(), javax.swing.Scrollable {
            override fun getPreferredScrollableViewportSize() = preferredSize
            override fun getScrollableUnitIncrement(visibleRect: java.awt.Rectangle, orientation: Int, direction: Int) = 16
            override fun getScrollableBlockIncrement(visibleRect: java.awt.Rectangle, orientation: Int, direction: Int) = visibleRect.height
            override fun getScrollableTracksViewportWidth() = true
            override fun getScrollableTracksViewportHeight() = false
        }
        menuPanel.layout = BoxLayout(menuPanel, BoxLayout.Y_AXIS)

        // ----------------- CONFIGURATION: Connection, Scan, Scan configuration (#415) -----------------
        val settingsView = XygeniSettingsView(project)
        settingsView.initialize()
        val scanView = ScanView(project)
        scanView.initialize()
        val scanConfigView = ScanConfigView(project)
        scanConfigView.initialize()
        menuPanel.add(ConfigurationPanel(listOf(settingsView, scanView, scanConfigView)))

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

        val malwareView = MalwareScanView(project)
        menuPanel.add(malwareView)

        val qualityView = QualityScanView(project)
        menuPanel.add(qualityView)

        val apisecView = ApisecScanView(project)
        menuPanel.add(apisecView)

        val aiView = AiScanView(project)
        menuPanel.add(aiView)

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

        // Gear in the tool window header: the Xygeni settings (connection, auto scan, scanner options).
        ActionManager.getInstance().getAction("Xygeni.OpenSettings")?.let { toolWindow.setTitleActions(listOf(it)) }

    }
}