package com.github.xygeni.intellij.page

import com.github.xygeni.intellij.model.JsonConfig
import com.github.xygeni.intellij.model.report.server.RemediationData
import com.github.xygeni.intellij.services.InstallerService
import com.github.xygeni.intellij.services.RemediateService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.Timer

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

    // pending data, render is called before initBrowser
    private var pendingData: String? = null

    private fun initBrowser() {
        if (initialized) return
        initialized = true

        SwingUtilities.invokeLater {
            if (browser == null) {
                browser = JBCefBrowser()

                val client = browser!!.jbCefClient
                // open href in default browser (avoid open it in CefBrowser)
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
                            return true //
                        }
                        return false
                    }
                }, browser!!.cefBrowser)

                client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
                    override fun onBeforePopup(
                        browser: CefBrowser?,
                        frame: CefFrame?,
                        targetUrl: String?,
                        targetFrameName: String?
                    ): Boolean {
                        if (targetUrl != null && targetUrl.startsWith("http")) {
                            com.intellij.ide.BrowserUtil.browse(targetUrl)
                            return true
                        }
                        return false
                    }
                }, browser!!.cefBrowser)

                // -- navigation panel conf
                val scrollPane = JScrollPane(browser!!.component).apply {
                    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                }
                panel.add(scrollPane, BorderLayout.CENTER)

                // Query for javascript
                val jsQuery = JBCefJSQuery.create(browser!!)
                jsQuery.addHandler { data: String ->
                    println("Data from the form: $data")

                    val exe = project.getService(RemediateService::class.java)
                    exe.remediate(project, data){
                        success ->
                        SwingUtilities.invokeLater {
                            val label = if (success) "Save" else "Remediation error"
                            browser!!.cefBrowser.executeJavaScript(
                                """
                                    const btn = document.getElementById('rem-preview-button');
                                    if (btn) {
                                        btn.disabled = false;
                                        btn.innerText = '$label';
                                    }
                                 """.trimIndent(),
                                browser!!.cefBrowser.url,
                                0
                            )
                        }
                    }
                    JBCefJSQuery.Response("Save")
                }

                // Load initial HTML (html template)
                val html = String(file.contentsToByteArray())
                browser!!.loadHTML(html)

                client.addLoadHandler(object : CefLoadHandlerAdapter() {
                    override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                        if (frame?.isMain == true) {
                            SwingUtilities.invokeLater {
                                browser?.executeJavaScript(
                                    "window.remediate = function(value) { ${jsQuery.inject("value")} };",
                                    browser.url,
                                    0
                                )
                            }
                        }
                    }
                }, browser!!.cefBrowser)

                // If there are pending data -> render them
                pendingData?.let {
                    renderData(it)
                    pendingData = null
                }
            }
        }
    }

    /**
     * Send the data to the HTM to render them
     * If the JS is not available -> wait 400 ms
     */
    fun renderData(json: String) {
        if (browser == null) {
            // HTML not ready, store the data and wait for it
            pendingData = json
            return
        }

        // Execute the render after a delay ro ensure Javascript is loaded
        Timer(400) {
            SwingUtilities.invokeLater {
                // println("🔹 Ejecutando renderData con: $json")
                browser?.cefBrowser?.executeJavaScript(
                    "window.renderData($json);",
                    browser?.cefBrowser?.url,
                    0
                )
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
    }

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun setState(state: FileEditorState) {}

    override fun getFile(): VirtualFile = file
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
}

