package com.github.xygeni.intellij.page

/**
 * DynamicHtmlEditorProvider
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class DynamicHtmlEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.name.endsWith(".dynamic.html")
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return DynamicHtmlFileEditor(file)
    }

    override fun getEditorTypeId(): String = "dynamic-html-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}



