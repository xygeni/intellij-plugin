package com.github.xygeni.intellij.services.report

import com.github.xygeni.intellij.events.READ_TOPIC
import com.github.xygeni.intellij.events.SCAN_STATE_TOPIC
import com.github.xygeni.intellij.events.ScanStateListener
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.PluginContext
import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.io.File

/**
 * BaseReportService
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/

abstract class BaseReportService<T : BaseXygeniIssue>(
    protected val project: Project,
    val reportType: String
) {

    private val pluginContext = PluginContext()
    private val _issues = mutableListOf<T>()
    val issues: List<T> get() = _issues

    init {
        project.messageBus.connect()
            .subscribe(SCAN_STATE_TOPIC, object : ScanStateListener {
                override fun scanStateChanged(project: Project?, status: Int) {
                    if (project != this@BaseReportService.project) return
                    if (status == 2) return cleanIssuesAndReturn()
                    read()
                }
            })
    }

    private fun cleanIssuesAndReturn(){
        this._issues.clear()
        ApplicationManager.getApplication().messageBus
            .syncPublisher(READ_TOPIC)
            .readCompleted(project, this@BaseReportService.reportType)
    }


    protected abstract fun processReport(jsonString: String): List<T>

    fun read() {
        val scanResultDir = this.pluginContext.ensureScanResultDirExists(project)
        val secretFile = File(
            scanResultDir,
            "${this.reportType}.${this.pluginContext.xygeniReportSuffix}"
        )

        this.readAndProcessFile(secretFile.absolutePath) { done, issues ->
            if (done) {
                ApplicationManager.getApplication().messageBus
                    .syncPublisher(READ_TOPIC)
                    .readCompleted(project, this@BaseReportService.reportType)
            } else {
                Logger.log("FAILED")
            }
        }
    }

    private fun readAndProcessFile(filename: String, callback: ReadIssuesCallback) {
        val file = File(filename)
        if (!file.exists()) {
            Logger.log("File $filename not found in ${project.basePath}")
            return callback.onComplete(true, emptyList())
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val jsonString = file.readText()
                val parsedIssues = processReport(jsonString)
                val sortedList = parsedIssues.sortedByDescending {
                    severityOrder[it.severity.lowercase()] ?: 0
                }
                _issues.addAll(sortedList)
                callback.onComplete(true, parsedIssues)
            } catch (e: Exception) {
                Logger.error("error processing file: ${e.message}", project)
                callback.onComplete(false, emptyList())
            }
        }
    }
}

val severityOrder = mapOf(
    "critical" to 4,
    "high" to 3,
    "low" to 2,
    "info" to 1
)