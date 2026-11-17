package com.github.xygeni.intellij.views

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
 * The single CONFIGURATION panel of the tool window (#415): one collapsible header and, inside, the Connection,
 * Scan and Scan configuration blocks, each with its own sub-header. Same structure as the VS Code extension.
 */
class ConfigurationPanel(private val blocks: List<JPanel>) : JPanel() {

    private val header = JLabel("CONFIGURATION").apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        alignmentX = Component.LEFT_ALIGNMENT
        icon = Icons.CHEVRON_DOWN_ICON
        iconTextGap = 10
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) = toggle()
        })
    }
    private val content = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(2, 10, 6, 4)
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        border = MatteBorder(0, 0, 1, 0, JBColor.GRAY)
        add(header)
        add(Box.createVerticalStrut(2).apply { setAlignmentX(0f) })
        blocks.forEach { block -> content.add(block) }
        add(content)
    }

    private fun toggle() {
        content.isVisible = !content.isVisible
        header.icon = if (content.isVisible) Icons.CHEVRON_DOWN_ICON else Icons.CHEVRON_RIGHT_ICON
        Sections.capHeight(this)
    }

    /** The blocks change height on their own (collapse, rows shown or hidden): keep the panel's cap in step. */
    override fun doLayout() {
        super.doLayout()
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
}
