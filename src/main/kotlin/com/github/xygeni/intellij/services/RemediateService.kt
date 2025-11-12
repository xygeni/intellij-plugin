package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.JsonConfig
import com.github.xygeni.intellij.model.PluginConfig
import com.github.xygeni.intellij.model.report.server.RemediationData
import com.github.xygeni.intellij.settings.XygeniSettings
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/**
 * RemediateService
 *
 * @author : Carmendelope
 * @version : 30/10/25 (Carmendelope)
 **/
@Service(Service.Level.PROJECT)
class RemediateService : ProcessExecutorService() {

    private val manager = service<PluginManagerService>()
    private val pluginContext = manager.pluginContext
    private val executor = manager.executor

    private val baseArgs: Map<String, String> = mapOf(
        "util" to "",
        "rectify" to ""
    )

    private fun buildArgs(remediation: RemediationData, filePath: String): Map<String, String> {
        return baseArgs.toMutableMap().apply {
            remediation.kind?.let { put("--${remediation.kind}", "") }
            remediation.filePath?.let { put("--file-path", filePath) }
            remediation.detector?.let { put("--detector", remediation.detector) }
            remediation.line?.let { put("--line", "${remediation.line}") }
            remediation.dependency?.let { put("--dependency", "${remediation.dependency}") }
            "-v" to ""
        }
    }


    private fun getTempFile(project: Project, remediation: RemediationData): File {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "xygeni-plugin/${project.name}")
            .apply { mkdirs() }

        val sourceFile = File(project.basePath, remediation.filePath)
        require(sourceFile.exists()) {
            "Source file does not exist: ${sourceFile.absolutePath}"
        }

        return File(tempDir, sourceFile.name)
    }

    fun remediate(project: Project,
                  data: String,
                  onComplete: (Boolean) -> Unit
    ) {
        val remediation = JsonConfig.relaxed.decodeFromString<RemediationData>(data)
        val source = File(project.basePath, remediation.filePath)


        val target = getTempFile(project, remediation)
        source.copyTo(target, overwrite = true)

        // refresh the fileSystem
        val vfs = LocalFileSystem.getInstance()
        vfs.refresh(true)
        val rightVf = vfs.refreshAndFindFileByPath(target.absolutePath)

        // 2.- lanzamos la remediacion en ese fichero
        executor.executeProcess(
            pluginContext.xygeniCommand,
            buildArgs(remediation, target.absolutePath),
            PluginConfig.fromSettings(XygeniSettings.getInstance()).toEnv(),
            pluginContext.installDir,
            { success ->
                if (success) {
                    Logger.log("✅ Remediation finished")
                    showDiff(project, remediation, target.absolutePath )
                } else {
                    Logger.error("❌ Remediation failed")
                }
                onComplete(success)
            })
    }

    // showDiff opens a diff showing
    private fun showDiff(project: Project, remediation: RemediationData, rightPath: String) {
        ApplicationManager.getApplication().invokeLater {

            val source = File(project.basePath, remediation.filePath)

            val left = LocalFileSystem.getInstance().findFileByPath(source.absolutePath)
            val right = LocalFileSystem.getInstance().refreshAndFindFileByPath(rightPath)
            if (left != null && right != null) {

                val factory = DiffContentFactory.getInstance()

                val content1 = factory.create(project, left)
                val content2 = factory.create(project, right)

                val request = SimpleDiffRequest(
                    "Remediation diff",
                    content1, content2,
                    left.name, right.name
                )
                DiffManager.getInstance().showDiff(project, request)
            }
        }
    }

    // save applies the new safe file to the vuln one
    fun save(project: Project,
             data: String,
    ){
        try {
            val remediation = JsonConfig.relaxed.decodeFromString<RemediationData>(data)
            val source = File(project.basePath, remediation.filePath)

            val target = getTempFile(project, remediation)
            target.copyTo(source, overwrite = true)
            Logger.log("✅ Saved")
        }
        catch (e: Exception) {
            Logger.error("❌ Failed to save remediation file")
        }
    }

    // region OtherFunctions
    /*
    // showDiffInDialog opens two files in a dialog
    // TODO: add diff
    private fun showDiffInDialog(project: Project, remediation: RemediationData, rightPath: String) {
        ApplicationManager.getApplication().invokeLater {
            val source = File(project.basePath, remediation.filePath)

            val leftFile = LocalFileSystem.getInstance().findFileByPath(source.absolutePath)
            val rightFile = LocalFileSystem.getInstance().findFileByPath(rightPath)

            if (leftFile == null || rightFile == null) return@invokeLater

            val leftBytes = leftFile.contentsToByteArray()
            val rightBytes = rightFile.contentsToByteArray()
            val leftText = String(leftBytes, StandardCharsets.UTF_8)
            val rightText = String(rightBytes, StandardCharsets.UTF_8)

            val leftArea = JTextArea(leftText).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
            val rightArea = JTextArea(rightText).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }

            val leftScroll = JBScrollPane(leftArea)
            val rightScroll = JBScrollPane(rightArea)

            val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll).apply {
                dividerLocation =  450
            }

            val panel = JPanel(BorderLayout()).apply { add(split, BorderLayout.CENTER) }

            val dialog = JFrame("Remediation diff").apply {
                defaultCloseOperation = JFrame.HIDE_ON_CLOSE
                contentPane = panel
                setSize(900, 600)
                setLocationRelativeTo(null)
                isVisible = true
            }
        }
    }
    // openFileSafelyToTheRight try to open as "Split right"
    // TODO: add diff
    fun openFileSafelyToTheRight(project: Project,rightPath: String) {
        val fileToOpen : VirtualFile? = LocalFileSystem.getInstance().refreshAndFindFileByPath(rightPath)
        if (fileToOpen == null) {return}
        val managerEx = FileEditorManagerEx.getInstanceEx(project)

        // Elegir una ventana “base” para el split: si currentWindow no tiene VirtualFile, usar null
        val baseWindow = managerEx.currentWindow
        val useCurrentWindow = baseWindow?.selectedFile?.isValid == true

        val splitBase = if (useCurrentWindow) baseWindow else null

        // Guardar ventanas antes del split
        val oldWindows = managerEx.windows.toSet()

        // Crear el split vertical (a la derecha) desde la ventana base o desde el layout raíz
        managerEx.createSplitter(SwingConstants.VERTICAL, splitBase)

        // Obtener la nueva ventana
        val newWindow = managerEx.windows.firstOrNull { it !in oldWindows } ?: return

        // Abrir el fichero en el panel derecho
        managerEx.openFile(
            fileToOpen,
            newWindow,
            FileEditorOpenOptions(requestFocus = true)
        )

        // Foco en el panel derecho
        managerEx.currentWindow = newWindow
    }
    // showSimpleDiffInRightSplit shows two files in the bottom(or right)
    // TODO: add diff
    private fun showSimpleDiffInRightSplit(project: Project, remediation: RemediationData, rightPath: String) {
        ApplicationManager.getApplication().invokeLater {
            val source = File(project.basePath, remediation.filePath)
            val left = LocalFileSystem.getInstance().findFileByPath(source.absolutePath)
            val right = LocalFileSystem.getInstance().refreshAndFindFileByPath(rightPath)
            if (left == null || right == null) return@invokeLater

            // Lee contenidos como texto
            val leftText = left.charset.run { java.nio.charset.Charset.defaultCharset() }.let { left.contentsToByteArray().toString(it) } // simplificado
            val rightText = right.contentsToByteArray().toString(left.charset)

            val textAreaLeft = JTextArea(leftText).apply { isEditable = false }
            val textAreaRight = JTextArea(rightText).apply { isEditable = false }

            val panel = JPanel(BorderLayout())
            val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(textAreaLeft), JScrollPane(textAreaRight)).apply {
                dividerLocation = 450
            }
            panel.add(split, BorderLayout.CENTER)

            val content = ContentFactory.getInstance().createContent(panel, "Remediation: ${left.name} ↔ ${right.name}", true)

            val toolWindowManager = ToolWindowManager.getInstance(project)
            var toolWindow = toolWindowManager.getToolWindow("Diffs")
            if (toolWindow == null) {
                    toolWindowManager.registerToolWindow("Diffs", true, ToolWindowAnchor.BOTTOM, true)
                toolWindow = toolWindowManager.getToolWindow("Diffs")
            }

            toolWindow?.let { tw ->
                val manager = tw.contentManager
                if (manager.contentCount > 0) manager.removeAllContents(true)
                manager.addContent(content)
                manager.setSelectedContent(content)
                tw.activate(null)
            }
        }
    }
     */
    //endregion
}