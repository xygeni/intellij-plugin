package com.github.xygeni.intellij.settings

/**
 * XygeniSettingsConfigurable
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/

import com.github.xygeni.intellij.events.SETTINGS_CHANGED_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.log

// XygeniSettingsConfigurable with a clean setting UI
class XygeniSettingsConfigurable(private val project: Project) : Configurable {

    private lateinit var apiUrlField: JBTextField
    private lateinit var tokenField: JBPasswordField
    private lateinit var autoScanField : JCheckBox
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "Xygeni Settings"

    override fun createComponent(): JComponent {

        apiUrlField = JBTextField()
        tokenField = JBPasswordField()
        autoScanField = JCheckBox("Scan project on save")

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Xygeni API URL:"), apiUrlField, 1, false)
            .addLabeledComponent(JBLabel("Access token:"), tokenField, 1, false)
            .addComponent(autoScanField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        mainPanel = form
        reset()
        return form
    }

    override fun isModified(): Boolean {
        val settings = XygeniSettings.getInstance()
        return apiUrlField.text != settings.apiUrl ||
                String(tokenField.password) != (settings.apiToken ?: "") ||
                autoScanField.isSelected != settings.autoScan
    }

    override fun apply() {
        val settings = XygeniSettings.getInstance()
        settings.apiUrl = apiUrlField.text.trim()
        settings.apiToken = String(tokenField.password).trim()
        settings.autoScan = autoScanField.isSelected
        Logger.log("Xygeni settings updated", project)
        this.project.messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingsChanged()
    }

    override fun reset() {
        val settings = XygeniSettings.getInstance()
        apiUrlField.text = settings.apiUrl
        tokenField.text = settings.apiToken ?: ""
        autoScanField.isSelected = settings.autoScan
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}