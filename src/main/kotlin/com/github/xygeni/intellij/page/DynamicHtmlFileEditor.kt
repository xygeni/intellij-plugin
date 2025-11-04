package com.github.xygeni.intellij.page

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.RemediateService
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.*

/**
 * DynamicHtmlFileEditor
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/
class DynamicHtmlFileEditor(private val project: Project, private val file: VirtualFile) : UserDataHolderBase(),
    FileEditor {

    private val panel = JPanel(BorderLayout())
    private var browser: JBCefBrowser? = null
    private var initialized = false
    private var pendingData: String? = null

    private lateinit var jsQuery: JBCefJSQuery

    private fun initBrowser() {
        if (initialized) return
        initialized = true

        SwingUtilities.invokeLater {
            browser = JBCefBrowser()
            val client = browser!!.jbCefClient

            // Evita abrir links externos en el navegador embebido
            client.addRequestHandler(object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    cefBrowser: CefBrowser?,
                    frame: CefFrame?,
                    request: CefRequest?,
                    userGesture: Boolean,
                    isRedirect: Boolean
                ): Boolean {
                    val url = request?.url ?: return false
                    if (url.startsWith("http")) {
                        com.intellij.ide.BrowserUtil.browse(url)
                        return true
                    }
                    return false
                }
            }, browser!!.cefBrowser)

            // Manejo de popups
            client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    targetUrl: String?,
                    targetFrameName: String?
                ): Boolean {
                    if (targetUrl?.startsWith("http") == true) {
                        com.intellij.ide.BrowserUtil.browse(targetUrl)
                        return true
                    }
                    return false
                }
            }, browser!!.cefBrowser)

            // Scroll pane
            val scrollPane = JScrollPane(browser!!.component).apply {
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            }
            panel.add(scrollPane, BorderLayout.CENTER)

            // JSQuery iniciales
            val exe = project.getService(RemediateService::class.java)
            jsQuery = JBCefJSQuery.create(browser!! as JBCefBrowserBase)

            // Remediate handler
            jsQuery.addHandler { data ->
                val json = Json.parseToJsonElement(data).jsonObject
                val action = json["action"]?.jsonPrimitive?.content
                val payload = json["data"]?.jsonPrimitive?.content
                when (action) {
                    "remediate" -> {
                        Logger.log("==================================")
                        Logger.log("Remediate")
                        Logger.log("==================================")
                        remediate(payload?: "")
                    }
                    "save" -> {
                        Logger.log("==================================")
                        Logger.log("Save Fix")
                        Logger.log("==================================")
                        save(payload?: "")
                    }

                }
                JBCefJSQuery.Response("OK")
            }

            // LoadHandler: inyecta JS seguro después de que la página carga
            client.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain == true) {
                        SwingUtilities.invokeLater {
                            println("registro de variables")
                            browser?.executeJavaScript(
                                """
                                window.pluginAction = function(action, value) {
                                    ${jsQuery.inject("""JSON.stringify({action: action, data: value})""")}
                                };                                              
                                """.trimIndent(),
                                browser.url,
                                0
                            )

                            // Render de datos pendientes
                            pendingData?.let {
                                renderData(it)
                                pendingData = null
                            }
                        }
                    }
                }
            }, browser!!.cefBrowser)

            // Cargar HTML
            val html = String(file.contentsToByteArray())
            browser!!.loadHTML(html)
        }
    }

    // TODO: mover esto a una clase nueva
    private fun remediate(data: String) {
        val exe = project.getService(RemediateService::class.java)
        exe.remediate(project, data) { success ->
            SwingUtilities.invokeLater {
                browser?.cefBrowser?.executeJavaScript(
                    if (success) """
                                const btn = document.getElementById('rem-preview-button');
                                if (btn) btn.style.display='none';
                                const saveBtn = document.getElementById('rem-save-button');
                                if (saveBtn) { saveBtn.style.display='inline-block'; saveBtn.disabled=false; }
                            """.trimIndent() else """
                                const btn = document.getElementById('rem-preview-button');
                                if (btn) btn.innerText='❌ Remediation error';
                            """.trimIndent(),
                    browser?.cefBrowser?.url,
                    0
                )
            }
        }
    }

    private fun save (data: String) {
        val exe = project.getService(RemediateService::class.java)
        exe.save(project, data)
    }

    fun renderData(json: String) {
        if (browser == null) {
            pendingData = json
            return
        }

        Timer(400) {
            SwingUtilities.invokeLater {
                browser?.cefBrowser?.executeJavaScript("window.renderData($json);", browser?.cefBrowser?.url, 0)
            }
        }.apply {
            isRepeats = false
            start()
        }
    }

    override fun getComponent(): JComponent {
        initBrowser()
        return panel
    }
    override fun getPreferredFocusedComponent(): JComponent? {
        initBrowser()
        return browser?.component ?: panel
    }
    override fun getName(): String = "HTML Dynamic Viewer"
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun dispose() {
        browser?.dispose()
        browser = null
        initialized = false
        jsQuery?.dispose()
    }
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun setState(state: FileEditorState) {}
    override fun getFile(): VirtualFile = file
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
}
