package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.events.SCAN_STATE_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.notifications.NotificationService
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
private val UNKNOWN_OPTION = Regex("Unknown options?: '([^']+)'")

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

    /** Whether the run in progress / just finished was a full scan: its skipped categories are cleared, not kept (#415). */
    var lastRunWasFull: Boolean = true
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

    /**
     * The `--run=` list comes from the project's `.xygeni/ide.json` (xygeni/xygeni-product-backlog#415), or is
     * [only] for a single scan type (the play button of a results view). The scanner options of the file go after
     * it, each canonical entry (`--name=value`) as one argument.
     */
    fun buildArgs(changingValue: String, config: ScanConfig, incremental: Boolean = false, only: List<String>? = null): Map<String, String> {
        val run = only?.takeIf { it.isNotEmpty() } ?: if (incremental) config.onSave else config.scans
        return baseArgs.toMutableMap().apply {
            this["--run"] = run.joinToString(",")
            this["-d"] = changingValue
            if (incremental) this["--incremental"] = ""
            config.excludeAddValue()?.let { this["--exclude-add"] = it }
            for (option in ScanOptions.normalize(config.options)) this[option] = ""
        }
    }

    /** The scan types plus the ones that run with them (SCA brings suspect dependencies), in CLI order. */
    fun withCompanions(types: List<String>): List<String> {
        val all = types.toMutableSet()
        for (scanner in ScanConfig.SCANNERS) {
            val parent = scanner.runsWith ?: continue
            if (parent in all) all.add(scanner.key)
        }
        return ScanConfig.ALL_SCAN_TYPES.filter { it in all }
    }

    fun isUnlicensed(scanType: String): Boolean = scanType in _unlicensedScanTypes

    /** The full scan, the on-save incremental scan, or with [only] just those scan types (their findings alone are refreshed). */
    fun scan(project: Project, incremental: Boolean = false, only: List<String>? = null) {
        if (scanning) {
            Logger.error("❌ Scanning is already running")
            return
        }

        val path = project.basePath ?: // TODO: ver qué hacer aquí
        return
        val config = ScanConfig.load(project)
        if (incremental && config.onSave.isEmpty()) {
            Logger.log("No scan type is configured to run on save (.xygeni/ide.json). Incremental scan skipped.", project)
            return
        }
        scanning = true

        val onlyTypes = only?.takeIf { it.isNotEmpty() }?.let { withCompanions(it) }
        val scanArgs = this@ScanService.buildArgs(path, config, incremental, onlyTypes)
        lastRunScanTypes = scanArgs["--run"].orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        // A single scan type keeps the findings of the others, like an incremental run does.
        lastRunWasFull = !incremental && onlyTypes == null
        // Only the scan types that run now can refresh their licence status: the incremental
        // scan skips apisec/ai, so clearing everything would turn "Not licensed" into "0 issues".
        _unlicensedScanTypes.removeAll(lastRunScanTypes)

        val scanResultDir = this.pluginContext.cleanScanResultDir(project)
        // Whole second: a file system with 1s mtime granularity must not date a fresh report before the start.
        val scanStartedAt = System.currentTimeMillis() / 1000 * 1000
        publishScanUpdate(project, 2) // running
        var unknownOption: String? = null // picocli: "Unknown option: '--x'" (exit code 2)

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
                        // An option the installed scanner does not know, typically one added in Scan configuration
                        // that needs a newer scanner: name it instead of the bare exit code (#415).
                        unknownOption?.let {
                            NotificationService.notifyError("The scanner does not accept $it (it may need scanner 6.23 or later): remove it from Scan configuration, or update the scanner", project)
                        }
                        publishScanUpdate(project, 0) // Finished with errors
                    }
                    scanning = false
                },
                onOutputLine = { line ->
                    unlicensedScanTypeIn(line)?.let { _unlicensedScanTypes.add(it) }
                    if (unknownOption == null) unknownOption = UNKNOWN_OPTION.find(line)?.groupValues?.get(1)
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
