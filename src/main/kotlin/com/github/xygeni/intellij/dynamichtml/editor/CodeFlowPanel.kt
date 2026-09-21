package com.github.xygeni.intellij.dynamichtml.editor

import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.AIExplainService
import com.github.xygeni.intellij.services.LicenseService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * CodeFlowPanel — Swing counterpart of the code-flow tab (#1976): the same three controls the
 * browser version offers (Explanation → AI explain, Graph view, Path) over [CodeFlowGraphComponent]
 * and a textual step list.
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

    init {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            add(explainButton); add(graphButton); add(pathButton)
        }
        add(toolbar, BorderLayout.NORTH)

        val graph = CodeFlowGraphComponent(data)
        val graphScroll = JBScrollPane(graph).apply {
            preferredSize = Dimension(graph.preferredSize.width, minOf(graph.preferredSize.height, 420))
            border = JBUI.Borders.empty()
        }
        viewsPanel.add(graphScroll, GRAPH_VIEW)
        viewsPanel.add(htmlPaneFactory().apply { text = pathListHtml() }, PATH_VIEW)
        add(viewsPanel, BorderLayout.CENTER)

        graphButton.addActionListener { showView(GRAPH_VIEW) }
        pathButton.addActionListener { showView(PATH_VIEW) }
        explainButton.addActionListener { explain() }
        showView(GRAPH_VIEW)
    }

    private fun showView(name: String) {
        views.show(viewsPanel, name)
        graphButton.isEnabled = name != GRAPH_VIEW
        pathButton.isEnabled = name != PATH_VIEW
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
    }
}
