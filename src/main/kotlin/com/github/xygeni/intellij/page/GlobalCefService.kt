package com.github.xygeni.intellij.page

/**
 * GlobalCefService
 *
 * @author : Carmendelope
 * @version : 5/11/25 (Carmendelope)
 **/
import com.github.xygeni.intellij.services.RemediateService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.SwingUtilities
import javax.swing.Timer

@Service(Service.Level.APP)
class GlobalCefService {
    val browser: JBCefBrowser = JBCefBrowser()
    private val client = browser.jbCefClient
    private val jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)

    private var currentProject: Project? = null
    private var pendingData: String? = null
    private var currentFileId: String? = null

    private var handlersAttached = false


    init {
        println("GlobalCefService initialized (hash=${this.hashCode()})")
        attachHandlersOnce()
    }

    private fun attachHandlersOnce(){
        if (handlersAttached) {
            println("Handlers already attached, skipping")
            return
        }
        handlersAttached = true

        println("Attaching global handlers for JCEF")

        // Handler para onLoadEnd — se registra solo una vez
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true) return
                println("Global onLoadEnd url=${browser?.url}")

                SwingUtilities.invokeLater {
                    Timer(200) {
                        SwingUtilities.invokeLater {
                            println("Injecting pluginAction (global)")
                            browser?.executeJavaScript(
                                """
                                window.pluginAction = function(action, value) {
                                    ${jsQuery.inject("""JSON.stringify({action: action, data: value})""")}
                                };
                                """.trimIndent(),
                                browser.url,
                                0
                            )

                            pendingData?.let {
                                println("Rendering pending data (${it.length} chars)")
                                browser?.executeJavaScript(
                                    "window.renderData($it);",
                                    browser.url,
                                    0
                                )
                                pendingData = null
                            }
                        }
                    }.apply { isRepeats = false; start() }
                }
            }
        }, browser.cefBrowser)

        jsQuery.addHandler { data ->
            println("Global JSQuery handler -> $data")
            handleJsAction(data)
            JBCefJSQuery.Response("OK")
        }
    }

    private fun handleJsAction(data: String){
        val json = Json.parseToJsonElement(data).jsonObject
        val action = json["action"]?.jsonPrimitive?.content
        val payload = json["data"]?.jsonPrimitive?.content

        when (action) {
            "remediate" -> {
                println("➡ remediate action: $payload")
                currentProject?.let {
                    val exe = it.getService(RemediateService::class.java)
                    exe.remediate(it, payload ?: "") { success ->
                        SwingUtilities.invokeLater {
                            browser.cefBrowser.executeJavaScript(
                                if (success) """
                                        const btn = document.getElementById('rem-preview-button');
                                        if (btn) btn.style.display='none';
                                        const saveBtn = document.getElementById('rem-save-button');
                                        if (saveBtn) { saveBtn.style.display='inline-block'; saveBtn.disabled=false; }
                                    """.trimIndent() else """
                                        const btn = document.getElementById('rem-preview-button');
                                        if (btn) btn.innerText='❌ Remediation error';
                                    """.trimIndent(),
                                browser.cefBrowser.url, 0
                            )
                        }
                    }
                }
            }

            "save" -> {
                println("➡ save action: $payload")
                currentProject?.getService(RemediateService::class.java)?.save(currentProject!!, payload ?: "")
            }
        }
    }

    fun loadHtmlOnce(html: String, project: Project, fileId: String) {
        currentProject = project
        currentFileId = fileId
        pendingData = null
        println("loadHtmlOnce(): fileId=$fileId, html.length=${html.length}")
        browser.loadHTML(html)
    }

    fun renderData(json: String, fileId: String) {
        if (fileId != currentFileId) {
            println("renderData ignored — fileId=$fileId (current=$currentFileId)")
            pendingData = json
            return
        }

        SwingUtilities.invokeLater {
            browser.cefBrowser.executeJavaScript(
                "if (window.renderData) window.renderData($json);",
                browser.cefBrowser.url,
                0
            )
        }
    }

    fun loadContent(html: String, data: String?, project: Project) {
        println("loadContent() called for project=${project.name}, html.length=${html.length}, data=${data?.length ?: 0}")
        currentProject = project
        pendingData = data

        browser.loadHTML(html)
    }
}
