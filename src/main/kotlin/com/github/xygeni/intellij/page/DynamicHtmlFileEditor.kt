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
    private var triedRender = false

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
                // load HTML content
                val html = String(file.contentsToByteArray())
                browser!!.loadHTML(html)

                scheduleRenderPending()

            }
        }
    }

    private fun scheduleRenderPending() {
        Timer(400) {
            if (browser?.cefBrowser == null) {
                scheduleRenderPending()
                return@Timer
            }

            pendingData?.let {
                renderData(it)
                pendingData = null
                triedRender = true
            } ?: run {
                if (!triedRender) {
                    scheduleRenderPending()
                }
            }
        }.apply {
            isRepeats = false
            start()
        }
    }

    fun renderData(json: String) {
        if (!initialized || browser == null) {
            pendingData = json
            return
        }

        pendingData = json

        SwingUtilities.invokeLater {
            val js = """
                (function() {
                    function safeRender() {
                        if (window.renderData) {
                            window.renderData($json);
                        } else {
                            setTimeout(safeRender, 100);
                        }
                    }
                    safeRender();
                })();
            """.trimIndent()

            browser?.cefBrowser?.executeJavaScript(js, browser?.cefBrowser?.url, 0)
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

