package com.github.xygeni.intellij.dynamichtml.mddialog

/**
 * MarkdownPreviewDialog
 *
 * @author : Carmendelope 
 * @version : 16/2/26 (Carmendelope)
 **/

import com.github.xygeni.intellij.logger.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefBrowser
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
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
        Logger.log("Cancelling Markdown preview...", project)
        browser.dispose() // Free
        super.doCancelAction()
    }

    override fun doOKAction() {
        Logger.log("Closing Markdown preview...", project)
        browser.dispose() // Free
        super.doOKAction()
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
            <html>
            <head>
            <style>
                body { font-family: Arial, sans-serif; padding: 10px; color: #333; }
                h1,h2,h3,h4,h5,h6 { color: #222; }
                code, pre { background-color: #f4f4f4; padding: 2px 4px; border-radius: 4px; }
                pre { display: block; padding: 10px; }
                a { color: #0066cc; text-decoration: none; }
                a:hover { text-decoration: underline; }
                ul, ol { margin-left: 20px; }
            </style>
            </head>
            <body>
            $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }
}


