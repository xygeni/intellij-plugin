package com.github.xygeni.intellij.settings

/**
 * XygeniSettingsConfigurable
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/

import com.github.xygeni.intellij.events.SETTINGS_CHANGED_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.LicenseService
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

// XygeniSettingsConfigurable with a clean setting UI
class XygeniSettingsConfigurable(private val project: Project) : Configurable {

    private lateinit var apiUrlField: JBTextField
    private lateinit var tokenField: JBPasswordField
    private lateinit var autoScanField : JCheckBox
    private lateinit var skipSslVerifyField: JCheckBox
    private lateinit var skipUpdateField: JCheckBox
    private lateinit var verboseField: JCheckBox
    private lateinit var additionalGlobalOptionsField: JBTextField
    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "Xygeni Settings"

    override fun createComponent(): JComponent {

        apiUrlField = JBTextField()
        tokenField = JBPasswordField()
        autoScanField = JCheckBox("Scan project on save")

        // Auto Scan on Save uses `--incremental`, rejected by the Free edition. On a Free license the
        // checkbox is disabled and an upgrade link to the pricing page is shown instead.
        val isFree = LicenseService.getInstance().isFreeLicense()
        autoScanField.isEnabled = !isFree
        val upgradeLink = ActionLink("Disabled on Free plan. Upgrade your plan") {
            BrowserUtil.browse(LicenseService.PRICING_URL)
        }.apply { isVisible = isFree }

        // Scanner global options, placed before the command: xygeni <options> scan ... (xygeni/tech-support#378)
        skipSslVerifyField = JCheckBox("Skip SSL verification (--skip-ssl-verify)")
        skipUpdateField = JCheckBox("Skip scanner update (--skip-update)")
        verboseField = JCheckBox("Verbose scanner output (--verbose)")
        additionalGlobalOptionsField = JBTextField().apply { emptyText.text = "e.g. -cop key=value" }

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Xygeni API URL:"), apiUrlField, 1, false)
            .addLabeledComponent(JBLabel("Access token:"), tokenField, 1, false)
            .addComponent(autoScanField)
            .addComponent(upgradeLink)
            .addSeparator()
            .addComponent(JBLabel("Scanner global options"))
            .addComponent(skipSslVerifyField)
            .addComponent(comment("Only when scans fail with certificate errors because a corporate proxy inspects TLS " +
                "traffic. This reduces transport security: use it only in trusted networks."))
            .addComponent(skipUpdateField)
            .addComponent(comment("Do not update the scanner before running it. You may run an outdated scanner version."))
            .addComponent(verboseField)
            .addLabeledComponent(JBLabel("Additional global options:"), additionalGlobalOptionsField, 1, false)
            .addComponent(comment("Advanced: placed before the scanner command, separated by spaces. Leave empty unless " +
                "Xygeni support asks for it. -q/--quiet (the plugin reads the scanner output) and --api-key (the token comes " +
                "from these settings) are ignored."))
            .addComponentFillVertically(JPanel(), 0)
            .panel
        mainPanel = form
        reset()
        return form
    }

    override fun isModified(): Boolean {
        val settings = XygeniSettings.getInstance()
        // Compare as saved: the setter drops a trailing slash, which must not keep Apply enabled.
        return XygeniSettings.normalizeApiUrl(apiUrlField.text) != settings.apiUrl ||
                String(tokenField.password) != (settings.apiToken ?: "") ||
                autoScanField.isSelected != settings.autoScan ||
                skipSslVerifyField.isSelected != settings.skipSslVerify ||
                skipUpdateField.isSelected != settings.skipUpdate ||
                verboseField.isSelected != settings.verbose ||
                additionalGlobalOptionsField.text.trim() != settings.additionalGlobalOptions
    }

    override fun apply() {
        val settings = XygeniSettings.getInstance()
        settings.apiUrl = apiUrlField.text.trim()
        settings.apiToken = String(tokenField.password).trim()
        settings.autoScan = autoScanField.isSelected
        settings.skipSslVerify = skipSslVerifyField.isSelected
        settings.skipUpdate = skipUpdateField.isSelected
        settings.verbose = verboseField.isSelected
        settings.additionalGlobalOptions = additionalGlobalOptionsField.text
        Logger.log("Xygeni settings updated", project)
        this.project.messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingsChanged()
    }

    override fun reset() {
        val settings = XygeniSettings.getInstance()
        apiUrlField.text = settings.apiUrl
        tokenField.text = settings.apiToken ?: ""
        autoScanField.isSelected = settings.autoScan
        skipSslVerifyField.isSelected = settings.skipSslVerify
        skipUpdateField.isSelected = settings.skipUpdate
        verboseField.isSelected = settings.verbose
        additionalGlobalOptionsField.text = settings.additionalGlobalOptions
    }

    private fun comment(text: String): JBLabel = JBLabel("<html>$text</html>").apply {
        componentStyle = UIUtil.ComponentStyle.SMALL
        fontColor = UIUtil.FontColor.BRIGHTER
        border = JBUI.Borders.emptyLeft(24)
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}