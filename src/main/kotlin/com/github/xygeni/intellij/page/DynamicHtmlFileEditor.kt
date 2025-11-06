package com.github.xygeni.intellij.page

/**
 * DynamicHtmlFileEditor
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class DynamicHtmlFileEditor(
    private val project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(), FileEditor {

    private val panel = JPanel(BorderLayout())
    private val globalService = ApplicationManager.getApplication().getService(GlobalCefService::class.java)
    private val fileId = file.nameWithoutExtension + "@" + System.identityHashCode(this)

    init {
        panel.layout = BorderLayout()
        panel.add(globalService.browser.component, BorderLayout.CENTER)

        SwingUtilities.invokeLater {
            val html = String(file.contentsToByteArray())
            println("Loading dynamic HTML for ${file.name} (id=$fileId)")
            globalService.loadHtmlOnce(html, project, fileId)
        }
    }


    fun renderData(json: String) {
        println("renderData for $fileId")
        globalService.renderData(json, fileId)
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent? = panel
    override fun getName(): String = "Dynamic HTML Viewer"
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun dispose() {}
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun setState(state: FileEditorState) {}
    override fun getFile(): VirtualFile = file
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
}
