package com.github.xygeni.intellij.views

/**
 * XygeniSettingsView
 *
 * @author : Carmendelope
 * @version : 8/10/25 (Carmendelope)
 **/

import com.github.xygeni.intellij.events.*
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.InstallerService
import com.github.xygeni.intellij.settings.XygeniSettings
import com.github.xygeni.intellij.settings.XygeniSettingsConfigurable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import icons.Icons
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
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

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = MatteBorder(0, 0, 1, 0, JBColor.GRAY)
    }

    fun initialize() {
        createHeader()
        createContent()
        add(header)
        add(Box.createVerticalStrut(4))
        add(content)

        loadSettingsAsync(true)

        project.messageBus.connect()
            .subscribe(SETTINGS_CHANGED_TOPIC, object : SettingsChangeListener {
                override fun settingsChanged() {
                    loadSettingsAsync()
                }
            })

        project.messageBus.connect()
            .subscribe(CONNECTION_STATE_TOPIC, object : ConnectionStateListener {
                override fun connectionStateChanged(project: Project?, urlOk: Boolean, tokenOk: Boolean) {
                    if (project != this@XygeniSettingsView.project) return

                    ApplicationManager.getApplication().invokeLater {
                        statusLabel.text = when {
                            !urlOk -> "❌ Invalid URL"
                            !tokenOk -> "❌ Invalid token"
                            else -> "✅ Valid Connection and Token"
                        }
                        if ((!urlOk || !tokenOk) && !content.isVisible) {
                            toggleContentVisibility()
                        }
                    }
                }
            })
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
    }

    private fun createContent() {
        content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
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
                    triggerConnectionCheck()
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

        autoScanCheck = JBCheckBox("Scan project on save")//.apply { isEnabled = false }
        autoScanCheck.addActionListener {
            val selected = autoScanCheck.isSelected
            XygeniSettings.getInstance().autoScan = selected
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
            add(Box.createVerticalStrut(8))
            add(statusLabel)
        }

        content.add(formPanel)
    }

    private fun triggerConnectionCheck() {
        val settings = XygeniSettings.getInstance()
        val apiUrl = settings.apiUrl
        val token = settings.apiToken ?: ""

        // Llamamos al servicio global para validar
        val installer = ApplicationManager.getApplication().getService(InstallerService::class.java)
        installer.validateConnection(apiUrl, token, project) { urlOk, tokenOk ->
            // Publicamos el resultado al MessageBus global
            installer.publishConnectionState(project, urlOk, tokenOk)
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
        repaint()
    }

    private fun loadSettingsAsync(check: Boolean = true) {
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

                val (apiUrl, tokenLen, autoScan) = snapshot

                ApplicationManager.getApplication().invokeLater({
                    urlTextField.text = apiUrl
                    tokenTextField.text = "•".repeat(tokenLen)
                    autoScanCheck.isSelected = autoScan
                }, project.disposed)

                if (check) {
                    triggerConnectionCheck()
                }
            }
        })
    }

}