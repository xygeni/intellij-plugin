package com.github.xygeni.intellij.dynamichtml.browser

/**
 * EditorBrowserContext
 *
 * @author : Carmendelope
 * @version : 14/11/25 (Carmendelope)
 **/
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.RemediateService
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.JComponent
import javax.swing.SwingUtilities


class EditorBrowserContext(private val project: Project) {

    private val browserWrapper: JBCefBrowserWrapper
    private val jsQuery: JBCefJSQuery

    @Volatile
    private var ready = false

    @Volatile
    private var pendingData: String? = null

    init {
        if (!SwingUtilities.isEventDispatchThread()) {
            val holder = arrayOfNulls<JBCefBrowserWrapper>(1)
            SwingUtilities.invokeAndWait { holder[0] = JBCefBrowserWrapper() }
            browserWrapper = holder[0]!!
        } else {
            browserWrapper = JBCefBrowserWrapper()
        }

        jsQuery = JBCefJSQuery.create(browserWrapper.browser as com.intellij.ui.jcef.JBCefBrowserBase)

        // JSQuery handler
        jsQuery.addHandler { data ->
            Logger.log("JSQuery received: $data", project)
            handleJsAction(data)
            JBCefJSQuery.Response("OK")
        }

        attachHandlers()
    }

    private fun attachHandlers() {
        val client = browserWrapper.browser.jbCefClient

        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true) return
                SwingUtilities.invokeLater {


                            browser?.executeJavaScript(
                                """
                                window.pluginAction = function(action, value) {
                                    ${jsQuery.inject("""JSON.stringify({action: action, data: value})""")}
                                };
                                """.trimIndent(),
                                browser.url ?: "",
                                0
                            )
                            flushPendingData()
                            ready = true
                            Logger.log("EditorBrowserContext: onLoadEnd -> pluginAction injected, ready=true")
                        }
            }
        }, browserWrapper.browser.cefBrowser)

    }

    private fun flushPendingData() {
        pendingData?.let { data ->
            pendingData = null
            SwingUtilities.invokeLater {
                try {
                    browserWrapper.browser.cefBrowser.executeJavaScript(
                        "if (window.renderData) window.renderData($data);",
                        browserWrapper.browser.cefBrowser.url ?: "",
                        0
                    )
                } catch (e: Exception) {
                    Logger.log("Error executing pending renderData: ${e.message}", project)
                }
            }
        }
    }

    fun loadHtml(html: String) {
        SwingUtilities.invokeLater {
            // carga la página
            browserWrapper.browser.loadHTML(html)
            // reset ready/pending cuando cargamos nuevo HTML
            ready = false
        }
    }
    fun renderData(json: String) {
        if (!ready) {
            Logger.log("renderData(): browser not ready → storing pendingData")
            pendingData = json
            return
        }

        SwingUtilities.invokeLater {
            browserWrapper.browser.cefBrowser.executeJavaScript(
                "if (window.renderData) window.renderData($json);",
                browserWrapper.browser.cefBrowser.url ?: "",
                0
            )
        }
    }

    private fun handleJsAction(data: String) {
        try {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(data).jsonObject
            val action = json["action"]?.jsonPrimitive?.content
            val payload = json["data"]?.jsonPrimitive?.content

            when (action) {
                "remediate" -> {
                    project.getService(RemediateService::class.java)
                        .remediate(project, payload ?: "") { success ->
                            SwingUtilities.invokeLater {
                                browserWrapper.browser.cefBrowser.executeJavaScript(
                                    if (success) """
                                        const btn = document.getElementById('rem-preview-button');
                                        if (btn) btn.style.display='none';
                                        const saveBtn = document.getElementById('rem-save-button');
                                        if (saveBtn) { saveBtn.style.display='inline-block'; saveBtn.disabled=false; }
                                    """.trimIndent() else """
                                        const btn = document.getElementById('rem-preview-button');
                                        if (btn) btn.innerText='❌ Remediation error';
                                    """.trimIndent(),
                                    browserWrapper.browser.cefBrowser.url, 0
                                )
                            }
                        }
                }
                "save" -> {
                    project.getService(RemediateService::class.java)
                        .save(project, payload ?: "")
                }
            }
        } catch (e: Exception) {
            println("Error parsing JSQuery data: ${e.message}")
        }
    }


    // Ejecutar script JS arbitrario
    fun executeScript(script: String) {
        SwingUtilities.invokeLater {
            browserWrapper.browser.cefBrowser.executeJavaScript(script, browserWrapper.browser.cefBrowser.url, 0)
        }
    }

    // Enviar acción Kotlin → JS
    fun sendActionToKotlin(action: String, data: String) {
        jsQuery.inject("""${action}:${data}""")
    }

    val browserComponent: JComponent get() = browserWrapper.component

    fun dispose() {
        if (SwingUtilities.isEventDispatchThread()) {
            jsQuery.dispose()
            browserWrapper.dispose()
        } else {
            SwingUtilities.invokeLater {
                jsQuery.dispose()
                browserWrapper.dispose()
            }
        }
    }
}


class JBCefBrowserWrapper {
    val browser: JBCefBrowser = JBCefBrowser()
    val component: JComponent get() = browser.component
    val cefBrowser: CefBrowser get() = browser.cefBrowser
    fun loadHTML(html: String) = browser.loadHTML(html)
    fun dispose() = browser.dispose()
}