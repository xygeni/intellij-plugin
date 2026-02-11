package com.github.xygeni.intellij.views

import com.github.xygeni.intellij.events.CONNECTION_STATE_TOPIC
import com.github.xygeni.intellij.events.ConnectionStateListener
import com.github.xygeni.intellij.events.SCAN_STATE_TOPIC
import com.github.xygeni.intellij.events.ScanStateListener
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.services.ScanService
import com.github.xygeni.intellij.events.INSTALLER_STATE_TOPIC
import com.github.xygeni.intellij.events.InstallerStateListener
import com.github.xygeni.intellij.services.InstallerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
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

/**
 * ScanView
 *
 * @author : Carmendelope
 * @version : 9/10/25 (Carmendelope)
 **/

class ScanView(private val project: Project) : JPanel() {

    private lateinit var header: JLabel
    private lateinit var content: JPanel
    private lateinit var button: JLabel
    private var scannerInstalled = false

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

        val installer = project.service<InstallerService>()
        scannerInstalled = installer.isInstalled()
        updateButtonState()

        project.messageBus.connect().subscribe(INSTALLER_STATE_TOPIC, object : InstallerStateListener {
            override fun installerStateChanged(project: Project?, installed: Boolean) {
                if (project != this@ScanView.project) return
                scannerInstalled = installed
                updateButtonState()
            }
        })

        project.messageBus.connect()
            .subscribe(CONNECTION_STATE_TOPIC, object : ConnectionStateListener {
                override fun connectionStateChanged(project: Project?, urlOk: Boolean, tokenOk: Boolean) {
                    Logger.log("Connection state changed to $urlOk, $tokenOk")
                    if (project != this@ScanView.project) return
                    isVisible = when {
                        !urlOk -> false
                        !tokenOk -> false
                        else -> true
                    }
                }
            })

        project.messageBus.connect()
            .subscribe(SCAN_STATE_TOPIC, object : ScanStateListener {
                override fun scanStateChanged(project: Project?, status: Int) {
                    if (project != this@ScanView.project) return
                    //this@ScanView.clickcable = status != 2
                    updateButtonState(status)
                }
            })
    }

    private fun updateButtonState(scanStatus: Int? = null) {
        val status = scanStatus ?: 0 // Default to not running if not provided

        button.isEnabled = scannerInstalled
        button.cursor = if (scannerInstalled) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)

        if (!scannerInstalled) {
            button.icon = Icons.RUN_ICON // Or a disabled version if available
            button.text = "Run Scan (Required Install)"
            button.toolTipText = "Please install Xygeni scanner first (Tools -> Xygeni -> Install)"
            button.foreground = JBColor.GRAY
            return
        }

        button.foreground = JBColor.namedColor("Label.foreground", JBColor.BLACK)
        button.icon = when {
            status == 2 -> Icons.RUN_IN_QUEUE_ICON
            else -> Icons.RUN_ICON
        }
        button.text = when {
            status == 2 -> "Scanning"
            else -> "Run Scan"
        }
        button.toolTipText = when {
            status == 2 -> "Stop scanning"
            else -> "Run Scan"
        }
    }

    private fun createContent() {
        button = JLabel().apply {
            icon = Icons.RUN_ICON
            text = "Run Scan"
            toolTipText = "Run Scan"
            iconTextGap = 10
        }

        button.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!scannerInstalled) return

                if (button.text == "Run Scan") {
                    // scan
                    project.getService(ScanService::class.java).scan(project)
                }else{
                    project.getService(ScanService::class.java).stop(project)
                }
            }
        })

        content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(5, 20, 10, 5)
            isVisible = false
        }
        content.add(button)

    }

    private fun createHeader() {
        header = JLabel("SCAN").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            alignmentX = LEFT_ALIGNMENT
            icon = Icons.CHEVRON_RIGHT_ICON
            iconTextGap = 10
            border = JBUI.Borders.emptyTop(8)
        }

        header.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                toggleContentVisibility()
            }
        })
    }

    private fun toggleContentVisibility() {
        content.isVisible = !content.isVisible
        header.icon = if (content.isVisible) Icons.CHEVRON_DOWN_ICON else Icons.CHEVRON_RIGHT_ICON

        revalidate()
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        repaint()
    }

}