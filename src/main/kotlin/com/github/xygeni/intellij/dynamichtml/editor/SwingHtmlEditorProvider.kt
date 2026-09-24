package com.github.xygeni.intellij.dynamichtml.editor

import com.github.xygeni.intellij.dynamichtml.browser.JcefSupport
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

/**
 * SwingHtmlEditorProvider — mirror of [DynamicHtmlEditorProvider] for IDEs without JCEF (#1976):
 * exactly one of the two accepts `*.dynamic.html`, so the platform never falls back to showing
 * the raw markup in a text editor.
 **/
class SwingHtmlEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".dynamic.html") && !JcefSupport.isAvailable

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val editor = SwingHtmlFileEditor(project, file)
        if (file is LightVirtualFile) {
            editor.loadHtml(file.content.toString())
        }
        return editor
    }

    override fun getEditorTypeId(): String = "xygeni-swing-html-editor"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
