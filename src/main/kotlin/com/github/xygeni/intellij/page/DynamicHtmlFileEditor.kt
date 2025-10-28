package com.github.xygeni.intellij.page

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * DynamicHtmlFileEditor
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/
class DynamicHtmlFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val panel = JPanel(BorderLayout())
    private var browser: JBCefBrowser? = null
    private var initialized = false

    // pending data, render is called before initBrowser
    private var pendingData: String? = null

    private fun initBrowser() {
        if (initialized) return
        initialized = true

        SwingUtilities.invokeLater {
            if (browser == null) {
                browser = JBCefBrowser()

                val scrollPane = JScrollPane(browser!!.component).apply {
                    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                }

                panel.add(scrollPane, BorderLayout.CENTER)

                // Load initial HTML (html template)
                val html = String(file.contentsToByteArray())
                browser!!.loadHTML(html)

                // If there are pending data -> render them
                pendingData?.let {
                    renderData(it)
                    pendingData = null
                }
            }
        }
    }

    /**
     * Send the data to the HTM to render them
     * If the JS is not available -> wait 400 ms
     */
    fun renderData(json: String) {
        if (browser == null) {
            // HTML not ready, store the data and wait for it
            pendingData = json
            return
        }

        // Execute the render after a delay ro ensure Javascript is loaded
        Timer(400) {
            SwingUtilities.invokeLater {
                println("🔹 Ejecutando renderData con: $json")
                browser?.cefBrowser?.executeJavaScript(
                    "window.renderData($json);",
                    browser?.cefBrowser?.url,
                    0
                )
            }
        }.apply {
            isRepeats = false
            start()
        }
    }

    override fun getComponent(): JComponent {
        initBrowser()
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        initBrowser()
        return browser?.component ?: panel
    }

    override fun getName(): String = "HTML Dynamic Viewer"
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun dispose() {
        browser?.dispose()
    }

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun setState(state: FileEditorState) {}

    override fun getFile(): VirtualFile = file
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
}

