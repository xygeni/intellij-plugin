package com.github.xygeni.intellij.dynamichtml.editor

import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.AIExplainService
import com.github.xygeni.intellij.services.LicenseService
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * CodeFlowPanel — Swing counterpart of the code-flow tab (#1976): the same three controls the
 * browser version offers (Explanation → AI explain, Graph view, Path) over [CodeFlowGraphComponent]
 * and a textual step list, plus the zoom controls of the D3 diagram (zoom in / out / fit / 1:1).
 **/
class CodeFlowPanel(
    private val project: Project,
    private val data: CodeFlowData,
    private val htmlPaneFactory: () -> JEditorPane,
) : JPanel(BorderLayout()) {

    private val views = CardLayout()
    private val viewsPanel = JPanel(views)
    private val explainButton = JButton("Explanation")
    private val graphButton = JButton("Graph view")
    private val pathButton = JButton("Path")
    private val graph = CodeFlowGraphComponent(data)

    /** Sized to the graph (so it is never squashed), within [MIN_GRAPH_HEIGHT]..[MAX_GRAPH_HEIGHT]; scrolls beyond that. */
    private val graphScroll = object : JBScrollPane(graph) {
        override fun getPreferredSize(): Dimension {
            val height = graph.preferredSize.height + horizontalScrollBar.preferredSize.height + JBUI.scale(4)
            return Dimension(JBUI.scale(200), height.coerceIn(JBUI.scale(MIN_GRAPH_HEIGHT), JBUI.scale(MAX_GRAPH_HEIGHT)))
        }
    }.apply {
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1)
        viewport.background = JBUI.CurrentTheme.EditorTabs.background()
    }
    private val zoomControls = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
        isOpaque = false
        add(zoomButton(AllIcons.General.ZoomOut, "Zoom out (Ctrl/Cmd + wheel)") { graph.zoomBy(1 / CodeFlowGraphComponent.ZOOM_STEP) })
        add(zoomButton(AllIcons.General.ZoomIn, "Zoom in (Ctrl/Cmd + wheel)") { graph.zoomBy(CodeFlowGraphComponent.ZOOM_STEP) })
        add(zoomButton(AllIcons.General.FitContent, "Fit graph") { fitGraph() })
        add(zoomButton(AllIcons.General.ActualZoom, "Actual size") { graph.resetZoom() })
    }

    init {
        isOpaque = false
        val toolbar = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(8)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(explainButton)
                add(Box.createHorizontalStrut(JBUI.scale(6)))
                add(graphButton)
                add(Box.createHorizontalStrut(JBUI.scale(6)))
                add(pathButton)
            }, BorderLayout.WEST)
            add(zoomControls, BorderLayout.EAST)
        }
        add(toolbar, BorderLayout.NORTH)

        viewsPanel.isOpaque = false
        viewsPanel.add(graphScroll, GRAPH_VIEW)
        viewsPanel.add(htmlPaneFactory().apply { text = pathListHtml() }, PATH_VIEW)
        add(viewsPanel, BorderLayout.CENTER)

        // A graph wider than the tool window starts fitted, like the D3 view that opens with everything in sight.
        graphScroll.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                if (event.component.width <= 0) return
                graphScroll.viewport.removeComponentListener(this)
                if (graph.preferredSize.width > graphScroll.viewport.width) fitGraph()
            }
        })

        graphButton.addActionListener { showView(GRAPH_VIEW) }
        pathButton.addActionListener { showView(PATH_VIEW) }
        explainButton.addActionListener { explain() }
        showView(GRAPH_VIEW)
    }

    private fun showView(name: String) {
        views.show(viewsPanel, name)
        graphButton.isEnabled = name != GRAPH_VIEW
        pathButton.isEnabled = name != PATH_VIEW
        zoomControls.isVisible = name == GRAPH_VIEW
    }

    private fun fitGraph() {
        val viewport = graphScroll.viewport
        graph.fitTo(viewport.width - JBUI.scale(8), JBUI.scale(MAX_GRAPH_HEIGHT) - JBUI.scale(8))
    }

    private fun zoomButton(icon: Icon, tooltip: String, action: () -> Unit): JButton = JButton(icon).apply {
        toolTipText = tooltip
        isFocusable = false
        putClientProperty("JButton.buttonType", "toolbar")
        addActionListener { action() }
    }

    /** Mirrors EditorBrowserContext's "explain" action, including its licence gate. */
    private fun explain() {
        if (!LicenseService.getInstance().isLicenseValid()) {
            Logger.log("AI Explain skipped — IDE seat not licensed", project)
            explainButton.text = "❌ License required"
            explainButton.isEnabled = false
            return
        }
        explainButton.text = "Processing..."
        explainButton.isEnabled = false
        try {
            project.getService(AIExplainService::class.java).explain(project, data.vulnerabilityJson) { success ->
                SwingUtilities.invokeLater {
                    if (success) explainButton.isVisible = false else explainButton.text = "❌ Error"
                }
            }
        } catch (failure: Exception) {
            Logger.error("AI explanation could not start", failure, project)
            explainButton.text = "❌ Error"
        }
    }

    /** Same content as the D3 `renderTextFlowInTab`: one block per step, sorted by level. */
    private fun pathListHtml(): String = buildString {
        append("<html><body>")
        data.nodes.sortedBy { node -> node.level }.forEach { node ->
            val fileName = node.filePath?.substringAfterLast('/')?.ifBlank { null } ?: "Unknown"
            append("<p><b>").append(escape(fileName)).append(':').append(node.line).append("</b>")
            append("&nbsp;&nbsp;<font color=\"#3794FF\">").append(escape(node.type.orEmpty())).append("</font><br>")
            append(escape(node.filePath.orEmpty()))
            val details = listOfNotNull(
                node.category?.takeIf { value -> value.isNotBlank() }?.let { value -> "Category: <b>${escape(value)}</b>" },
                node.container?.takeIf { value -> value.isNotBlank() }?.let { value -> "Container: <b>${escape(value)}</b>" },
                node.injectionPoint?.takeIf { value -> value.isNotBlank() }?.let { value -> "InjectionPoint: <b>${escape(value)}</b>" },
            )
            if (details.isNotEmpty()) append("<br>").append(details.joinToString(" "))
            append("</p>")
            node.code?.takeIf { value -> value.isNotBlank() }?.let { value -> append("<pre>").append(escape(value)).append("</pre>") }
        }
        append("</body></html>")
    }

    private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)

    companion object {
        private const val GRAPH_VIEW = "graph"
        private const val PATH_VIEW = "path"
        private const val MIN_GRAPH_HEIGHT = 260
        private const val MAX_GRAPH_HEIGHT = 520
    }
}
