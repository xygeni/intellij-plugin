package com.github.xygeni.intellij.views.report

/**
 * BaseView
 *
 * @author : Carmendelope
 * @version : 14/10/25 (Carmendelope)
 **/


import com.github.xygeni.intellij.dynamichtml.editor.DynamicHtmlFileEditor
import com.github.xygeni.intellij.events.READ_TOPIC
import com.github.xygeni.intellij.events.ReadListener
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.github.xygeni.intellij.render.BaseHtmlIssueRenderer
import com.github.xygeni.intellij.services.report.BaseReportService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.treeStructure.Tree
import icons.Icons
import java.awt.Color
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.jvm.javaClass

//  BaseView containing a tree panel for displaying issues
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

        // header configuration
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

        // Tree configuration
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

        // configure click and double click
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
        })
    }

    fun lineColToOffset(lineNum: Int, colNum: Int, document: Document): Int {
        val safeLine = lineNum.coerceIn(0, document.lineCount - 1)
        val start = document.getLineStartOffset(safeLine)
        val end = document.getLineEndOffset(safeLine)
        return (start + colNum).coerceIn(start, end)
    }

    fun openFileInEditor(
        project: Project,
        relativePath: String,
        line: Int = 1,
        column: Int = 0,
        endLine: Int = -1,
        endColumn: Int = -1
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val basePath = project.basePath ?: return@executeOnPooledThread
            val absolutePath = "$basePath/$relativePath"
            val vFile: VirtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath) 
                ?: return@executeOnPooledThread

            val beginLine0 = (line - 1).coerceAtLeast(0)
            val beginColumn0 = (column - 1).coerceAtLeast(0)
            val endLine0 = if (endLine >= 0) (endLine - 1).coerceAtLeast(0) else -1
            val endColumn0 = if (endColumn >= 0) (endColumn - 1).coerceAtLeast(0) else -1
            
            ApplicationManager.getApplication().invokeLater {
                val editor: Editor? = FileEditorManager.getInstance(project)
                    .openTextEditor(OpenFileDescriptor(project, vFile, beginLine0, beginColumn0), true)
                
                if (editor == null) {
                    Logger.log("openFileInEditor: editor is null", project)
                    return@invokeLater
                }

                val document = editor.document
                val markup = editor.markupModel

                // remove previous highlighters
                ApplicationManager.getApplication().runWriteAction {
                    markup.allHighlighters
                        .filter { it.layer == HighlighterLayer.ERROR + 1 }
                        .forEach { markup.removeHighlighter(it) }

                    // highlight the code
                    val underlineAttributes = TextAttributes().apply {
                        effectType = EffectType.BOXED
                        effectColor = JBColor.RED
                        backgroundColor = null
                    }

                    when {
                        endLine0 >= 0 && endColumn0 >= 0 -> {
                            val startOffset: Int
                            var endOffset: Int

                            if (beginColumn0 == 0 && endColumn0 == 0) {
                                startOffset = document.getLineStartOffset(beginLine0)
                                endOffset = document.getLineEndOffset(endLine0)
                            } else {
                                startOffset = lineColToOffset(beginLine0, beginColumn0, document)
                                endOffset = lineColToOffset(endLine0, endColumn0, document)
                                if (startOffset == endOffset) {
                                    endOffset = startOffset + 1
                                }
                            }

                            markup.addRangeHighlighter(
                                startOffset,
                                endOffset,
                                HighlighterLayer.ERROR + 1,
                                underlineAttributes,
                                HighlighterTargetArea.EXACT_RANGE
                            )
                        }

                        line > 0 -> {
                            val startOffset = document.getLineStartOffset(beginLine0)
                            val endOffset = document.getLineEndOffset(beginLine0)
                            markup.addRangeHighlighter(
                                startOffset,
                                endOffset,
                                HighlighterLayer.ERROR + 1,
                                underlineAttributes,
                                HighlighterTargetArea.EXACT_RANGE
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    // subscribeToMessage subscribes to READ_TOPIC to load issues when the scan finish
    protected open fun subscribeToMessage() {
        project.messageBus.connect().subscribe(READ_TOPIC, object : ReadListener {
            override fun readCompleted(project: Project?, reportType: String?) {
                if (project != this@BaseView.project || reportType != service.reportType) return
                SwingUtilities.invokeLater {
                    if (treeScrollPane.isVisible) loadChildren()
                }
            }
        })
    }

    protected open fun getItems(): List<T> = service.issues

    protected open fun getToolTipExplanation(item: T) : String {
        return item.explanation
    }

    // buildNode builds a node for each issue
    protected open fun buildNode(item: T): DefaultMutableTreeNode {
        return DefaultMutableTreeNode(
            NodeData(
                text = "(${item.type}) - ${item.file}",
                icon = item.getIcon(),
                tooltip = getToolTipExplanation(item),
                onClick = {
                    openFileInEditor(
                        project, item.file, item.beginLine, item.beginColumn,
                        item.endLine, item.endColumn
                    )
                },
                onDoubleClick = {
                    this.openDynamicHtml(project, item)
                }
            ))
    }


    protected open fun openDynamicHtml(project: Project, item: T) {
        val fileEditorManager = FileEditorManager.getInstance(project)

        // Close existing dynamic HTML editors properly
        ApplicationManager.getApplication().invokeLater {
            fileEditorManager.allEditors
                .filterIsInstance<DynamicHtmlFileEditor>()
                .forEach { editor ->
                    editor.file?.let { file -> 
                        fileEditorManager.closeFile(file)
                    }
                }
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // Renderizar HTML pesado y obtener datos
                val htmlContent = renderer.render(item)
                val data: String? = if (item.kind != "" && item.kind != "sca") {
                    item.fetchData()
                } else null

                ApplicationManager.getApplication().invokeLater {
                    val fileName = "${item.type}.dynamic.html"
                    val file = LightVirtualFile(fileName, htmlContent).apply { isWritable = false }

                    fileEditorManager.openFile(file, true)
                    val editor = fileEditorManager.getEditors(file)
                        .filterIsInstance<DynamicHtmlFileEditor>()
                        .firstOrNull() ?: return@invokeLater

                    // Cargar HTML y renderData
                    editor.loadHtml(htmlContent)
                    data?.let { editor.renderData(it) }
                }
            } catch (e: Exception) {
                Logger.warn(e.message ?: e.toString(), project)
            }
        }
    }
}