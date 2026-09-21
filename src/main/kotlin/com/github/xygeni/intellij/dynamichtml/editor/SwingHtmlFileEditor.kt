package com.github.xygeni.intellij.dynamichtml.editor

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent

/**
 * SwingHtmlFileEditor — the issue detail on IDEs without JCEF (#1976). Shows the segments that
 * [SwingHtmlAdapter] derives from the renderer HTML: text in JEditorPanes, and native panels for
 * the code flow (graph / path / Explanation) and the remediation buttons, stacked vertically.
 **/
class SwingHtmlFileEditor(
    val project: Project,
    private val virtualFile: VirtualFile
) : UserDataHolderBase(), FileEditor, HtmlDetailEditor {

    private var currentHtml: String = ""
    private var currentData: String? = null

    private val content = ScrollablePanel().apply {
        layout = GridBagLayout()
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(10)
    }
    private val scrollPane = JBScrollPane(content).apply { border = JBUI.Borders.empty() }

    override fun loadHtml(html: String) {
        currentHtml = html
        currentData = null
        render()
    }

    override fun renderData(json: String) {
        currentData = json
        render()
    }

    override fun getComponent(): JComponent = scrollPane
    override fun getPreferredFocusedComponent(): JComponent = content
    override fun getName(): String = "Issue Detail"
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun dispose() {}
    override fun getFile(): VirtualFile = virtualFile
    override fun getState(level: FileEditorStateLevel) = FileEditorState.INSTANCE
    override fun setState(state: FileEditorState) {}
    override fun addPropertyChangeListener(listener: java.beans.PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: java.beans.PropertyChangeListener) {}

    private fun render() {
        val segments = SwingHtmlAdapter.toSegments(currentHtml, currentData)
        SwingUtilities.invokeLater {
            content.removeAll()
            val constraints = GridBagConstraints().apply {
                gridx = 0; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.NORTHWEST
            }
            segments.forEach { segment ->
                val component: JComponent = when (segment) {
                    is DetailSegment.Html -> newHtmlPane().apply { text = segment.html; caretPosition = 0 }
                    is DetailSegment.CodeFlow -> CodeFlowPanel(project, segment.data, ::newHtmlPane)
                    is DetailSegment.FixActions -> RemediationActionsPanel(project, segment.remediationJson)
                }
                content.add(component, constraints)
                constraints.gridy++
            }
            // Filler row so the segments stay packed at the top.
            content.add(JPanel().apply { isOpaque = false }, constraints.apply { weighty = 1.0; fill = GridBagConstraints.BOTH })
            content.revalidate()
            content.repaint()
            scrollPane.verticalScrollBar.value = 0
        }
    }

    private fun newHtmlPane(): JEditorPane = JEditorPane().apply {
        isEditable = false
        isOpaque = false
        editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build().also { kit ->
            kit.styleSheet.addRule(themeStyles())
        }
        addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                val target = event.url?.toString() ?: event.description
                if (!target.isNullOrBlank()) BrowserUtil.browse(target)
            }
        }
    }

    /** CSS1 subset the HTMLEditorKit honours, filled with the IDE theme's font and colours. */
    private fun themeStyles(): String {
        val font = UIUtil.getLabelFont()
        val foreground = ColorUtil.toHtmlColor(UIUtil.getLabelForeground())
        val muted = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())
        return """
            body { font-family: "${font.family}"; font-size: ${font.size}pt; color: $foreground; margin: 0; }
            h1 { font-size: ${font.size + 4}pt; margin-bottom: 4px; }
            h2 { font-size: ${font.size + 1}pt; margin-top: 16px; margin-bottom: 4px; color: $muted; }
            a { color: #3794FF; }
            table { margin-top: 4px; }
            td { padding: 2px 6px; vertical-align: top; }
            .xy-severity-chip { font-weight: bold; }
            .file { font-weight: bold; }
            .line-number { color: $muted; }
            .code-line, pre, code { font-family: monospaced; }
        """.trimIndent()
    }

    /** Tracks the viewport width so the HTML panes wrap instead of growing sideways. */
    private class ScrollablePanel : JPanel(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = 16
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = visibleRect.height
        override fun getScrollableTracksViewportWidth(): Boolean = true
        override fun getScrollableTracksViewportHeight(): Boolean = false
    }
}
