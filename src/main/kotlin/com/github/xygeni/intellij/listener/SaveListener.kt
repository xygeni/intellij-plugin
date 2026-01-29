package com.github.xygeni.intellij.listener

/**
 * SaveListener
 *
 * @author : Carmendelope
 * @version : 27/1/26 (Carmendelope)
 **/

import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.ScanService
import com.github.xygeni.intellij.settings.XygeniSettings
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager

@Service
class SaveListener :  FileDocumentManagerListener{

    @Override
    override fun beforeDocumentSaving(document: Document) {

        if (! XygeniSettings.getInstance().autoScan) return

        val vf = FileDocumentManager.getInstance().getFile(document) ?: return;

        val project = ProjectManager.getInstance().openProjects
            .firstOrNull { proj -> FileEditorManager.getInstance(proj).isFileOpen(vf) } ?: return;

        project.getService(ScanService::class.java).scan(project, true)

    }
}