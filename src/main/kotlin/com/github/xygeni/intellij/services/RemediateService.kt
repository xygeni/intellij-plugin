package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.JsonConfig
import com.github.xygeni.intellij.model.PluginContext
import com.github.xygeni.intellij.model.report.server.RemediationData
import com.github.xygeni.intellij.settings.XygeniSettings
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

/**
 * RemediateService
 *
 * @author : Carmendelope
 * @version : 30/10/25 (Carmendelope)
 **/
@Service(Service.Level.PROJECT)
class RemediateService : ProcessExecutorService() {

    private val pluginContext = PluginContext() // manager.pluginContext

    private val baseArgs: Map<String, String> = mapOf(
        "util" to "",
        "rectify" to ""
    )

    private fun buildArgs(remediation: RemediationData, filePath: String): Map<String, String> {
        return baseArgs.toMutableMap().apply {
            remediation.kind?.let { put("--${remediation.kind}", "") }
            remediation.filePath?.let { put("--file-path", filePath) }
            remediation.detector?.let { put("--detector", remediation.detector) }
            remediation.line?.let { put("--line", "${remediation.line}") }
            remediation.dependency?.let { put("--dependency", "${remediation.dependency}") }
            "-v" to ""
        }
    }


    private fun getTempFile(project: Project, remediation: RemediationData): File {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "xygeni-plugin/${project.name}")
            .apply { mkdirs() }

        val sourceFile = File(project.basePath, remediation.filePath)
        require(sourceFile.exists()) {
            "Source file does not exist: ${sourceFile.absolutePath}"
        }

        return File(tempDir, sourceFile.name)
    }

    fun remediate(project: Project,
                  data: String,
                  onComplete: (Boolean) -> Unit
    ) {
        val remediation = JsonConfig.relaxed.decodeFromString<RemediationData>(data)
        val source = File(project.basePath, remediation.filePath)


        val target = getTempFile(project, remediation)
        source.copyTo(target, overwrite = true)

        // refresh the fileSystem
        val vfs = LocalFileSystem.getInstance()
        vfs.refresh(true)
        vfs.refreshAndFindFileByPath(target.absolutePath)

        // 2.- lanzamos la remediacion en ese fichero
        executeProcess(
            pluginContext.xygeniCommand,
            buildArgs(remediation, target.absolutePath),
            //PluginConfig.fromSettings(XygeniSettings.getInstance()).toEnv(),
            XygeniSettings. getInstance().toEnv(),
            pluginContext.installDir,
            project,
            { success ->
                if (success) {
                    Logger.log("✅ Remediation finished")
                    showDiff(project, remediation, target.absolutePath )
                } else {
                    Logger.error("❌ Remediation failed")
                }
                onComplete(success)
            })
    }

    // showDiff opens a diff showing
    private fun showDiff(project: Project, remediation: RemediationData, rightPath: String) {
        ApplicationManager.getApplication().invokeLater {

            val source = File(project.basePath, remediation.filePath)

            val left = LocalFileSystem.getInstance().findFileByPath(source.absolutePath)
            val right = LocalFileSystem.getInstance().refreshAndFindFileByPath(rightPath)
            if (left != null && right != null) {

                val factory = DiffContentFactory.getInstance()

                val content1 = factory.create(project, left)
                val content2 = factory.create(project, right)

                val request = SimpleDiffRequest(
                    "Remediation Diff",
                    content1, content2,
                    left.name, right.name
                )
                DiffManager.getInstance().showDiff(project, request)
            }
        }
    }

    // save applies the new safe file to the vuln one
    fun save(project: Project,
             data: String,
    ) {/*
        try {
            val remediation = JsonConfig.relaxed.decodeFromString<RemediationData>(data)
            val source = File(project.basePath, remediation.filePath)

            val target = getTempFile(project, remediation)
            target.copyTo(source, overwrite = true)
            Logger.log("✅ Saved", project)

            // reload the file
            val virtualFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByIoFile(source)

            if (virtualFile != null) {
                ApplicationManager.getApplication().invokeLater {
                    ApplicationManager.getApplication().runWriteAction {
                        virtualFile.refresh(false, false)
                    }
                }
            }

            // if auto scan -> scan again
            if (XygeniSettings.getInstance().autoScan) {
                project.getService(ScanService::class.java).scan(project, true)
            }

        }
        catch (e: Exception) {
            Logger.error("❌ Failed to save remediation file", project)
        }
    }*/
        try {
            val remediation =
                JsonConfig.relaxed.decodeFromString<RemediationData>(data)

            val source = File(project.basePath, remediation.filePath)

            val virtualFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByIoFile(source)
                ?: error("VirtualFile not found for ${source.path}")

            val target = getTempFile(project, remediation)
            val newContent = target.readText()

            ApplicationManager.getApplication().invokeLater {
                ApplicationManager.getApplication().runWriteAction {

                    val fileDocumentManager = FileDocumentManager.getInstance()
                    val document: Document? =
                        fileDocumentManager.getDocument(virtualFile)

                    if (document != null) {
                        // 📂 Archivo abierto en editor
                        if (document.text != newContent) {
                            document.setText(newContent)
                            fileDocumentManager.saveDocument(document)
                        }
                    } else {
                        // 📄 Archivo no abierto
                        VfsUtil.saveText(virtualFile, newContent)
                    }
                }
            }

            Logger.log("✅ Saved", project)

            // 🔁 auto scan
            if (XygeniSettings.getInstance().autoScan) {
                project.getService(ScanService::class.java)
                    .scan(project, true)
            }

        } catch (e: Exception) {
            Logger.error("❌ Failed to save remediation file", project)
        }
    }

}