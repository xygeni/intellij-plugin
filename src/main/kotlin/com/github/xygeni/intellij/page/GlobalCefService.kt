package com.github.xygeni.intellij.page

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

/**
 * GlobalCefService
 *
 * @author : Carmendelope
 * @version : 5/11/25 (Carmendelope)
 *
 * Application-level service that manages a single shared JCEF browser instance
 * across the entire plugin. This allows reusing the same browser for all
 * dynamic HTML views, instead of creating a new JCEF instance per editor.
 *
 * Responsibilities:
 * - Holds a single [JBCefBorwser] instance reused globally.
 * - Injects JavaScript hooks (like window.pluginAction) into loaded pages.
 * - Handles communication between the webview (JS) and Kotlin [via JBCefQuery]
 * - Provides methods for loadin and rendering HTML content with dynamic data
 * - Delegates remediation and save actions to project-level services
 *
 * Notes:
 * - This service is annotated with @Service(Service.Level.APP) so it is unique for the entire
 *   IDE session (no per project)
 * - currentProject is updated by each [DynamicHtmlEditor] when it uses this browser
 *   instance, to allow project-aware actions
 */
@Service(Service.Level.PROJECT)
class GlobalCefService {
    // browser is a shared JCEF browser instance used by all dynamic HTML editors
    val browser: JBCefBrowser = JBCefBrowser()
    // client with an underlying JCEF client for registering handlers
    private val client = browser.jbCefClient
    // jsQuery with a javascript bridge to handle messages from the webview to Kotlin
    private val jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    // currentProject with the project currently associated with the active dynamic editor
    private var currentProject: Project? = null
    // pendingData with an optional peding data to render once the HTML content was fully loaded
    private var pendingData: String? = null
    private var currentFileId: String? = null
    // handlersAttached with a flag to ensure load handlers are attached only once
    private var handlersAttached = false


    init {
        println("GlobalCefService initialized (hash=${this.hashCode()})")
        attachHandlersOnce()
    }

    /**
     *  attachHandlersOnce attaches onLoadEnd and JSQuery handlers to the browser if not already attached
     */
    private fun attachHandlersOnce(){
        if (handlersAttached) {
            println("Handlers already attached, skipping")
            return
        }
        handlersAttached = true

        println("Attaching global handlers for JCEF")

        // Called when the HTML page finishes loading
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true) return
                println("Global onLoadEnd url=${browser?.url}")

                SwingUtilities.invokeLater {
                    Timer(200) {
                        SwingUtilities.invokeLater {
                            println("Injecting pluginAction (global)")
                            // inject the global pluginAction function for JS -> Kotlin calls
                            browser?.executeJavaScript(
                                """
                                window.pluginAction = function(action, value) {
                                    ${jsQuery.inject("""JSON.stringify({action: action, data: value})""")}
                                };
                                """.trimIndent(),
                                browser.url,
                                0
                            )

                            // Render pending daa if any was set before the page was ready
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

        // Handle calls from JS -> Kotlin
        jsQuery.addHandler { data ->
            println("Global JSQuery handler -> $data")
            handleJsAction(data)
            JBCefJSQuery.Response("OK")
        }
    }

    /**
     * handleJsAction handles the "remediate" JS action by delegating to the project service
     */
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

    /**
     * loadHtmlOnce loads the given HTML into the browser
     */
    fun loadHtmlOnce(html: String, project: Project, fileId: String) {
        currentProject = project
        currentFileId = fileId
        pendingData = null
        println("loadHtmlOnce(): fileId=$fileId, html.length=${html.length}")
        browser.loadHTML(html)
    }

    /**
     * renderData renders data, if it is called before loadEnd, keep daa pending
     */
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
}
