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

    fun setConsoleView(consoleView: ConsoleView) {
        console = consoleView
    }

    fun print(message: String, type: ConsoleViewContentType = ConsoleViewContentType.NORMAL_OUTPUT) {
        console?.print(message + "\n", type)
    }

    fun printError(message: String) {
        print(message, ConsoleViewContentType.ERROR_OUTPUT)
    }

    fun clear() {
        console?.clear()
    }
}