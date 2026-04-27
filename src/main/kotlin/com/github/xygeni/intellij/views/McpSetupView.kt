package com.github.xygeni.intellij.views

import com.github.xygeni.intellij.MyBundle
import com.github.xygeni.intellij.dynamichtml.editor.DynamicHtmlFileEditor
import com.github.xygeni.intellij.model.PluginContext
import com.github.xygeni.intellij.services.InstallerService
import com.github.xygeni.intellij.settings.XygeniSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ui.UIUtil
import java.awt.Color

object McpSetupView {

    private const val FILE_NAME = "Xygeni MCP Server Setup.dynamic.html"
    private const val SERVER_NAME = "xygeni-mcp-server"
    private val FILE_KEY = Key.create<LightVirtualFile>("xygeni.mcp.setup.file")

    fun show(project: Project) {
        val html = buildHtml()
        ApplicationManager.getApplication().invokeLater {
            val cached = project.getUserData(FILE_KEY)
            val file = cached ?: LightVirtualFile(FILE_NAME, html).also {
                project.putUserData(FILE_KEY, it)
            }
            val manager = FileEditorManager.getInstance(project)
            val wasOpen = manager.isFileOpen(file)
            if (cached != null) {
                file.setContent(this, html, false)
            }
            manager.openFile(file, true)
            if (wasOpen) {
                manager.getEditors(file)
                    .filterIsInstance<DynamicHtmlFileEditor>()
                    .forEach { it.loadHtml(html) }
            }
        }
    }

    private fun buildHtml(): String {
        val installer = ApplicationManager.getApplication().getService(InstallerService::class.java)
        val pluginContext = service<PluginContext>()
        val settings = XygeniSettings.getInstance()

        val scannerInstalled = installer.isInstalled(mcp = false)
        val mcpInstalled = installer.isInstalled(mcp = true)

        val mcpJarPath = if (mcpInstalled) pluginContext.mcpJarFile.absolutePath else "\$XYGENI_MCP_SERVER_JAR"
        val scannerInstallDir = pluginContext.installDir.resolve("xygeni_scanner").absolutePath
        val javaHome = System.getenv("JAVA_HOME") ?: "\$JAVA_HOME"

        val savedUrl = settings.apiUrl.takeIf { it.isNotBlank() }
        val savedToken = settings.apiToken.takeIf { it.isNotBlank() }
        val xygeniUrl = savedUrl ?: "\$XYGENI_URL"
        val xygeniToken = savedToken ?: "\$XYGENI_TOKEN"
        val scannerPathArg = "--scannerPath=$scannerInstallDir"

        val jsonConfig = buildJsonConfig(SERVER_NAME, mcpJarPath, scannerPathArg, javaHome, xygeniUrl, xygeniToken)
        val tomlConfig = buildTomlConfig(SERVER_NAME, mcpJarPath, scannerPathArg, javaHome, xygeniUrl, xygeniToken)

        val showConfig = scannerInstalled && mcpInstalled
        val tokenWarning = if (savedToken != null) {
            MyBundle.message("mcp.setup.warning.tokenInClear")
        } else {
            MyBundle.message("mcp.setup.warning.noToken")
        }

        val css = buildCss()

        val configSection = if (showConfig) {
            """
            <h2>${escapeHtml(MyBundle.message("mcp.setup.section.setupInstructions"))}</h2>

            <div class="note">
                <strong>${escapeHtml(MyBundle.message("mcp.setup.note.label"))}</strong>
                ${escapeHtml(MyBundle.message("mcp.setup.note.autoDownload"))}
            </div>

            <div class="warning">$tokenWarning</div>

            <div class="step">
                <div class="step-number">${escapeHtml(MyBundle.message("mcp.setup.step1.title"))}</div>
                <p>${escapeHtml(MyBundle.message("mcp.setup.step1.body"))}</p>

                <h3>JSON</h3>
                <div class="code-block"><pre>${escapeHtml(jsonConfig)}</pre></div>
                <button class="copy-button" data-copy-prev>${escapeHtml(MyBundle.message("mcp.setup.copyJson"))}</button>

                <h3>TOML</h3>
                <div class="code-block"><pre>${escapeHtml(tomlConfig)}</pre></div>
                <button class="copy-button" data-copy-prev>${escapeHtml(MyBundle.message("mcp.setup.copyToml"))}</button>
            </div>

            <div class="step">
                <div class="step-number">${escapeHtml(MyBundle.message("mcp.setup.step2.title"))}</div>
                <p>${escapeHtml(MyBundle.message("mcp.setup.step2.body"))}</p>
                <ol>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.step2.bullet1"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.step2.bullet2"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.step2.bullet3"))}</li>
                </ol>
            </div>
            """.trimIndent()
        } else {
            """
            <p>${escapeHtml(MyBundle.message("mcp.setup.notInstalled"))}</p>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>${escapeHtml(MyBundle.message("mcp.setup.tab.title"))}</title>
            <style>$css</style>
        </head>
        <body>
            <div class="container">
                <h1>${escapeHtml(MyBundle.message("mcp.setup.heading"))}</h1>
                <p>${escapeHtml(MyBundle.message("mcp.setup.intro"))}</p>

                <h2>${escapeHtml(MyBundle.message("mcp.setup.section.whatIsMcp"))}</h2>
                <p>${escapeHtml(MyBundle.message("mcp.setup.whatIsMcp.body"))}</p>

                <h2>${escapeHtml(MyBundle.message("mcp.setup.section.features"))}</h2>
                <h3>${escapeHtml(MyBundle.message("mcp.setup.features.scanners"))}</h3>
                <ul class="feature-list">
                    <li>SAST (Static Application Security Testing)</li>
                    <li>SCA (Software Composition Analysis)</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.suspectDeps"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.secrets"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.iac"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.cicd"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.malware"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.tampering"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.compliance"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.inventory"))}</li>
                </ul>
                <h3>${escapeHtml(MyBundle.message("mcp.setup.features.guardrails"))}</h3>
                <p>${escapeHtml(MyBundle.message("mcp.setup.features.guardrails.body"))}</p>
                <h3>${escapeHtml(MyBundle.message("mcp.setup.features.aiRemediation"))}</h3>
                <ul class="feature-list">
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.aiRemediation.sast"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.aiRemediation.sca"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.aiRemediation.risk"))}</li>
                </ul>
                <h3>${escapeHtml(MyBundle.message("mcp.setup.features.issueMgmt"))}</h3>
                <ul class="feature-list">
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.issueMgmt.baseline"))}</li>
                    <li>${escapeHtml(MyBundle.message("mcp.setup.features.issueMgmt.mute"))}</li>
                </ul>

                <h2>${escapeHtml(MyBundle.message("mcp.setup.section.prerequisites"))}</h2>
                <div class="step">
                    <div class="step-number">${escapeHtml(MyBundle.message("mcp.setup.prereq.title"))}</div>
                    <p>${escapeHtml(MyBundle.message("mcp.setup.prereq.body"))}</p>
                </div>

                $configSection

                <h2>${escapeHtml(MyBundle.message("mcp.setup.section.help"))}</h2>
                <p>${escapeHtml(MyBundle.message("mcp.setup.help.body"))} <a href="https://docs.xygeni.io" target="_blank">docs.xygeni.io</a></p>
            </div>

            <script>
                document.querySelectorAll('button.copy-button[data-copy-prev]').forEach(function (btn) {
                    btn.addEventListener('click', function () {
                        var block = btn.previousElementSibling;
                        if (!block) return;
                        var pre = block.querySelector('pre') || block;
                        var text = pre.textContent || '';
                        if (navigator.clipboard && navigator.clipboard.writeText) {
                            navigator.clipboard.writeText(text).then(function () {
                                var orig = btn.textContent;
                                btn.textContent = '✓ Copied';
                                setTimeout(function () { btn.textContent = orig; }, 1500);
                            });
                        }
                    });
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun buildJsonConfig(
        serverName: String,
        jarPath: String,
        scannerPathArg: String,
        javaHome: String,
        xygeniUrl: String,
        xygeniToken: String
    ): String {
        return """
            {
              "servers": {
                "$serverName": {
                  "timeout": 60,
                  "type": "stdio",
                  "command": "java",
                  "args": [
                    "-jar",
                    ${jsonString(jarPath)},
                    ${jsonString(scannerPathArg)}
                  ],
                  "env": {
                    "JAVA_HOME": ${jsonString(javaHome)},
                    "XYGENI_URL": ${jsonString(xygeniUrl)},
                    "XYGENI_TOKEN": ${jsonString(xygeniToken)}
                  }
                }
              }
            }
        """.trimIndent()
    }

    private fun buildTomlConfig(
        serverName: String,
        jarPath: String,
        scannerPathArg: String,
        javaHome: String,
        xygeniUrl: String,
        xygeniToken: String
    ): String {
        return """
            [servers."$serverName"]
            timeout = 60
            type = "stdio"
            command = "java"
            args = [
              "-jar",
              ${tomlQuote(jarPath)},
              ${tomlQuote(scannerPathArg)}
            ]

            [servers."$serverName".env]
            JAVA_HOME = ${tomlQuote(javaHome)}
            XYGENI_URL = ${tomlQuote(xygeniUrl)}
            XYGENI_TOKEN = ${tomlQuote(xygeniToken)}
        """.trimIndent()
    }

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun tomlQuote(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return "\"$escaped\""
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun buildCss(): String {
        val fg = colorToCss(UIUtil.getLabelForeground())
        val bg = colorToCss(UIUtil.getPanelBackground())
        val link = colorToCss(UIUtil.getLabelInfoForeground())
        val codeBg = colorToCss(UIUtil.getTextFieldBackground())
        val border = colorToCss(UIUtil.getBoundsColor())
        return """
            :root {
                --xy-fg: $fg;
                --xy-bg: $bg;
                --xy-link: $link;
                --xy-code-bg: $codeBg;
                --xy-border: $border;
            }
            body {
                font-family: sans-serif;
                font-size: 13px;
                background-color: var(--xy-bg);
                color: var(--xy-fg);
                margin: 0;
                padding: 20px;
                line-height: 1.6;
            }
            .container { max-width: 820px; margin: 0 auto; }
            h1, h2, h3 {
                color: var(--xy-link);
                border-bottom: 1px solid var(--xy-border);
                padding-bottom: 6px;
            }
            h1 { font-size: 22px; margin-bottom: 20px; }
            h2 { font-size: 18px; margin-top: 28px; }
            h3 { font-size: 15px; margin-top: 20px; }
            .code-block {
                background-color: var(--xy-code-bg);
                border: 1px solid var(--xy-border);
                border-radius: 4px;
                padding: 10px;
                margin: 10px 0;
                font-family: monospace;
                overflow-x: auto;
            }
            .code-block pre { margin: 0; white-space: pre; }
            .step {
                margin: 14px 0;
                padding: 12px;
                background-color: var(--xy-code-bg);
                border-radius: 4px;
                border-left: 4px solid var(--xy-link);
            }
            .step-number { font-weight: bold; color: var(--xy-link); margin-bottom: 6px; }
            .note, .warning {
                border-radius: 4px;
                padding: 10px;
                margin: 10px 0;
                border: 1px solid var(--xy-border);
            }
            .warning { border-left: 4px solid #d9822b; }
            .note { border-left: 4px solid var(--xy-link); }
            .feature-list { list-style: none; padding: 0; }
            .feature-list li { padding: 2px 0; padding-left: 18px; position: relative; }
            .feature-list li:before { content: "✓"; position: absolute; left: 0; font-weight: bold; }
            .copy-button {
                background-color: var(--xy-link);
                color: var(--xy-bg);
                border: none;
                padding: 6px 12px;
                border-radius: 2px;
                cursor: pointer;
                font-size: 12px;
                margin: 4px 0;
            }
            .copy-button:hover { opacity: 0.85; }
            a { color: var(--xy-link); }
        """.trimIndent()
    }

    private fun colorToCss(color: Color): String = "rgb(${color.red}, ${color.green}, ${color.blue})"
}
