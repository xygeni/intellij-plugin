package com.github.xygeni.intellij.dynamichtml.editor

/**
 * DynamicHtmlFileEditor
 *
 * @author : Carmendelope
 * @version : 14/11/25 (Carmendelope)
 **/
//import com.github.xygeni.intellij.dynamichtml.service.GlobalCefService
import com.github.xygeni.intellij.dynamichtml.browser.EditorBrowserContext
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent


class DynamicHtmlFileEditor(
    private val project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(), FileEditor {

    private val browserContext: EditorBrowserContext by lazy {
        EditorBrowserContext(project)
    }

    fun loadHtml(html: String) {
        browserContext.loadHtml(html)
    }

    fun renderData(json: String) {
        browserContext.renderData(json)
    }

    fun sendActionToKotlin(action: String, data: String) {
        browserContext.sendActionToKotlin(action, data)
    }

    override fun getComponent(): JComponent = browserContext.browserComponent
    override fun getPreferredFocusedComponent(): JComponent? = browserContext.browserComponent
    override fun getName(): String = "Dynamic HTML Viewer"
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun dispose() {
        browserContext.dispose()

    }

    override fun getFile(): VirtualFile = file
    override fun getState(level: FileEditorStateLevel) = FileEditorState.INSTANCE
    override fun setState(state: FileEditorState) {}
    override fun addPropertyChangeListener(listener: java.beans.PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: java.beans.PropertyChangeListener) {}
}