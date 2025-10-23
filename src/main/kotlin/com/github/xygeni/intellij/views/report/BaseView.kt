package com.github.xygeni.intellij.views.report

/**
 * BaseView
 *
 * @author : Carmendelope
 * @version : 14/10/25 (Carmendelope)
 **/
import com.github.xygeni.intellij.events.READ_TOPIC
import com.github.xygeni.intellij.events.ReadListener
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.github.xygeni.intellij.model.report.secret.SecretsXygeniIssue
import com.github.xygeni.intellij.render.BaseHtmlIssueRenderer
import com.github.xygeni.intellij.render.SecretIssueRenderer
import com.github.xygeni.intellij.services.report.BaseReportService
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.treeStructure.Tree
import icons.Icons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

abstract class BaseView<T : BaseXygeniIssue>(
    protected val project: Project,
    private val title: String,
    protected val service: BaseReportService<T>,
    private val summaryIcon: Icon? = null,
    private val maxVisibleHeight: Int = 250
) : JPanel() {

    private val header = JLabel(title)
    val treeScrollPane: JBScrollPane
    protected val root = DefaultMutableTreeNode(title)
    protected val model = DefaultTreeModel(root)
    protected val tree = Tree(model)

    init {

        service.read()

        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY)

        // Configura header
        header.icon = Icons.CHEVRON_RIGHT_ICON
        header.iconTextGap = 10
        header.border = EmptyBorder(8, 0, 0, 0)
        header.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        header.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                toggle()
            }
        })

        add(header)

        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.rowHeight = 20

        treeScrollPane = JBScrollPane(tree).apply {
            border = EmptyBorder(0, 0, 0, 0)
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            isVisible = false
        }

        add(Box.createVerticalStrut(4))
        add(treeScrollPane)

        setupRenderer()
        subscribeToMessage()
    }

    protected abstract val renderer: BaseHtmlIssueRenderer<T>

    private fun toggle() {
        treeScrollPane.isVisible = !treeScrollPane.isVisible
        header.icon = if (treeScrollPane.isVisible) Icons.CHEVRON_DOWN_ICON else Icons.CHEVRON_RIGHT_ICON
        if (treeScrollPane.isVisible) {
            loadChildren()
        }
        updateScrollHeight()
        revalidate()
        repaint()
    }

    protected fun loadChildren() {
        val items = getItems()
        root.removeAllChildren()

        val summaryText = if (items.isEmpty()) "0 issues found" else "${items.size} issues found"
        val summaryNode = DefaultMutableTreeNode(NodeData(summaryText, summaryIcon))
        root.add(summaryNode)

        items.forEach { summaryNode.add(buildNode(it)) }

        model.reload()
        tree.expandRow(0)
        updateScrollHeight()
    }

    private fun updateScrollHeight() {
        val visibleRows = if (treeScrollPane.isVisible) tree.rowCount else 1
        val rowHeight = tree.rowHeight.takeIf { it > 0 } ?: 20
        val contentHeight = visibleRows * rowHeight + 30
        val actualHeight = contentHeight.coerceAtMost(maxVisibleHeight)

        treeScrollPane.preferredSize = Dimension(Int.MAX_VALUE, actualHeight)
        treeScrollPane.maximumSize = Dimension(Int.MAX_VALUE, actualHeight)
        treeScrollPane.minimumSize = Dimension(0, actualHeight)
    }

    private fun setupRenderer() {
        tree.cellRenderer = javax.swing.tree.TreeCellRenderer { t, value, sel, exp, leaf, row, focus ->
            val node = value as DefaultMutableTreeNode
            val data = node.userObject as? NodeData
            JLabel().apply {
                text = data?.text ?: node.userObject.toString()
                icon = data?.icon
                toolTipText = data?.tooltip
                border = EmptyBorder(2, 10, 2, 2)
                isOpaque = true
            }
        }

        tree.addMouseListener(object : MouseAdapter() {
            private var clickTimer: Timer? = null
            override fun mouseClicked(e: MouseEvent) {
                val selPath = tree.getPathForLocation(e.x, e.y)
                selPath?.lastPathComponent?.let { node ->
                    if (node is DefaultMutableTreeNode) {
                        val data = node.userObject as? NodeData
                        if (e.clickCount == 2) {
                            clickTimer?.stop()
                            data?.onDoubleClick?.invoke()
                        } else if (e.clickCount == 1) {
                            clickTimer = Timer(200) {
                                data?.onClick?.invoke()
                            }.apply { isRepeats = false; start() }
                        }
                    }
                }
            }

            /*override fun mouseClicked(e: MouseEvent) {
                val selPath = tree.getPathForLocation(e.x, e.y)
                selPath?.lastPathComponent?.let { node ->
                    if (node is DefaultMutableTreeNode) {
                        val data = node.userObject as? NodeData
                        when (e.clickCount) {
                            1 -> data?.onClick?.invoke()
                            2 -> data?.onDoubleClick?.invoke()

                        }
                    }
                }
            }*/
        })

        //tree.addTreeSelectionListener { event ->
        //    val node = event.path.lastPathComponent as? DefaultMutableTreeNode
        //    val data = node?.userObject as? NodeData
        //    data?.onClick?.invoke()
        //}
    }

    fun openFileInEditor(project: Project, relativePath: String, line: Int = 0, column: Int = 0) {
        val basePath = project.basePath
        val absolutePath = "$basePath/$relativePath"
        val vFile: VirtualFile? = LocalFileSystem.getInstance().findFileByPath(absolutePath)
        if (vFile != null) {
            val linePos = (line - 1).coerceAtLeast(0)
            val columnPos = (column - 1).coerceAtLeast(0)

            FileEditorManager.getInstance(project).openTextEditor(
                OpenFileDescriptor(project, vFile, linePos, columnPos),
                true
            )
        } else {
            Logger.log("File not found: $absolutePath")
        }
    }

    protected open fun subscribeToMessage() {
        project.messageBus.connect().subscribe(READ_TOPIC, object : ReadListener {
            override fun readCompleted(project: Project?, reportType: String?) {
                if (project != this@BaseView.project || reportType != service.reportType) return
                javax.swing.SwingUtilities.invokeLater {
                    if (treeScrollPane.isVisible) loadChildren()
                }
            }
        })
    }

    protected open fun getItems(): List<T> = service.issues
    protected open fun buildNode(item: T): DefaultMutableTreeNode = DefaultMutableTreeNode(item.toString())

    protected open fun openDynamicHtml(project: Project, item: T) {
        val fileName = if (JBCefApp.isSupported()) {
            // DynamicHtmlEditorProvider con JCEF
            "${item.type}.dynamic.html" // DynamicHtmlEditorProvider con JCEF
        } else {
            "${item.type}.html" // normal
        }

        val content = renderer.render(item)
        println(content)

        val file = LightVirtualFile(fileName, content).apply { isWritable = false }
        FileEditorManager.getInstance(project).openFile(file, true)
    }

}