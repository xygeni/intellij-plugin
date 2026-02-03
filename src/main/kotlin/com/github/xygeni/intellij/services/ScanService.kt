package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.events.SCAN_STATE_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.PluginContext
import com.github.xygeni.intellij.settings.XygeniSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.gradle.work.Incremental

/**
 * ScanService
 *
 * @author : Carmendelope
 * @version : 9/10/25 (Carmendelope)
 **/

@Service(Service.Level.PROJECT)
class ScanService : ProcessExecutorService() {

    private val pluginContext = PluginContext() // manager.pluginContext
    private var scanning = false

    var processHandle: MyProcessHandle? = null

    private val baseArgs: Map<String, String> = mapOf(
        "scan" to "",
//        "--run" to "deps,secrets,misconf,iac,suspectdeps,sast",
        "-f" to "json",
        "-d" to ".",
        "-o" to this.pluginContext.xygeniReportSuffix,
        "--no-upload" to "",
        "--include-vulnerabilities" to ""
    )

    private fun buildArgs(changingValue: String, incremental: Boolean = false): Map<String, String> {
        return if (!incremental) {
            baseArgs.toMutableMap().apply {
                this["--run"] = "deps,secrets,misconf,iac,suspectdeps,sast"
                this["-d"] = changingValue
            }
        }else{
            baseArgs.toMutableMap().apply {
                this["-d"] = changingValue
                this["--run"] = "deps,secrets,iac,suspectdeps,sast"
                this["--incremental"] = ""
            }
        }
    }

    fun scan(project: Project, incremental: Boolean = false) {
        if (scanning) {
            Logger.error("❌ Scanning is already running")
            return
        }

        scanning = true

        val path = project.basePath ?: // TODO: ver qué hacer aquí
        return

        val scanResultDir = this.pluginContext.cleanScanResultDir(project)
        publishScanUpdate(project, 2) // running

        try {
            processHandle = executeProcess(
                pluginContext.xygeniCommand,
                this@ScanService.buildArgs(path, incremental),
                XygeniSettings.getInstance().toEnv(),
                scanResultDir,
                project,
            ) { success ->
                if (success) {
                    publishScanUpdate(project, 1) // Finished
                } else {
                    publishScanUpdate(project, 0) // Finished with errors
                }
                scanning = false
            }
        } catch (e: Exception) {
            Logger.error("error in scanning process ", e)
            publishScanUpdate(project, 0) // Finished with errors
        }
    }

    fun stop(project : Project){
        val running = processHandle?.isRunning() ?: false
        if (running){
            processHandle?.stop(project)
        }
        processHandle = null
    }

    fun publishScanUpdate(project: Project, status: Int) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().messageBus
                .syncPublisher(SCAN_STATE_TOPIC)
                .scanStateChanged(project, status)
        }
    }

}
