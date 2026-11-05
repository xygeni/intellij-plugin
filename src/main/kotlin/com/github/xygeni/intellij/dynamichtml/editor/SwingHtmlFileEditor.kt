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
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
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
    /** Title of the tab on show; null → the first one. Kept when the detector data re-renders the page. */
    private var selectedTab: String? = null

    // VerticalLayout, not GridBagLayout: GridBag drops EVERY row to its minimum size as soon as one
    // row is wider than the viewport (the tab strip on a narrow tool window), which squashed the code
    // flow graph to ~150 px.
    private val content = ScrollablePanel().apply {
        layout = VerticalLayout(0, VerticalLayout.FILL)
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(10)
    }
    private val scrollPane = JBScrollPane(content).apply { border = JBUI.Borders.empty() }

    override fun loadHtml(html: String) {
        currentHtml = html
        currentData = null
        selectedTab = null
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
        val page = SwingHtmlAdapter.toPage(currentHtml, currentData)
        SwingUtilities.invokeLater {
            content.removeAll()
            page.header.forEach { segment -> segmentComponent(segment)?.let(content::add) }
            val selected = page.tabs.firstOrNull { tab -> tab.title == selectedTab } ?: page.tabs.firstOrNull()
            if (selected != null) {
                content.add(tabStrip(page.tabs.map { tab -> tab.title }, selected.title))
                // The browser gives each tab pane a 10px margin; keep it so the pane does not hug the strip's edge.
                val pane = JPanel(VerticalLayout(JBUI.scale(8), VerticalLayout.FILL)).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(10, 10, 0, 10)
                }
                selected.segments.forEach { segment -> segmentComponent(segment)?.let(pane::add) }
                content.add(pane)
            }
            content.revalidate()
            content.repaint()
            scrollPane.verticalScrollBar.value = 0
        }
    }

    private fun segmentComponent(segment: DetailSegment): JComponent? = when (segment) {
        is DetailSegment.Html -> if (hasVisibleText(segment.html)) createHtmlPane().apply { text = segment.html; caretPosition = 0 } else null
        is DetailSegment.CodeFlow -> CodeFlowPanel(project, segment.data) { createHtmlPane() }
        is DetailSegment.FixActions -> RemediationActionsPanel(project, segment.remediationJson)
        is DetailSegment.TabStart -> null
    }

    /** Pieces left between native components are often just closing tags; they would only add gaps. */
    private fun hasVisibleText(html: String): Boolean =
        html.replace(Regex("<[^>]*>"), "").replace("&nbsp;", "").isNotBlank()

    /** The browser tab strip redrawn as links: clicking one shows that tab's pane in place of the current one. */
    private fun tabStrip(titles: List<String>, selected: String): JEditorPane {
        val cells = titles.withIndex().joinToString("") { (index, title) ->
            if (title == selected) {
                "<td class=\"xy-tab-selected\" nowrap>$title</td>"
            } else {
                "<td class=\"xy-tab\" nowrap><a href=\"$TAB_LINK_PREFIX$index\">$title</a></td>"
            }
        }
        return createHtmlPane { link ->
            link.removePrefix(TAB_LINK_PREFIX).toIntOrNull()?.let(titles::getOrNull)?.let { title ->
                selectedTab = title
                render()
            }
        }.apply { text = "<html><body><table class=\"xy-tab-strip\"><tr>$cells</tr></table></body></html>" }
    }

    companion object {
        private const val TAB_LINK_PREFIX = "xy-tab:"

        /**
         * A read-only HTML pane styled with the IDE theme; also used by [CodeFlowPanel]. Links go to the
         * browser, except the [TAB_LINK_PREFIX] ones of the tab strip, which go to [onTabLink].
         */
        fun createHtmlPane(onTabLink: ((String) -> Unit)? = null): JEditorPane = JEditorPane().apply {
            isEditable = false
            isOpaque = false
            editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build().also { kit ->
                kit.styleSheet.addRule(themeStyles())
            }
            addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    val target = event.url?.toString() ?: event.description
                    when {
                        target.isNullOrBlank() -> {}
                        onTabLink != null && target.startsWith(TAB_LINK_PREFIX) -> onTabLink(target)
                        else -> BrowserUtil.browse(target)
                    }
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
                .xy-tab-strip { margin-top: 12px; margin-bottom: 4px; }
                .xy-tab, .xy-tab-selected { padding: 5px 10px 6px 10px; font-weight: bold; }
                .xy-tab { border-bottom: 3px solid $muted; }
                .xy-tab a { color: $muted; text-decoration: none; }
                .xy-tab-selected { color: $foreground; border-bottom: 3px solid #3794FF; }
                p { margin-top: 0; margin-bottom: 6px; }
                .xy-fix-title { font-weight: bold; color: $muted; margin-bottom: 8px; }
                th { font-weight: normal; text-align: left; vertical-align: top; padding: 2px 12px 2px 0; }
                td { padding: 2px 6px; vertical-align: top; }
                ol, ul { margin-top: 0; margin-bottom: 0; margin-left: 20px; }
                .xy-severity-critical { font-weight: bold; background-color: #FEE2E2; color: #991B1B; }
                .xy-severity-high { font-weight: bold; background-color: #FFEDD5; color: #9A3412; }
                .xy-severity-low { font-weight: bold; background-color: #FEF9C3; color: #854D0E; }
                .xy-severity-info { font-weight: bold; background-color: #DBEAFE; color: #1E40AF; }
                .file { font-weight: bold; }
                .line-number { color: $muted; }
                .code-line, pre, code { font-family: monospaced; }
            """.trimIndent()
        }
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
