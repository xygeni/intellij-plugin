package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.events.SCAN_STATE_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.PluginContext
import com.github.xygeni.intellij.settings.XygeniSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * ScanService
 *
 * @author : Carmendelope
 * @version : 9/10/25 (Carmendelope)
 **/

@Service(Service.Level.PROJECT)
class ScanService : ProcessExecutorService() {

    private val pluginContext = service<PluginContext>() 
    private var scanning = false

    /** Scan types (scanner `ScanType` names: apisec, ai, sast…) the scanner refused for licensing
     *  in the last run. The scanner only says so on stdout, so the line is captured here and the
     *  report views show "not licensed" instead of an empty node (#1976). */
    private val _unlicensedScanTypes: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val notLicensedLine = Regex("(\\w+) feature is not licensed")

    var processHandle: MyProcessHandle? = null

    private val baseArgs: Map<String, String> = mapOf(
        "scan" to "",
        "-f" to "json",
        "-d" to ".",
        "-o" to this.pluginContext.xygeniReportSuffix,
        "--no-upload" to "",
        "--include-vulnerabilities" to ""
    )

    private fun buildArgs(changingValue: String, incremental: Boolean = false): Map<String, String> {
        return if (!incremental) {
            baseArgs.toMutableMap().apply {
                this["--run"] = "deps,secrets,misconf,iac,suspectdeps,sast,malware,quality,apisec,ai"
                this["-d"] = changingValue
            }
        }else{
            baseArgs.toMutableMap().apply {
                this["-d"] = changingValue
                this["--run"] = "secrets,iac,sast,malware"
                this["--incremental"] = ""
            }
        }
    }

    fun isUnlicensed(scanType: String): Boolean = scanType in _unlicensedScanTypes

    fun scan(project: Project, incremental: Boolean = false) {
        if (scanning) {
            Logger.error("❌ Scanning is already running")
            return
        }

        scanning = true

        val path = project.basePath ?: // TODO: ver qué hacer aquí
        return

        val scanArgs = this@ScanService.buildArgs(path, incremental)
        // Only the scan types that run now can refresh their licence status: the incremental
        // scan skips apisec/ai, so clearing everything would turn "Not licensed" into "0 issues".
        _unlicensedScanTypes.removeAll(scanArgs["--run"].orEmpty().split(',').toSet())

        val scanResultDir = this.pluginContext.cleanScanResultDir(project)
        publishScanUpdate(project, 2) // running

        try {
            processHandle = executeProcess(
                path = pluginContext.xygeniCommand,
                args = scanArgs,
                envs = XygeniSettings.getInstance().toEnv(),
                workingDir = scanResultDir,
                project = project,
                onComplete = { success ->
                    if (success) {
                        publishScanUpdate(project, 1) // Finished
                    } else {
                        publishScanUpdate(project, 0) // Finished with errors
                    }
                    scanning = false
                },
                onOutputLine = { line ->
                    notLicensedLine.find(line)?.groupValues?.get(1)?.let { _unlicensedScanTypes.add(it.lowercase()) }
                },
                // Scanner convention, shared with the Eclipse / VS Code plugins: 0 = clean scan,
                // > 127 = scan completed with findings above threshold (134). 127 = licence error.
                isSuccessExitCode = { exitCode -> exitCode == 0 || exitCode > 127 }
            )
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
