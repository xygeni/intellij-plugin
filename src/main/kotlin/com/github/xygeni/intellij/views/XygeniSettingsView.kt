package com.github.xygeni.intellij.views

/**
 * XygeniSettingsView
 *
 * @author : Carmendelope
 * @version : 8/10/25 (Carmendelope)
 **/

import com.github.xygeni.intellij.events.CONNECTION_STATE_TOPIC
import com.github.xygeni.intellij.events.McpStateListener
import com.github.xygeni.intellij.events.MCP_STATE_TOPIC
import com.github.xygeni.intellij.events.ConnectionStateListener
import com.github.xygeni.intellij.events.LICENSE_STATE_TOPIC
import com.github.xygeni.intellij.events.LicenseStateListener
import com.github.xygeni.intellij.events.SCAN_CONFIG_CHANGED_TOPIC
import com.github.xygeni.intellij.events.ScanConfigChangeListener
import com.github.xygeni.intellij.events.SETTINGS_CHANGED_TOPIC
import com.github.xygeni.intellij.events.SettingsChangeListener
import com.github.xygeni.intellij.services.ScanConfig
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.InstallerService
import com.github.xygeni.intellij.services.LicenseService
import com.github.xygeni.intellij.settings.SkipSslVerifySuggestion
import com.github.xygeni.intellij.settings.XygeniSettings
import com.github.xygeni.intellij.settings.XygeniSettingsConfigurable
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import icons.Icons
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.MatteBorder

data class ApiSettingsSnapshot(
    val apiUrl: String,
    val tokenLen: Int,
    val autoScan: Boolean,
    val skipSslVerify: Boolean,
    val skipUpdate: Boolean,
    val verbose: Boolean
)


class XygeniSettingsView(private val project: Project) : JPanel() {

    private lateinit var header: JLabel
    private lateinit var content: JPanel
    private lateinit var urlTextField: JBTextField
    private lateinit var tokenTextField: JBTextField
    private lateinit var statusLabel: JLabel
    private lateinit var mcpStatusLabel: JLabel
    private lateinit var mcpRestartNote: JLabel
    private lateinit var mcpRestartRow: JPanel
    private var connecting = false
    private lateinit var autoScanCheck : JBCheckBox
    private lateinit var upgradeLink : ActionLink
    private lateinit var skipSslVerifyCheck: JBCheckBox
    private lateinit var skipUpdateCheck: JBCheckBox
    private lateinit var verboseCheck: JBCheckBox
    
    // Track last checked values to avoid redundant validations
    private var lastCheckedUrl: String? = null
    private var lastCheckedToken: String? = null

    /** The platform spinner next to "Connecting…" while a connection check is in flight. */
    private fun showConnecting() {
        connecting = true
        statusLabel.icon = com.intellij.ui.AnimatedIcon.Default()
        statusLabel.iconTextGap = 6
        statusLabel.text = "Connecting…"
        refreshMcpStatus()
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    fun initialize() {
        createHeader()
        createContent()
        add(header)
        add(Box.createVerticalStrut(4).apply { setAlignmentX(0f) })
        add(content)

        loadSettingsAsync(false)

        // Apply the cached connection state so the panel reflects the startup validation even if it
        // was published before this (lazily created) view subscribed to CONNECTION_STATE_TOPIC.
        // With no result yet the startup check is still running: say so instead of "Unknown".
        val cachedState = ApplicationManager.getApplication().getService(InstallerService::class.java).getConnectionState()
        if (cachedState != null) applyConnectionState(cachedState.first, cachedState.second) else showConnecting()

        project.messageBus.connect()
            .subscribe(SCAN_CONFIG_CHANGED_TOPIC, object : ScanConfigChangeListener {
                override fun scanConfigChanged(project: Project?) {
                    ApplicationManager.getApplication().invokeLater { autoScanCheck.isSelected = XygeniSettings.getInstance().autoScan }
                }
            })

        project.messageBus.connect()
            .subscribe(SETTINGS_CHANGED_TOPIC, object : SettingsChangeListener {
                override fun settingsChanged() {
                    loadSettingsAsync(reinstall =  true)
                }
            })

        project.messageBus.connect()
            .subscribe(CONNECTION_STATE_TOPIC, object : ConnectionStateListener {
                override fun connectionStateChanged(project: Project?, urlOk: Boolean, tokenOk: Boolean) {
                    if (project != this@XygeniSettingsView.project) return
                    applyConnectionState(urlOk, tokenOk)
                }
            })

        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(MCP_STATE_TOPIC, object : McpStateListener {
                override fun mcpStateChanged() {
                    ApplicationManager.getApplication().invokeLater { refreshMcpStatus() }
                }
            })

        // The license plan (Free vs paid) is resolved asynchronously after the seat is registered;
        // refresh the Auto Scan gating once it lands.
        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(LICENSE_STATE_TOPIC, object : LicenseStateListener {
                override fun licenseStateChanged(project: Project?, valid: Boolean) {
                    applyLicenseState()
                }
            })
    }

    /** Updates the status label and collapses CONFIGURATION when valid / expands it when invalid. */
    /**
     * The MCP server line (#2382): "Installed" from the JAR on disk, plus what the last check found; "Checking…" while
     * the connection check (which installs it) runs; and the note after an update until the user says OK.
     */
    private fun refreshMcpStatus() {
        if (!::mcpStatusLabel.isInitialized) return
        val installer = ApplicationManager.getApplication().getService(InstallerService::class.java)
        val settings = XygeniSettings.getInstance()
        val installed = installer.isInstalled(mcp = true)
        val check = installer.lastMcpUpdateCheck
        val pending = settings.mcpRestartPending
        mcpStatusLabel.icon = if (!installed && connecting) com.intellij.ui.AnimatedIcon.Default() else null
        mcpStatusLabel.iconTextGap = 6
        mcpStatusLabel.text = when {
            installed -> (installer.installedMcpVersion() ?: "Installed") +
                (when {
                    check == null -> ""
                    check.updateAvailable -> " → ${check.available} available"
                    else -> " · up to date"
                }) + (if (pending != null) " · restart your AI assistant" else "")
            connecting -> "Checking…"
            else -> "Not installed yet"
        }
        mcpRestartNote.text = if (pending != null) "Updated to $pending. Restart your AI assistant (Copilot, Claude Code, Cursor…) so it uses the new version." else ""
        mcpRestartNote.isVisible = pending != null
        mcpRestartNote.parent?.isVisible = pending != null
        mcpRestartRow.isVisible = pending != null
        mcpRestartRow.parent?.isVisible = pending != null
        refreshLayout()
    }

    private fun applyConnectionState(urlOk: Boolean, tokenOk: Boolean) {
        connecting = false
        refreshMcpStatus()
        ApplicationManager.getApplication().invokeLater {
            statusLabel.icon = null
            val valid = urlOk && tokenOk
            val serverProblem = ApplicationManager.getApplication().getService(InstallerService::class.java).lastConnectionProblem
            statusLabel.text = when {
                !urlOk -> "❌ Invalid URL"
                !tokenOk && serverProblem != null -> "⚠️ $serverProblem. Click to retry"
                !tokenOk -> "❌ Invalid token"
                else -> "✅ Valid Connection and Token"
            }
            // Invalid → reveal the form so the user can fix it; valid → collapse it to surface SCAN.
            if (!valid && !content.isVisible) {
                toggleContentVisibility()
            } else if (valid && content.isVisible) {
                toggleContentVisibility()
            }
        }
    }

    private fun createHeader() {
        header = Sections.subHeader("Connection") { toggleContentVisibility() }
    }

    private fun createContent() {
        content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(2, Sections.BODY_LEFT, 6, 6)
            isVisible = false
        }

        urlTextField = createField()
        tokenTextField = createField()
        statusLabel = JLabel("Connection Status => Unknown ").apply {
            alignmentX = LEFT_ALIGNMENT
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Click to check the connection status"

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    showConnecting()
                    triggerConnectionCheck(force = true)
                }
            })
        }

        listOf(urlTextField, tokenTextField).forEach { field ->
            field.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        project,
                        XygeniSettingsConfigurable::class.java
                    )
                }
            })
        }

        autoScanCheck = JBCheckBox("Scan project on save").apply { border = JBUI.Borders.empty() }
        autoScanCheck.addActionListener {
            val selected = autoScanCheck.isSelected
            XygeniSettings.getInstance().autoScan = selected
            // Switched on with nothing chosen for on save: start from the default on-save types (#415).
            if (selected && ScanConfig.load(project).onSave.isEmpty()) {
                ScanConfig.save(project, ScanConfig.load(project).withDefaultOnSave())
            }
            project.messageBus.syncPublisher(SCAN_CONFIG_CHANGED_TOPIC).scanConfigChanged(project)
        }

        // Auto Scan on Save uses `--incremental`, rejected by the Free edition. On a Free license the
        // checkbox is disabled and this link opens the pricing page so the user can upgrade.
        upgradeLink = ActionLink("Disabled on Free plan. Upgrade your plan") {
            BrowserUtil.browse(LicenseService.PRICING_URL)
        }.apply {
            alignmentX = LEFT_ALIGNMENT
            isVisible = false
        }

        // Scanner global options (xygeni/tech-support#378). Skip SSL verification is always offered (the corporate
        // TLS proxy case); the others show up only while they are on, so what changes the scanner is visible and one
        // click away from being turned off. All of them are in the settings dialog.
        skipSslVerifyCheck = JBCheckBox("Skip SSL verification").apply {
            border = JBUI.Borders.empty()
            toolTipText = "Runs the scanner with --skip-ssl-verify, for corporate proxies that inspect TLS traffic"
            addActionListener {
                if (isSelected) {
                    isSelected = SkipSslVerifySuggestion.confirmAndEnable(project)
                } else {
                    XygeniSettings.getInstance().skipSslVerify = false
                }
            }
        }
        skipUpdateCheck = enabledOnlyOptionCheck("Skip scanner update (--skip-update)") {
            XygeniSettings.getInstance().skipUpdate = false
        }
        verboseCheck = enabledOnlyOptionCheck("Verbose scanner output (--verbose)") {
            XygeniSettings.getInstance().verbose = false
        }

        // MCP server line, links and the note shown after an update (#2382).
        mcpStatusLabel = JLabel("Not installed yet").apply { alignmentX = LEFT_ALIGNMENT }
        val mcpRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            add(JLabel("MCP server: "))
            add(mcpStatusLabel)
            add(Box.createHorizontalGlue())
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        val mcpLinks = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            add(ActionLink("MCP Setup") { McpSetupView.show(project) })
            add(Box.createHorizontalStrut(JBUI.scale(10)))
            add(ActionLink("Check for updates") { ApplicationManager.getApplication().getService(InstallerService::class.java).checkMcpUpdate(project, force = true) })
            add(Box.createHorizontalGlue())
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        mcpRestartNote = Sections.description("").apply { foreground = JBColor.namedColor("Label.errorForeground", JBColor.RED) }
        mcpRestartRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false
            add(ActionLink("Show me how") { McpSetupView.show(project) })
            add(Box.createHorizontalStrut(JBUI.scale(10)))
            add(ActionLink("OK") { ApplicationManager.getApplication().getService(InstallerService::class.java).dismissMcpRestart() })
            add(Box.createHorizontalGlue())
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            isVisible = false
        }

        val formPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(Sections.flush(JLabel("Xygeni API URL:")))
            add(Box.createVerticalStrut(2))
            add(urlTextField)
            add(Box.createVerticalStrut(8))
            add(Sections.flush(JLabel("Access Token:")))
            add(Box.createVerticalStrut(2))
            add(tokenTextField)
            add(Box.createVerticalStrut(8))
            add(autoScanCheck)
            add(Sections.flush(upgradeLink))
            add(skipSslVerifyCheck)
            add(skipUpdateCheck)
            add(verboseCheck)
            add(Box.createVerticalStrut(8))
            add(Sections.flush(statusLabel))
            add(Box.createVerticalStrut(8))
            add(Sections.flush(mcpRow))
            add(Box.createVerticalStrut(2))
            add(Sections.flush(mcpLinks))
            add(Sections.flush(mcpRestartNote))
            add(Sections.flush(mcpRestartRow))
        }

        content.add(formPanel)

        applyLicenseState()
        refreshMcpStatus()
    }

    /** Disables Auto Scan on Save and reveals the upgrade link when the installed license is Free. */
    private fun applyLicenseState() {
        ApplicationManager.getApplication().invokeLater {
            val free = LicenseService.getInstance().isFreeLicense()
            autoScanCheck.isEnabled = !free
            upgradeLink.isVisible = free
        }
    }

    private fun triggerConnectionCheck(reinstall: Boolean = false, force: Boolean = false) {
        val settings = XygeniSettings.getInstance()
        val apiUrl = settings.apiUrl
        val token = settings.apiToken ?: ""

        // Check if values actually changed
        val urlChanged = apiUrl != lastCheckedUrl
        val tokenChanged = token != lastCheckedToken

        // Only check if forced or if URL/token have changed
        if (!force && !urlChanged && !tokenChanged) {
            Logger.log("Skipping connection check - URL and token unchanged", project)
            return
        }

        // Update tracked values
        lastCheckedUrl = apiUrl
        lastCheckedToken = token

        // Llamamos al servicio global para validar
        val installer = ApplicationManager.getApplication().getService(InstallerService::class.java)
        installer.validateConnection(apiUrl, token, project) { urlOk, tokenOk ->
            // Publicamos el resultado al MessageBus global
            installer.publishConnectionState(project, urlOk, tokenOk)
            // Registramos la licencia IDE en cuanto la conexión es válida, igual que en el arranque,
            // para que el botón Run Scan se habilite sin reiniciar al configurar el token. register()
            // ya despacha su trabajo de red fuera del EDT.
            if (urlOk && tokenOk) {
                LicenseService.getInstance().register(project)
            }
            // Only reinstall if URL/token changed AND reinstall was requested AND validation passed
            if (reinstall && (urlChanged || tokenChanged) && urlOk && tokenOk) {
                Logger.log("Reinstalling scanner due to URL/token change", project)
                installer.installOrUpdate(project)
            }
        }
    }

    /** A global option shown only while it is on: unchecking turns it off and hides it again. */
    private fun enabledOnlyOptionCheck(text: String, turnOff: () -> Unit): JBCheckBox = JBCheckBox(text).apply {
        border = JBUI.Borders.empty()
        isVisible = false
        addActionListener {
            if (!isSelected) {
                turnOff()
                isVisible = false
                refreshLayout()
            }
        }
    }

    private fun createField(): JBTextField = JBTextField().apply {
        isEditable = false
        isFocusable = false
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private fun toggleContentVisibility() {
        content.isVisible = !content.isVisible
        header.icon = if (content.isVisible) Icons.CHEVRON_DOWN_ICON else Icons.CHEVRON_RIGHT_ICON
        refreshLayout()
    }

    /** The panel is capped at its preferred height: recompute it whenever rows are shown or hidden. */
    private fun refreshLayout() {
        revalidate()
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        repaint()
    }

    private fun loadSettingsAsync(check: Boolean = true, reinstall: Boolean = false) {
        ProgressManager.getInstance().run(object :
            Task.Backgroundable(project, "Loading Xygeni Settings", false) {

            override fun run(indicator: ProgressIndicator) {
                val settings = XygeniSettings.getInstance()

                val snapshot: ApiSettingsSnapshot =
                    runReadAction {
                        ApiSettingsSnapshot(
                            apiUrl = settings.apiUrl,
                            tokenLen = settings.apiToken.length,
                            autoScan = settings.autoScan,
                            skipSslVerify = settings.skipSslVerify,
                            skipUpdate = settings.skipUpdate,
                            verbose = settings.verbose
                        )
                    }

                ApplicationManager.getApplication().invokeLater({
                    urlTextField.text = snapshot.apiUrl
                    tokenTextField.text = "•".repeat(snapshot.tokenLen)
                    autoScanCheck.isSelected = snapshot.autoScan
                    skipSslVerifyCheck.isSelected = snapshot.skipSslVerify
                    skipUpdateCheck.isSelected = snapshot.skipUpdate
                    skipUpdateCheck.isVisible = snapshot.skipUpdate
                    verboseCheck.isSelected = snapshot.verbose
                    verboseCheck.isVisible = snapshot.verbose
                    refreshLayout()
                    
                    // Initialize tracking values on first load to avoid unnecessary checks
                    if (lastCheckedUrl == null && lastCheckedToken == null) {
                        lastCheckedUrl = snapshot.apiUrl
                        lastCheckedToken = settings.apiToken
                    }
                }, project.disposed)

                if (check) {
                    triggerConnectionCheck(reinstall)
                }
            }
        })
    }

}