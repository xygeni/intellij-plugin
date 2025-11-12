package com.github.xygeni.intellij.services

/**
 * ConsoleService
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
class ConsoleService {

    private var console: ConsoleView? = null

    private val logBuffer = mutableListOf<Pair<String, ConsoleViewContentType>>()
    private val maxBufferSize = 1000

    fun setConsoleView(consoleView: ConsoleView) {
        console = consoleView

        for ((message, type) in logBuffer) {
            console?.print(message + "\n", type)
        }
        logBuffer.clear()
    }

    fun print(message: String, type: ConsoleViewContentType = ConsoleViewContentType.NORMAL_OUTPUT) {
        logBuffer.add(message to type)

        if (logBuffer.size > maxBufferSize) {
            logBuffer.subList(0, logBuffer.size - maxBufferSize).clear()
        }

        console?.print(message + "\n", type)
    }

    fun printError(message: String) {
        print(message, ConsoleViewContentType.ERROR_OUTPUT)
    }

    fun clear() {
        console?.clear()
        logBuffer.clear()
    }
}