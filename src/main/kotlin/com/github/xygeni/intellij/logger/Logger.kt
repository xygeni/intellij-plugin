package com.github.xygeni.intellij.logger

/**
 * Logger
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/
import com.github.xygeni.intellij.services.ConsoleService
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

object Logger {

    private fun service(project: Project?): ConsoleService? =
        project?.getService(ConsoleService::class.java)
        //ApplicationManager.getApplication()?.getService(ConsoleService::class.java)

    fun log(message: String, project: Project? = null) {
        if (project != null){
            service(project)?.print(cleanAnsi(message), ConsoleViewContentType.NORMAL_OUTPUT)
        }
        println("[INFO] $message")
    }

    fun warn(message: String, project: Project? = null) {
        if (project != null ) {
            service(project)?.print(cleanAnsi(message), ConsoleViewContentType.LOG_WARNING_OUTPUT)
        }
        println("[WARN] $message")
    }

    fun error(message: String, e: Throwable? = null, project: Project? = null) {
        val fullMessage = "$message\n${e?.message ?: ""}"
        if (project != null) {
            service(project)?.printError(cleanAnsi(fullMessage))
        }
        println("[ERROR] $fullMessage")
    }

    fun error(message: String,  project: Project? = null) {
        if (project != null) {
            service(project)?.print(cleanAnsi(message), ConsoleViewContentType.LOG_ERROR_OUTPUT)
        }
        println("[ERROR] $message")
    }

    private fun cleanAnsi(message: String): String {
        return message.replace("\u001B\\[[;\\d]*m".toRegex(), "")
    }

    fun clear(project: Project? = null) {
        service(project)?.clear()
    }
}