package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.dynamichtml.browser.JcefSupport
import com.github.xygeni.intellij.dynamichtml.mddialog.MarkdownPreviewDialog
import com.github.xygeni.intellij.model.PluginContext
import com.github.xygeni.intellij.notifications.NotificationService
import com.github.xygeni.intellij.settings.XygeniSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.io.File
import java.util.*

/**
 * AIExplainService
 *
 * @author : Carmendelope 
 * @version : 16/2/26 (Carmendelope)
 **/

@Service(Service.Level.PROJECT)
class AIExplainService : ProcessExecutorService(){
    private val pluginContext = service<PluginContext>()

    private val baseArgs: Map<String, String> = mapOf(
        "util" to "",
        "ai-explain" to ""
    )

    private fun buildArgs(issueJsonFile: String, filePath: String): Map<String, String> {
        return baseArgs.toMutableMap().apply {
            put ("-f", filePath)
            // Pass the issue JSON through a file instead of --issue-json inline. The JSON contains
            // double quotes (e.g. the offending code snippet), which break command-line quoting when
            // the scanner is launched via `powershell.exe -File xygeni.ps1` on Windows, splitting the
            // argument and aborting with "Unmatched arguments" (see issue #410 / #400).
            put("--issue-json-file", issueJsonFile)
        }
    }

    fun explain(project: Project,
                data: String,
                onComplete: (Boolean) -> Unit
    ){
        val tempDir = File(System.getProperty("java.io.tmpdir"), "xygeni-plugin/${project.name}")
            .apply { mkdirs() }
        val fileName = "${tempDir.absolutePath}/${UUID.randomUUID()}"

        // Write the issue JSON to a temp file so it never travels on the command line.
        val issueJsonFile = File("$fileName.issue.json").apply { writeText(data) }

        executeProcess(
            path=pluginContext.xygeniCommand,
            args = buildArgs(issueJsonFile.absolutePath, fileName),
            workingDir = pluginContext.installDir,
            envs = XygeniSettings.getInstance().toEnv(),
            project = project,
            onComplete = { success ->
                if (success) { showMarkdownPreviewAsync(project, fileName) }
                issueJsonFile.delete()
                onComplete(success)
            }

        )
    }

    fun showMarkdownPreviewAsync(project: Project, markdownFile: String, title: String = "Explanation") {
        // MarkdownPreviewDialog is JCEF-backed (#1688): without JCEF, tell the user explicitly
        // instead of throwing NoClassDefFoundError while opening the dialog.
        if (!JcefSupport.isAvailable) {
            NotificationService.notifyWarn(
                "The embedded browser (JCEF) is not available in this IDE, so the explanation " +
                    "preview cannot be shown. The explanation was saved to: $markdownFile",
                project
            )
            return
        }
        ApplicationManager.getApplication().invokeLater {
            val dialog = MarkdownPreviewDialog(project, File(markdownFile), title)
            dialog.setUndecorated(false)
            dialog.isResizable = true
            dialog.show()
        }
    }
}