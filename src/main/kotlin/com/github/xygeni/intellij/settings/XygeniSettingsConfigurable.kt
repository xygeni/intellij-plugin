package com.github.xygeni.intellij.settings

/**
 * XygeniSettingsConfigurable
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/

import com.github.xygeni.intellij.events.SETTINGS_CHANGED_TOPIC
import com.github.xygeni.intellij.model.PluginConfig
import com.github.xygeni.intellij.services.InstallerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

// XygeniSettingsConfigurable with a clean setting UI
class XygeniSettingsConfigurable(private val project: Project) : Configurable {

    private lateinit var apiUrlField: JBTextField
    private lateinit var tokenField: JBPasswordField
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "Xygeni Settings"

    override fun createComponent(): JComponent {

        apiUrlField = JBTextField()
        tokenField = JBPasswordField()

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Xygeni API URL:"), apiUrlField, 1, false)
            .addLabeledComponent(JBLabel("Access Token:"), tokenField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        mainPanel = form
        reset()
        return form
    }

    override fun isModified(): Boolean {
        val settings = XygeniSettings.getInstance()
        return apiUrlField.text != settings.apiUrl ||
                String(tokenField.password) != settings.apiToken
    }

    override fun apply() {
        val settings = XygeniSettings.getInstance()
        settings.apiUrl = apiUrlField.text.trim()
        settings.apiToken = String(tokenField.password).trim()
        this.project.messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingsChanged()
    }

    override fun reset() {
        val settings = XygeniSettings.getInstance()
        apiUrlField.text = settings.apiUrl
        tokenField.text = settings.apiToken
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}