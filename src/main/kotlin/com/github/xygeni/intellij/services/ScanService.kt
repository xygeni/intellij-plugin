package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.events.SCAN_STATE_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * ScanService
 *
 * @author : Carmendelope
 * @version : 9/10/25 (Carmendelope)
 **/

@Service(Service.Level.PROJECT)
class ScanService : ProcessExecutorService() {

    private val manager = service<PluginManagerService>()
    private val pluginContext = manager.pluginContext
    private val executor = manager.executor

    private var scanning = false

    private val baseArgs: Map<String, String> = mapOf(
        "scan" to "",
        "--run" to "deps,secrets,misconf,iac,suspectdeps,sast",
        "-f" to "json",
        "-d" to ".",
        "-o" to this.pluginContext.xygeniReportSuffix,
        "--no-upload" to "",
        "--include-vulnerabilities" to ""
    )

    private fun buildArgs(changingValue: String): Map<String, String> {
        return baseArgs.toMutableMap().apply {
            this["-d"] = changingValue
        }
    }

    fun scan(project: Project) {
        if (scanning) {
            Logger.error("❌ Scanning is already running")
            return
        }

        scanning = true

        val path = project.basePath ?: // TODO: ver qué hacer aquí
        return

        val scanResultDir = this.pluginContext.ensureScanResultDirExists(project)
        publishScanUpdate(project, 2) // running

        executor.executeProcess(
            pluginContext.xygeniCommand,
            this.buildArgs(path), scanResultDir
        ) { success ->
            if (success) {
                publishScanUpdate(project, 1) // Finished
            } else {
                publishScanUpdate(project, 0) // Finished with errors
            }
            scanning = false
        }
    }

    fun publishScanUpdate(project: Project, status: Int) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().messageBus
                .syncPublisher(SCAN_STATE_TOPIC)
                .scanStateChanged(project, status)
        }
    }

}
