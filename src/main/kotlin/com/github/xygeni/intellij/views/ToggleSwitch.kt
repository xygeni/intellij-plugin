package com.github.xygeni.intellij.views

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleState
import javax.accessibility.AccessibleStateSet
import javax.swing.JComponent

/**
 * A pill toggle like the one in the VS Code extension's Configuration view (#415): rounded track, round knob,
 * the theme's accent colour when on. Mouse and keyboard (Space / Enter) switch it; disabled draws faded.
 */
class ToggleSwitch : JComponent() {

    var isSelected: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    /** Painted faded but still clickable (the global switch it depends on is off). */
    var dimmed: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    private val listeners = mutableListOf<ActionListener>()

    init {
        isFocusable = true
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        val size = Dimension(JBUI.scale(30), JBUI.scale(16))
        preferredSize = size
        minimumSize = size
        maximumSize = size
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!isEnabled) return
                requestFocusInWindow()
                toggle()
            }
        })
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (isEnabled && (e.keyCode == KeyEvent.VK_SPACE || e.keyCode == KeyEvent.VK_ENTER)) toggle()
            }
        })
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent?) = repaint()
            override fun focusLost(e: java.awt.event.FocusEvent?) = repaint()
        })
    }

    /** Read by screen readers as a check box with its on/off state; the name is set by the owner. */
    override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) {
            accessibleContext = object : AccessibleJComponent() {
                override fun getAccessibleRole(): AccessibleRole = AccessibleRole.CHECK_BOX
                override fun getAccessibleStateSet(): AccessibleStateSet =
                    super.getAccessibleStateSet().also { if (isSelected) it.add(AccessibleState.CHECKED) }
            }
        }
        return accessibleContext
    }

    fun addActionListener(listener: ActionListener) {
        listeners.add(listener)
    }

    private fun toggle() {
        isSelected = !isSelected
        val event = ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggle")
        listeners.forEach { it.actionPerformed(event) }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        cursor = Cursor.getPredefinedCursor(if (enabled) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width
            val h = height
            val track = if (isSelected) ACCENT else OFF_TRACK
            g2.color = if (!isEnabled) withAlpha(track, 0.4f) else if (dimmed) withAlpha(track, 0.5f) else track
            g2.fillRoundRect(0, 0, w, h, h, h)
            if (hasFocus()) {
                g2.color = JBColor.namedColor("Component.focusColor", ACCENT)
                g2.drawRoundRect(0, 0, w - 1, h - 1, h, h)
            }
            val pad = JBUI.scale(2)
            val knob = h - pad * 2
            val x = if (isSelected) w - pad - knob else pad
            g2.color = if (isEnabled) KNOB else withAlpha(KNOB, 0.6f)
            g2.fillOval(x, pad, knob, knob)
        } finally {
            g2.dispose()
        }
    }

    private fun withAlpha(color: Color, alpha: Float): Color = Color(color.red, color.green, color.blue, (alpha * 255).toInt())

    private companion object {
        /** The theme's accent: the default button colour in the New UI, a blue close to VS Code's otherwise. */
        val ACCENT: Color = JBColor.namedColor("Button.default.startBackground", JBColor(0x3574F0, 0x3574F0))
        val OFF_TRACK: Color = JBColor.namedColor("Component.borderColor", JBColor(0xBFBFBF, 0x6B6B6B))
        val KNOB: Color = JBColor(Color.WHITE, Color(0xE6E6E6))
    }
}
