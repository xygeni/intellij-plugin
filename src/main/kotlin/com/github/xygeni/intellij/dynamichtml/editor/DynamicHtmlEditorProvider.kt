package com.github.xygeni.intellij.dynamichtml.editor

/**
 * DynamicHtmlEditorProvider
 *
 * @author : Carmendelope
 * @version : 14/11/25 (Carmendelope)
 **/
import com.github.xygeni.intellij.dynamichtml.browser.EditorBrowserContext
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile


class DynamicHtmlEditorProvider : FileEditorProvider, DumbAware {
    companion object {
        private val CONTEXT_KEY = Key.create<EditorBrowserContext>("xygeni.browser.context")
    }

    override fun accept(project: Project, file: VirtualFile): Boolean = file.name.endsWith(".dynamic.html")

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val context = file.getUserData(CONTEXT_KEY) ?: EditorBrowserContext(project).also {
            file.putUserData(CONTEXT_KEY, it)
        }

        val editor = DynamicHtmlFileEditor(project, file, context)

        // Cargar HTML inicial si el archivo lo tiene
        if (file is LightVirtualFile) {
            context.loadHtml(file.content.toString())
        }

        return editor
    }

    override fun getEditorTypeId(): String = "dynamic-html-editor"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    override fun disposeEditor(editor: FileEditor) {
        val file = (editor as? DynamicHtmlFileEditor)?.file ?: return
        val ctx = file.getUserData(CONTEXT_KEY) ?: return

        if (!FileEditorManager.getInstance(editor.project).isFileOpen(file)) {
            ctx.dispose()
            file.putUserData(CONTEXT_KEY, null)
        }
    }
}