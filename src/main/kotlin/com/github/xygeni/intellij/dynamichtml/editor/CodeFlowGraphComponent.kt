package com.github.xygeni.intellij.dynamichtml.editor

import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.QuadCurve2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

/**
 * CodeFlowGraphComponent — Java2D port of the D3 diagram in BaseHtmlIssueRenderer (same layout:
 * x from the average path column, y from the step level; same node colours per role), used by the
 * Swing detail viewer on IDEs without JCEF (#1976).
 **/
class CodeFlowGraphComponent(private val data: CodeFlowData) : JComponent() {

    private data class PlacedNode(val node: FlowNode, val x: Double, val y: Double, val isSink: Boolean)

    private val colSpacing = 220.0
    private val rowSpacing = 120.0
    private val nodeRadius = 30.0
    private val linkColor = Color(0x99, 0x99, 0x99)

    private val placed: Map<String, PlacedNode>

    init {
        val pathColumns = mutableMapOf<String, MutableList<Int>>()
        data.paths.forEachIndexed { pathIndex, path ->
            path.forEachIndexed { level, id -> pathColumns.getOrPut("${id}__$level") { mutableListOf() }.add(pathIndex) }
        }
        val sinkKeys = data.paths.filter { path -> path.isNotEmpty() }
            .map { path -> "${path.last()}__${path.lastIndex}" }.toSet()

        placed = data.nodes.associate { node ->
            val columns = pathColumns[node.key].orEmpty()
            val averageColumn = if (columns.isEmpty()) 0.0 else columns.average()
            node.key to PlacedNode(
                node = node,
                x = averageColumn * colSpacing + 150,
                y = node.level * rowSpacing + 80,
                isSink = node.key in sinkKeys,
            )
        }

        val maxX = placed.values.maxOfOrNull { placedNode -> placedNode.x } ?: 150.0
        val maxY = placed.values.maxOfOrNull { placedNode -> placedNode.y } ?: 80.0
        preferredSize = Dimension((maxX + 170).toInt(), (maxY + 110).toInt())
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = ""
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val hit = placed.values.firstOrNull { placedNode ->
            val dx = event.x - placedNode.x
            val dy = event.y - placedNode.y
            dx * dx + dy * dy <= nodeRadius * nodeRadius
        } ?: return null
        val node = hit.node
        val code = node.code.orEmpty().trim()
        return buildString {
            append("<html><b>").append(escape(node.filePath.orEmpty())).append(':').append(node.line).append("</b>")
            append("<br>").append(escape(node.type.orEmpty()))
            if (!node.category.isNullOrBlank()) append("<br>Category: ").append(escape(node.category))
            if (!node.container.isNullOrBlank()) append("<br>Container: ").append(escape(node.container))
            if (code.isNotEmpty()) append("<br><pre>").append(escape(code)).append("</pre>")
            append("</html>")
        }
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val canvas = graphics as Graphics2D
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        paintLinks(canvas)
        paintNodes(canvas)
    }

    private fun paintLinks(canvas: Graphics2D) {
        canvas.color = linkColor
        canvas.stroke = BasicStroke(2f)
        data.links.forEach { link ->
            val source = placed[link.source] ?: return@forEach
            val target = placed[link.target] ?: return@forEach
            val controlX = source.x + maxOf(30.0, (target.x - source.x) / 2)
            val controlY = (source.y + target.y) / 2
            canvas.draw(QuadCurve2D.Double(source.x, source.y, controlX, controlY, target.x, target.y))

            // Arrow head at the curve's midpoint, pointing along the source → target direction.
            val midX = 0.25 * source.x + 0.5 * controlX + 0.25 * target.x
            val midY = 0.25 * source.y + 0.5 * controlY + 0.25 * target.y
            val angle = Math.atan2(target.y - source.y, target.x - source.x)
            val arrow = Path2D.Double().apply {
                moveTo(8.0, 0.0); lineTo(-6.0, -6.0); lineTo(-6.0, 6.0); closePath()
            }
            val saved = canvas.transform
            canvas.translate(midX, midY)
            canvas.rotate(angle)
            canvas.fill(arrow)
            canvas.transform = saved
        }
    }

    private fun paintNodes(canvas: Graphics2D) {
        val ordered = placed.values.sortedWith(compareBy({ placedNode -> placedNode.node.level }, { placedNode -> placedNode.x }))
        ordered.forEachIndexed { index, placedNode ->
            val (fill, stroke) = roleColors(placedNode)
            val circle = Ellipse2D.Double(placedNode.x - nodeRadius, placedNode.y - nodeRadius, nodeRadius * 2, nodeRadius * 2)
            canvas.color = fill
            canvas.fill(circle)
            canvas.color = stroke
            canvas.stroke = BasicStroke(2f)
            canvas.draw(circle)
            paintStackGlyph(canvas, placedNode.x, placedNode.y)

            // Labels alternate two rows so neighbouring nodes do not overlap, as in the D3 version.
            val label = placedNode.node.label ?: placedNode.node.id.orEmpty()
            val labelY = placedNode.y + if (index % 2 == 0) 45 else 65
            canvas.color = UIUtil.getLabelForeground()
            canvas.font = UIUtil.getLabelFont()
            val width = canvas.fontMetrics.stringWidth(label)
            canvas.drawString(label, (placedNode.x - width / 2).toFloat(), labelY.toFloat())
        }
    }

    /** Three stacked white cards inside the circle, standing in for the D3 stack icon. */
    private fun paintStackGlyph(canvas: Graphics2D, centerX: Double, centerY: Double) {
        canvas.stroke = BasicStroke(1f)
        for (layer in 0 until 3) {
            val offset = layer * 5.0
            val card = RoundRectangle2D.Double(centerX - 10 + offset / 2, centerY - 9 + offset, 16.0, 6.0, 2.0, 2.0)
            canvas.color = Color.WHITE
            canvas.fill(card)
            canvas.color = Color(0xE2, 0xE8, 0xF0)
            canvas.draw(card)
        }
    }

    private fun roleColors(placedNode: PlacedNode): Pair<Color, Color> {
        val type = placedNode.node.type.orEmpty().lowercase()
        return when {
            placedNode.node.level == 0 -> Color(0x59, 0xC9, 0xA6) to Color(0x4D, 0x8A, 0x7C)   // source
            placedNode.isSink -> Color(0x1F, 0x29, 0x37) to Color(0x11, 0x18, 0x27)              // sink
            type.contains("sanitizer") -> Color(0xF5, 0x9E, 0x0B) to Color(0xD9, 0x77, 0x06)
            type.contains("propagation") -> Color(0x3B, 0x82, 0xF6) to Color(0x4D, 0x8A, 0x7C)
            else -> Color(0x10, 0xB9, 0x81) to Color(0x05, 0x96, 0x69)
        }
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
