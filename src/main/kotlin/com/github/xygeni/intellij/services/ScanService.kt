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

    /** Scan types of the run in progress / just finished (the `--run` list). The report views
     *  reload only these: an incremental run must not wipe apisec/ai/deps findings it never re-scanned. */
    var lastRunScanTypes: Set<String> = emptySet()
        private set

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

        val path = project.basePath ?: // TODO: ver qué hacer aquí
        return
        scanning = true

        val scanArgs = this@ScanService.buildArgs(path, incremental)
        lastRunScanTypes = scanArgs["--run"].orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        // Only the scan types that run now can refresh their licence status: the incremental
        // scan skips apisec/ai, so clearing everything would turn "Not licensed" into "0 issues".
        _unlicensedScanTypes.removeAll(lastRunScanTypes)

        val scanResultDir = this.pluginContext.cleanScanResultDir(project)
        // Whole second: a file system with 1s mtime granularity must not date a fresh report before the start.
        val scanStartedAt = System.currentTimeMillis() / 1000 * 1000
        publishScanUpdate(project, 2) // running

        try {
            processHandle = executeProcess(
                path = pluginContext.xygeniCommand,
                args = scanArgs,
                envs = XygeniSettings.getInstance().toEnv(),
                workingDir = scanResultDir,
                project = project,
                onComplete = { success ->
                    if (_unlicensedScanTypes.isNotEmpty()) {
                        Logger.log("Not licensed for this account and skipped: ${_unlicensedScanTypes.sorted().joinToString(", ")}", project)
                    }
                    if (success) {
                        publishScanUpdate(project, 1) // Finished
                    } else {
                        publishScanUpdate(project, 0) // Finished with errors
                    }
                    scanning = false
                },
                onOutputLine = { line ->
                    unlicensedScanTypeIn(line)?.let { _unlicensedScanTypes.add(it) }
                },
                // Scanner convention, shared with the other plugins: 0 = clean scan, > 127 = completed
                // with findings (134). 127 = some scan types not licensed: still a completed scan when
                // the licensed ones wrote their report in this run; with no fresh report the licence
                // itself is missing or expired and the scan failed.
                isSuccessExitCode = { exitCode ->
                    exitCode == 0 || exitCode > 127 || (exitCode == 127 && hasReportWrittenSince(scanResultDir, scanStartedAt))
                }
            )
        } catch (e: Exception) {
            Logger.error("error in scanning process ", e)
            publishScanUpdate(project, 0) // Finished with errors
            scanning = false
        }
    }

    /**
     * The `--run` scan type a CLI output line refuses for licensing, or null. `isScanAllowed` logs the
     * ScanType name ("apisec feature is not licensed"); AI is gated by its subscription feature instead
     * ("aiSecurity feature is not licensed" / "AI Security scan is not enabled by the active license"),
     * and Quality is bundled into SAST with a sentence of its own (SastCommand / AiSecurityCommand).
     */
    private fun unlicensedScanTypeIn(line: String): String? {
        notLicensedLine.find(line)?.groupValues?.get(1)?.let { refused ->
            return if (refused == "aiSecurity") "ai" else refused.lowercase()
        }
        if (line.contains("AI Security scan is not enabled by the active license")) return "ai"
        if (line.contains("Code Quality is not allowed by the current license")) return "quality"
        return null
    }

    private fun hasReportWrittenSince(reportDir: java.io.File, startedAtMillis: Long): Boolean =
        reportDir.listFiles { file -> file.name.endsWith(pluginContext.xygeniReportSuffix) }
            ?.any { report -> report.lastModified() >= startedAtMillis } == true

    fun stop(project : Project){
        val running = processHandle?.isRunning() ?: false
        if (running){
            processHandle?.stop(project)
        }
        processHandle = null
    }

    fun publishScanUpdate(project: Project, status: Int) {
        // Deliver synchronously when already on the EDT: a save queued right behind a finished
        // full scan must see `scanning = false` only AFTER the views consumed this event with the
        // full run's `lastRunScanTypes`, or its incremental run would overwrite them first.
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            application.messageBus.syncPublisher(SCAN_STATE_TOPIC).scanStateChanged(project, status)
            return
        }
        application.invokeLater {
            application.messageBus
                .syncPublisher(SCAN_STATE_TOPIC)
                .scanStateChanged(project, status)
        }
    }

}
