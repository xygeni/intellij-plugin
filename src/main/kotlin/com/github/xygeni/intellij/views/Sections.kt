package com.github.xygeni.intellij.views

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.Icons
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Shared look of the blocks inside the CONFIGURATION panel (Connection, Scan, Scan configuration): a clickable
 * sub-header with a chevron, like the VS Code extension's Configuration view (#415).
 */
object Sections {

    /** A block sub-header: bold, chevron, hand cursor; `onClick` toggles the block. */
    fun subHeader(text: String, onClick: () -> Unit): JLabel = JLabel(text).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        alignmentX = Component.LEFT_ALIGNMENT
        icon = Icons.CHEVRON_RIGHT_ICON
        iconTextGap = 8
        font = font.deriveFont(java.awt.Font.BOLD)
        border = JBUI.Borders.empty(6, 2, 2, 2)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) = onClick()
        })
    }

    /** Left inset of a block body: the chevron (16) + gap (8) + border (2) of the sub-header, minus the ~4px that
     *  text fields and checkboxes paint inside their own bounds, so their visible edge meets the header text. */
    const val BODY_LEFT = 22

    /** The inset plain components (labels, links, rows) need to sit on the same vertical as the fields' visible edge. */
    const val PLAIN_LEFT = 4

    /** Wraps a plain component so its visible left edge lines up with the fields and checkboxes. */
    fun flush(component: java.awt.Component): JPanel = JPanel(java.awt.BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.emptyLeft(PLAIN_LEFT)
        add(component, java.awt.BorderLayout.CENTER)
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    /** The muted one-line description under a row label. */
    fun description(text: String): JLabel = JLabel(text).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
        alignmentX = Component.LEFT_ALIGNMENT
    }

    /** A vertical panel aligned left, used for every block body. */
    fun column(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    /** Caps a BoxLayout child at its preferred height so blocks never stretch. */
    fun capHeight(panel: JPanel) {
        panel.revalidate()
        panel.maximumSize = Dimension(Int.MAX_VALUE, panel.preferredSize.height)
        panel.repaint()
    }
}
