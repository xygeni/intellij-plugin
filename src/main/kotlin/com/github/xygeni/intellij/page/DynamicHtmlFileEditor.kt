package com.github.xygeni.intellij.page

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

/**
 * DynamicHtmlFileEditor
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 *
 * A lightweight file editor that displays dynamic HTML content
 * inside a shared JCEF browser manage by [GlobalCefService]
 *
 * Responsibilities:
 * - Integrates the global browser into the IDE editor UI
 * - Loads and renders HTML pages (provided via LightVirtualFile)
 * - Sends data to the webview via [GlobalCefService.loadHtmlOnce]
 * - Associates the browser session with the current [Project]
 *
 * Notes:
 * - This editor does **not** own a JCEF instance; instead, it reuses the gloabl one
 * - The actual HTML rendering and JS <-> Kotlin bridge logic lives in [GlobalCefService]
 * - When multiple dynamic editors are opened, only one (the active one) updates the
 *   project context in the global service
 **/
/*
class DynamicHtmlFileEditor(
    private val project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(), FileEditor {

    // panel root swing panel that hosts the browser UI
    private val panel = JPanel(BorderLayout())
    // globalService references to the shared global browser service
    // private val globalService = project.getService(GlobalCefService::class.java)
    private val globalService by lazy {
        project.getService(GlobalCefService::class.java)
    }
    private var initialized = false

    private val fileId = file.nameWithoutExtension + "@" + System.identityHashCode(this)

    /*init {
        panel.layout = BorderLayout()
        panel.add(globalService.browser.component, BorderLayout.CENTER)

        SwingUtilities.invokeLater {
            val html = String(file.contentsToByteArray())
            println("Loading dynamic HTML for ${file.name} (id=$fileId)")
            globalService.loadHtmlOnce(html, project, fileId)
        }
    }*/

    private fun initIfNeeded() {
        if (initialized) return
        initialized = true

        val service = globalService

        panel.add(service.browser.component, BorderLayout.CENTER)

        // carga HTML ya cuando el editor está visible
        SwingUtilities.invokeLater {
            val html = String(file.contentsToByteArray())
            println("Loading dynamic HTML for ${file.name} (id=$fileId)")
            service.loadHtmlOnce(html, project, fileId)
        }
    }

    /**
     * renderData called by other plugin components (views)
     * to push data into the HTML page (via window.renderData())
     */
    fun renderData(json: String) {
        println("renderData for $fileId")
        globalService.renderData(json, fileId)
    }

    // -- FileEditor implementation --/
    override fun getComponent(): JComponent {
        initIfNeeded()
        return panel
    }
    override fun getPreferredFocusedComponent(): JComponent? = panel
    override fun getName(): String = "Dynamic HTML Viewer"
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun dispose() {
        panel.removeAll()
    }
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun setState(state: FileEditorState) {}
    override fun getFile(): VirtualFile = file
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
}
*/