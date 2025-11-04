package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.JsonConfig
import com.github.xygeni.intellij.model.report.cicd.CicdReport
import com.github.xygeni.intellij.model.report.server.RemediationData
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.execution.target.value.targetPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.generator.nova.PredefinedType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * RemediateService
 *
 * @author : Carmendelope
 * @version : 30/10/25 (Carmendelope)
 **/
@Service(Service.Level.PROJECT)
class RemediateService : ProcessExecutorService() {

    private val manager = service<PluginManagerService>()
    private val pluginContext = manager.pluginContext
    private val executor = manager.executor

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
        val rightVf = vfs.refreshAndFindFileByPath(target.absolutePath)

        // 2.- lanzamos la remediacion en ese fichero
        executor.executeProcess(
            pluginContext.xygeniCommand,
            buildArgs(remediation, target.absolutePath),
            pluginContext.installDir,//targetDir,
            { success ->
                if (success) {
                    Logger.log("Remediation finished")
                    showDiff(project, remediation, target.absolutePath )
                } else {
                    Logger.log("Remediation failed")
                }
                onComplete(success)
            })
    }

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
                    "Remediation result",
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
    ){
        val remediation = JsonConfig.relaxed.decodeFromString<RemediationData>(data)
        val source = File(project.basePath, remediation.filePath)

        val target = getTempFile(project, remediation)
        target.copyTo(source, overwrite = true)
    }
}