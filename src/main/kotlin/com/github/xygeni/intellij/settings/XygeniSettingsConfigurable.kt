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
import com.intellij.openapi.ui.ComboBox
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
    private lateinit var allowInsecureHttpField: JCheckBox
    private lateinit var skipUpdateField: JCheckBox
    private lateinit var verboseField: JCheckBox
    private lateinit var additionalGlobalOptionsField: JBTextField
    private lateinit var mcpUpdateModeField: ComboBox<McpUpdateMode>
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
        allowInsecureHttpField = JCheckBox("Allow insecure HTTP (token sent in clear)")
        skipSslVerifyField = JCheckBox("Skip SSL verification (--skip-ssl-verify)")
        skipUpdateField = JCheckBox("Skip scanner update (--skip-update)")
        verboseField = JCheckBox("Verbose scanner output (--verbose)")
        additionalGlobalOptionsField = JBTextField().apply { emptyText.text = "e.g. -cop key=value" }

        // MCP server updates (xygeni/xygeni-product-backlog#2382): the published version is checked once a day.
        mcpUpdateModeField = ComboBox(McpUpdateMode.entries.toTypedArray())

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Xygeni API URL:"), apiUrlField, 1, false)
            .addLabeledComponent(JBLabel("Access token:"), tokenField, 1, false)
            .addComponent(allowInsecureHttpField)
            .addComponent(comment("Only for a Xygeni server without TLS: with an http URL to a host other than localhost " +
                "the API token travels in clear on every request. http://localhost is always allowed."))
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
            .addSeparator()
            .addComponent(JBLabel("MCP server"))
            .addLabeledComponent(JBLabel("MCP server updates:"), mcpUpdateModeField, 1, false)
            .addComponent(comment("Checked once a day after the connection is validated. \"Notify me\" offers Update / Ignore " +
                "this version; \"Update automatically\" installs it in the background. Tools > Xygeni > Check for MCP Server " +
                "Updates checks now, whatever the setting."))
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
                allowInsecureHttpField.isSelected != settings.allowInsecureHttp ||
                skipUpdateField.isSelected != settings.skipUpdate ||
                verboseField.isSelected != settings.verbose ||
                additionalGlobalOptionsField.text.trim() != settings.additionalGlobalOptions ||
                mcpUpdateModeField.selectedItem != settings.mcpUpdateMode
    }

    override fun apply() {
        val settings = XygeniSettings.getInstance()
        settings.apiUrl = apiUrlField.text.trim()
        settings.apiToken = String(tokenField.password).trim()
        val autoScanTurnedOn = autoScanField.isSelected && !settings.autoScan
        settings.autoScan = autoScanField.isSelected
        // Switched on with nothing chosen for on save: start from the default on-save types (#415).
        if (autoScanTurnedOn && com.github.xygeni.intellij.services.ScanConfig.load(project).onSave.isEmpty()) {
            com.github.xygeni.intellij.services.ScanConfig.save(project, com.github.xygeni.intellij.services.ScanConfig.load(project).withDefaultOnSave())
        }
        this.project.messageBus.syncPublisher(com.github.xygeni.intellij.events.SCAN_CONFIG_CHANGED_TOPIC).scanConfigChanged(project)
        settings.skipSslVerify = skipSslVerifyField.isSelected
        settings.allowInsecureHttp = allowInsecureHttpField.isSelected
        settings.skipUpdate = skipUpdateField.isSelected
        settings.verbose = verboseField.isSelected
        settings.additionalGlobalOptions = additionalGlobalOptionsField.text
        settings.mcpUpdateMode = mcpUpdateModeField.selectedItem as? McpUpdateMode ?: McpUpdateMode.NOTIFY
        Logger.log("Xygeni settings updated", project)
        this.project.messageBus.syncPublisher(SETTINGS_CHANGED_TOPIC).settingsChanged()
    }

    override fun reset() {
        val settings = XygeniSettings.getInstance()
        apiUrlField.text = settings.apiUrl
        tokenField.text = settings.apiToken ?: ""
        autoScanField.isSelected = settings.autoScan
        skipSslVerifyField.isSelected = settings.skipSslVerify
        allowInsecureHttpField.isSelected = settings.allowInsecureHttp
        skipUpdateField.isSelected = settings.skipUpdate
        verboseField.isSelected = settings.verbose
        additionalGlobalOptionsField.text = settings.additionalGlobalOptions
        mcpUpdateModeField.selectedItem = settings.mcpUpdateMode
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