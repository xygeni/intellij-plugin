package com.github.xygeni.intellij.views

/**
 * XygeniSettingsView
 *
 * @author : Carmendelope
 * @version : 8/10/25 (Carmendelope)
 **/

import com.github.xygeni.intellij.events.CONNECTION_STATE_TOPIC
import com.github.xygeni.intellij.events.ConnectionStateListener
import com.github.xygeni.intellij.events.LICENSE_STATE_TOPIC
import com.github.xygeni.intellij.events.LicenseStateListener
import com.github.xygeni.intellij.events.SETTINGS_CHANGED_TOPIC
import com.github.xygeni.intellij.events.SettingsChangeListener
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.InstallerService
import com.github.xygeni.intellij.services.LicenseService
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
    val autoScan: Boolean
)


class XygeniSettingsView(private val project: Project) : JPanel() {

    private lateinit var header: JLabel
    private lateinit var content: JPanel
    private lateinit var urlTextField: JBTextField
    private lateinit var tokenTextField: JBTextField
    private lateinit var statusLabel: JLabel
    private lateinit var autoScanCheck : JBCheckBox
    private lateinit var upgradeLink : ActionLink
    
    // Track last checked values to avoid redundant validations
    private var lastCheckedUrl: String? = null
    private var lastCheckedToken: String? = null

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        border = MatteBorder(0, 0, 1, 0, JBColor.GRAY)
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
        ApplicationManager.getApplication().getService(InstallerService::class.java)
            .getConnectionState()?.let { (urlOk, tokenOk) -> applyConnectionState(urlOk, tokenOk) }

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
    private fun applyConnectionState(urlOk: Boolean, tokenOk: Boolean) {
        ApplicationManager.getApplication().invokeLater {
            val valid = urlOk && tokenOk
            statusLabel.text = when {
                !urlOk -> "❌ Invalid URL"
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
        header = JLabel("CONFIGURATION").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            alignmentX = LEFT_ALIGNMENT
            icon = Icons.CHEVRON_RIGHT_ICON
            iconTextGap = 10
        }

        header.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                toggleContentVisibility()
            }
        })
        header.alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun createContent() {
        content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(5, 20, 10, 5)
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
                    statusLabel.text = "⏳ Checking connection status..."
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

        autoScanCheck = JBCheckBox("Scan project on save")
        autoScanCheck.addActionListener {
            val selected = autoScanCheck.isSelected
            XygeniSettings.getInstance().autoScan = selected
        }

        // Auto Scan on Save uses `--incremental`, rejected by the Free edition. On a Free license the
        // checkbox is disabled and this link opens the pricing page so the user can upgrade.
        upgradeLink = ActionLink("Disabled on Free plan. Upgrade your plan") {
            BrowserUtil.browse(LicenseService.PRICING_URL)
        }.apply {
            alignmentX = LEFT_ALIGNMENT
            isVisible = false
        }

        val formPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel("Xygeni API URL:"))
            add(Box.createVerticalStrut(2))
            add(urlTextField)
            add(Box.createVerticalStrut(8))
            add(JLabel("Access Token:"))
            add(Box.createVerticalStrut(2))
            add(tokenTextField)
            add(Box.createVerticalStrut(8))
            add(autoScanCheck)
            add(upgradeLink)
            add(Box.createVerticalStrut(8))
            add(statusLabel)
        }

        content.add(formPanel)

        applyLicenseState()
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
                            autoScan = settings.autoScan
                        )
                    }

                ApplicationManager.getApplication().invokeLater({
                    urlTextField.text = snapshot.apiUrl
                    tokenTextField.text = "•".repeat(snapshot.tokenLen)
                    autoScanCheck.isSelected = snapshot.autoScan
                    
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