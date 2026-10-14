package com.github.xygeni.intellij.dynamichtml.mddialog

import com.github.xygeni.intellij.services.server.ServerClient
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.io.File
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

/**
 * SwingMarkdownPreviewDialog — the AI explanation preview for IDEs without JCEF (#1976).
 * Same markdown → HTML conversion as the server side ([ServerClient.convert]), shown in a themed
 * JEditorPane instead of the JCEF-backed [MarkdownPreviewDialog].
 **/
class SwingMarkdownPreviewDialog(
    project: Project,
    private val markdownFile: File,
    titleText: String? = null,
) : DialogWrapper(project) {

    init {
        title = titleText?.takeIf { it.isNotBlank() } ?: "Explanation"
        isResizable = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val font = UIUtil.getLabelFont()
        val pane = JEditorPane().apply {
            isEditable = false
            editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build().also { kit ->
                kit.styleSheet.addRule(
                    "body { font-family: \"${font.family}\"; font-size: ${font.size}pt; " +
                        "color: ${ColorUtil.toHtmlColor(UIUtil.getLabelForeground())}; margin: 10px; } " +
                        "pre, code { font-family: monospaced; } a { color: #3794FF; }"
                )
            }
            background = UIUtil.getPanelBackground()
            addHyperlinkListener { event ->
                if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    val target = event.url?.toString() ?: event.description
                    if (!target.isNullOrBlank()) BrowserUtil.browse(target)
                }
            }
            text = ServerClient.convert(markdownFile.takeIf { it.exists() }?.readText().orEmpty())
            caretPosition = 0
        }
        return JBScrollPane(pane).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(720, 520)
        }
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)
}
