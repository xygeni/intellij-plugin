package com.github.xygeni.intellij.dynamichtml.editor

import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.RemediateService
import com.intellij.openapi.project.Project
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * RemediationActionsPanel — Swing counterpart of the Fix-it buttons (#1976): "Remediate with
 * Xygeni Agent" previews the fix, then "Save" applies it, driving RemediateService exactly like
 * EditorBrowserContext does for the browser buttons.
 **/
class RemediationActionsPanel(
    private val project: Project,
    private val remediationJson: String,
) : JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)) {

    private val remediateButton = JButton("Remediate with Xygeni Agent")
    private val saveButton = JButton("Save").apply { isVisible = false }

    init {
        add(remediateButton)
        add(saveButton)
        remediateButton.addActionListener { remediate() }
        saveButton.addActionListener { save() }
    }

    private fun remediate() {
        remediateButton.isEnabled = false
        remediateButton.text = "Processing..."
        try {
            project.getService(RemediateService::class.java).remediate(project, remediationJson) { success ->
                SwingUtilities.invokeLater {
                    if (success) {
                        remediateButton.isVisible = false
                        saveButton.isVisible = true
                    } else {
                        remediateButton.text = "❌ Remediation error"
                    }
                }
            }
        } catch (failure: Exception) {
            // remediate() validates the source file synchronously; the browser path catches this in the JS bridge.
            Logger.error("Remediation could not start", failure, project)
            remediateButton.text = "❌ Remediation error"
        }
    }

    private fun save() {
        saveButton.isVisible = false
        try {
            project.getService(RemediateService::class.java).save(project, remediationJson)
        } catch (failure: Exception) {
            Logger.error("Remediation could not be saved", failure, project)
            saveButton.isVisible = true
        }
    }
}
