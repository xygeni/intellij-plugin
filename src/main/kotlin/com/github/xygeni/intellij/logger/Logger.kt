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

object Logger {

    private fun service(): ConsoleService? =
        ApplicationManager.getApplication()?.getService(ConsoleService::class.java)

    fun log(message: String) {
        service()?.print(cleanAnsi(message), ConsoleViewContentType.NORMAL_OUTPUT) ?: println("[INFO] $message")
    }

    fun warn(message: String) {
        service()?.print(cleanAnsi(message), ConsoleViewContentType.LOG_WARNING_OUTPUT) ?: println("[WARN] $message")
    }

    fun error(message: String, e: Throwable? = null) {
        val fullMessage = "$message\n${e?.message ?: ""}"
        service()?.printError(cleanAnsi(fullMessage)) ?: println("[ERROR] $fullMessage")
    }

    private fun cleanAnsi(message: String): String {
        return message.replace("\u001B\\[[;\\d]*m".toRegex(), "")
    }

    fun clear() {
        service()?.clear()
    }
}