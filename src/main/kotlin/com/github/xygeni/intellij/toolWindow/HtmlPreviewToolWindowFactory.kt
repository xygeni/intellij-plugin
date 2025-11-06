package com.github.xygeni.intellij.toolWindow

/**
 * HtmlPreviewToolWindowFactory
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import javax.swing.JPanel
import javax.swing.SwingUtilities

class HtmlPreviewToolWindowFactory : ToolWindowFactory {

    companion object {
        private var browser: JBCefBrowser? = null
        private var content: Content? = null

        /**
         * Abre o actualiza el HTML dinámicamente en el ToolWindow.
         */
        fun openHtml(project: Project, htmlContent: String) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("HTML Preview") ?: return
            toolWindow.show()  // Mostrar primero

            SwingUtilities.invokeLater {
                if (browser == null) {
                    browser = JBCefBrowser()
                    val panel = JPanel()
                    panel.add(browser!!.component)
                    content = ContentFactory.getInstance().createContent(panel, "", false)
                    toolWindow.contentManager.addContent(content!!)
                }
                browser!!.loadHTML(htmlContent)
            }

        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Inicialización vacía: el contenido se crea dinámicamente en openHtml()

    }
}

