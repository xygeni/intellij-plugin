package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.dynamichtml.mddialog.MarkdownPreviewDialog
import com.github.xygeni.intellij.model.PluginContext
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

    private fun buildArgs(issueJson: String, filePath: String): Map<String, String> {
        return baseArgs.toMutableMap().apply {
            put ("-f", filePath)
            put("--issue-json",issueJson)
        }
    }

    fun explain(project: Project,
                data: String,
                onComplete: (Boolean) -> Unit
    ){
        val tempDir = File(System.getProperty("java.io.tmpdir"), "xygeni-plugin/${project.name}")
            .apply { mkdirs() }
        val fileName = "${tempDir.absolutePath}/${UUID.randomUUID()}"

        executeProcess(
            path=pluginContext.xygeniCommand,
            args = buildArgs(data, fileName),
            workingDir = pluginContext.installDir,
            envs = XygeniSettings.getInstance().toEnv(),
            project = project,
            onComplete = { success ->
                if (success) { showMarkdownPreviewAsync(project, fileName) }
                onComplete(success)
            }

        )
    }

    fun showMarkdownPreviewAsync(project: Project, markdownFile: String, title: String = "Explanation") {
        ApplicationManager.getApplication().invokeLater {
            val dialog = MarkdownPreviewDialog(project, File(markdownFile), title)
            dialog.setUndecorated(false)
            dialog.isResizable = true
            dialog.show()
        }
    }
}