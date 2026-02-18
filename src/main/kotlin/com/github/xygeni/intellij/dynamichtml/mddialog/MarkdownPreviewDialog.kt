package com.github.xygeni.intellij.dynamichtml.mddialog

/**
 * MarkdownPreviewDialog
 *
 * @author : Carmendelope 
 * @version : 16/2/26 (Carmendelope)
 **/

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.UIUtil
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.awt.Color
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Diálogo de preview de Markdown para vulnerabilidad SAST
 */
class MarkdownPreviewDialog(
    private val project: Project,
    private val markdownFile: File,
    private val titleText: String? = null
) : DialogWrapper(project) {

    private val browser: JBCefBrowser

    init {
        if (! titleText.isNullOrEmpty() ) {this.title = titleText}
        isModal = false

        // REad the markdown
        val mdContent = markdownFile.readText()
        val htmlContent = markdownToHtml(mdContent)

        browser = JBCefBrowser()
        browser.loadHTML(htmlContent)

        init() // required for DialogWrapper
    }

    override fun createCenterPanel(): JComponent? {
        val panel = JPanel()
        panel.layout = java.awt.BorderLayout()
        panel.add(JBScrollPane(browser.component), java.awt.BorderLayout.CENTER)
        return panel
    }

    override fun doCancelAction() {
        browser.dispose() // Free
        super.doCancelAction()
    }

    override fun doOKAction() {
        browser.dispose() // Free
        super.doOKAction()
    }

    private fun colorToCss(color: Color): String = "rgb(${color.red}, ${color.green}, ${color.blue})"

    private fun getThemeStyles(): String {
        val fg = UIUtil.getLabelForeground()
        val bg = UIUtil.getPanelBackground()
        val isDark = ColorUtil.isDark(bg)
        val link = JBColor.namedColor("link.foreground", JBColor(0x0066cc, 0x589df6))
        val codeBg = if (isDark) "rgba(255, 255, 255, 0.1)" else "#f4f4f4"
        val borderColor = if (isDark) "rgba(255, 255, 255, 0.2)" else "#ddd"

        return """
            :root {
                --fg: ${colorToCss(fg)};
                --bg: ${colorToCss(bg)};
                --link: ${colorToCss(link)};
                --code-bg: $codeBg;
                --border: $borderColor;
            }
            body { 
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif; 
                padding: 20px; 
                color: var(--fg);
                background-color: var(--bg);
                line-height: 1.6;
            }
            h1, h2, h3, h4, h5, h6 { 
                color: var(--fg); 
                margin-top: 24px;
                margin-bottom: 16px;
                font-weight: 600;
                line-height: 1.25;
            }
            h1 { padding-bottom: 0.3em; border-bottom: 1px solid var(--border); }
            h2 { padding-bottom: 0.3em; border-bottom: 1px solid var(--border); }
            code, pre { 
                background-color: var(--code-bg); 
                padding: 0.2em 0.4em; 
                border-radius: 6px; 
                font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, Liberation Mono, monospace;
                font-size: 85%;
            }
            pre { 
                padding: 16px; 
                overflow: auto;
                line-height: 1.45;
            }
            pre code {
                background-color: transparent;
                padding: 0;
            }
            a { 
                color: var(--link); 
                text-decoration: none; 
            }
            a:hover { text-decoration: underline; }
            ul, ol { padding-left: 2em; }
            blockquote {
                padding: 0 1em;
                color: var(--fg);
                opacity: 0.8;
                border-left: 0.25em solid var(--border);
                margin: 0;
            }
        """.trimIndent()
    }

    /**
     * Convierte Markdown a HTML usando Flexmark (Commonmark)
     */
    private fun markdownToHtml(md: String): String {
        val parser = Parser.builder().build()
        val document = parser.parse(md)
        val renderer = HtmlRenderer.builder().build()
        val bodyHtml = renderer.render(document)

        return """
            <!DOCTYPE html>
            <html>
            <head>
            <style>
                ${getThemeStyles()}
            </style>
            </head>
            <body>
            $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }
}


