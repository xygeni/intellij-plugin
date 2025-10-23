package com.github.xygeni.intellij.page

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * DynamicHtmlFileEditor
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/
class DynamicHtmlFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val panel = JPanel()
    private var browser: JBCefBrowser? = null
    private var initialized = false

    private fun initBrowser() {
        if (initialized) return
        initialized = true

        SwingUtilities.invokeLater {
            if (browser == null) {
                browser = JBCefBrowser()
                panel.add(browser!!.component)
                browser!!.loadHTML(String(file.contentsToByteArray()))
            }
        }
    }

    override fun getComponent(): JComponent {
        initBrowser()  // Inicializa el browser solo cuando se llama a getComponent()
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        initBrowser()
        return browser?.component ?: panel    }

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