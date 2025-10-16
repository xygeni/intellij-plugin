package com.github.xygeni.intellij.views

import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import javax.swing.JScrollPane

/**
 * LogView
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/
class LogView : JBPanel<LogView>() {
    val logArea = JBTextArea().apply {
        isEditable = false
        rows = 10
        lineWrap = true
    }
    init {
        layout = BorderLayout()
        add(JScrollPane(logArea), BorderLayout.CENTER)
    }

    fun appendLog(message: String) {
        logArea.append(message + "\n")
        logArea.caretPosition = logArea.document.length
    }
}