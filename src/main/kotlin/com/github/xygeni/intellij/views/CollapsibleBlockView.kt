package com.github.xygeni.intellij.views

import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import icons.Icons
import java.awt.Color
import java.awt.Cursor
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import javax.swing.event.HyperlinkEvent

/**
 * CollapsibleBlockView
 *
 * @author : Carmendelope
 * @version : 8/10/25 (Carmendelope)
 **/


// CollapsibleBlockView with a base view of a folding block.
// Can be extended to react to model or bus events.
open class CollapsibleBlockView(
    private val headerText: String,
    private val contentHtml: String
) : JPanel() {

    protected lateinit var header: JLabel
    protected lateinit var contentPane: JTextPane

    fun createUI(separate: Boolean): JPanel {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = if (separate) {
            val padding = EmptyBorder(8, 0, 8, 0)
            val bottomLine = MatteBorder(0, 0, 1, 0, JBColor.GRAY)
            CompoundBorder(bottomLine, padding)
        } else {
            EmptyBorder(8, 0, 8, 0)
        }


        header = JLabel("$headerText").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            icon = Icons.CHEVRON_RIGHT_ICON
            iconTextGap = 10
        }

        contentPane = JTextPane().apply {
            contentType = "text/html"
            isEditable = false
            text = contentHtml
            border = EmptyBorder(8, 0, 8, 0)
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isVisible = false

            // Add hyperlink listener to open links in browser
            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    e.url?.toString()?.let { url ->
                        BrowserUtil.browse(url)
                    }
                }
            }
        }

        header.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                toggle()
            }
        })

        add(header)
        add(Box.createVerticalStrut(4))
        add(contentPane)
        return this
    }

    fun toggle() {
        contentPane.isVisible = !contentPane.isVisible
        header.icon = if (contentPane.isVisible) Icons.CHEVRON_DOWN_ICON else Icons.CHEVRON_RIGHT_ICON
        revalidate()
        repaint()
    }

    fun collapse() {
        if (contentPane.isVisible) toggle()
    }

    // onAttach optional method to subscribe to events
    open fun onAttach() {}

    // onDetach optional method to unsubscribe to events
    open fun onDetach() {}
}