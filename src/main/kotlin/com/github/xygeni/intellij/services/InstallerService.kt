package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.MyBundle
import com.github.xygeni.intellij.events.CONNECTION_STATE_TOPIC
import com.github.xygeni.intellij.events.INSTALLER_STATE_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.PluginContext
import com.github.xygeni.intellij.notifications.NotificationService
import com.github.xygeni.intellij.settings.XygeniSettings
import com.github.xygeni.intellij.views.McpSetupView
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.FileVisitResult
import java.nio.file.LinkOption
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.swing.SwingUtilities


/**
 * InstallerService
 *
 * @author : Carmendelope
 * @version : 7/10/25 (Carmendelope)
 **/
@Service(Service.Level.APP)
class InstallerService : ProcessExecutorService() {

    private val pluginContext = service<PluginContext>() 
    private val client: OkHttpClient = XygeniHttpClient.create(connectTimeoutSeconds = 15, readTimeoutSeconds = 30)


    private fun uninstallIfNeeded(project: Project? = null) {
        Logger.log("Removing previous installation: ${this.pluginContext.installDir.absolutePath}...", project)
        this.deleteRecursively(this.pluginContext.installDir.absolutePath)
    }

    private fun checkUrlAccessible(url: String, project: Project? = null, callback: (Boolean) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val accessible = try {
                val validateUrl = if (url.endsWith("/")) "${url}ping" else "$url/ping"
                val request = Request.Builder()
                    .url(validateUrl)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    Logger.log("=== Connection ready: ${response.code}", project)
                    response.isSuccessful || response.code in 300..399
                }
            } catch (e: Exception) {
                false
            }

            SwingUtilities.invokeLater {
                callback(accessible)
            }
        }
    }

    private fun checkTokenValid(apiUrl: String, token: String, project: Project? = null, callback: (Boolean) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val valid = try {
                val validateUrl = if (apiUrl.endsWith("/")) "${apiUrl}language" else "$apiUrl/language"

                val request = Request.Builder()
                    .url(validateUrl)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    Logger.log("=== Token authorized: ${response.code}", project)
                    response.isSuccessful || response.code in 300..399
                }
            } catch (e: Exception) {
                false
            }

            SwingUtilities.invokeLater {
                callback(valid)
            }
        }
    }

    fun validateConnection(
        apiUrl: String,
        token: String,
        project: Project? = null,
        callback: (urlOk: Boolean, tokenOk: Boolean) -> Unit
    ) {
        if (apiUrl.isBlank()) {
            SwingUtilities.invokeLater { callback(false, false) }
            return
        }
        Logger.log("===========================", project)
        Logger.log("== Validating connection ==", project)
        Logger.log("===========================", project)

        checkUrlAccessible(apiUrl, project) { urlOk ->
            if (!urlOk) {
                callback(false, false)
                return@checkUrlAccessible
            }

            if (token.isBlank()) {
                callback(true, false)
                return@checkUrlAccessible
            }

            checkTokenValid(apiUrl, token, project) { tokenOk ->
                callback(true, tokenOk)
            }
        }
    }

    // Last known connection state (url/token are global, so a single cached value is enough).
    // Views read this on initialize() to avoid missing the startup CONNECTION_STATE_TOPIC event,
    // which is published before the (lazily created) tool window has subscribed. Null = unknown.
    @Volatile
    private var connectionState: Pair<Boolean, Boolean>? = null

    /** Last validated (urlOk, tokenOk), or null if no validation has run yet. */
    fun getConnectionState(): Pair<Boolean, Boolean>? = connectionState

    fun publishConnectionState(project: Project, urlOk: Boolean, tokenOk: Boolean) {
        connectionState = urlOk to tokenOk
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().messageBus
                .syncPublisher(CONNECTION_STATE_TOPIC)
                .connectionStateChanged(project, urlOk, tokenOk)
        }
    }

    private fun downloadFile(url: String,  fileName: String, token: String = "", project: Project? = null): File? {
        return try {

            val request = Request.Builder()
                .url(url)
                .apply {
                    if (!token.isNullOrBlank()) {
                        addHeader("Authorization", "Bearer $token")
                    }
                }
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.warn("error downloading from $url: ${response.code}", project)
                    return null
                }

                val body = response.body ?: run {
                    Logger.warn("Empty response downloading from $url", project)
                    return null
                }

                val tempDir = File(System.getProperty("java.io.tmpdir"), "xygeni-plugin").apply { mkdirs() }
                val tempFile = File(tempDir, fileName)

                // delete the previous script
                this.deleteRecursively(tempFile.absolutePath)

                Logger.log("Downloading from:  $url to: $tempFile", project)

                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Logger.log("File downloaded successfully: ${tempFile.absolutePath}", project)
                tempFile
            }
        } catch (e: Exception) {
            Logger.error("error downloading $url", e, project)
            null
        }
    }

    // deleteRecursively removes the path WITHOUT following symlinks: the Hub wires the scanner
    // folder as a link to a folder outside the install dir, and `File.deleteRecursively()`
    // wiped that target (#1976). Links are removed as links; the walk never descends into them.
    private fun deleteRecursively(path: String): Boolean {
        val target = Paths.get(path)
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return false
        if (Files.isSymbolicLink(target)) return Files.deleteIfExists(target)
        Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, failure: IOException?): FileVisitResult {
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
        return true
    }

    // unzip decompress a file in destDir
    private fun unzip(zipFile: Path, destDir: Path, project: Project? = null) {
        val start = System.currentTimeMillis()
        Logger.log("Unzip $zipFile into $destDir")
        try {
            ZipInputStream(Files.newInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = destDir.resolve(entry.name)
                    if (entry.isDirectory) {
                        Files.createDirectories(newFile)
                    } else {
                        Files.createDirectories(newFile.parent)
                        Files.newOutputStream(newFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val elapsed = System.currentTimeMillis() - start
            Logger.log("Unzip finished:  ${elapsed}ms", project)
        } catch (e: Exception) {
            Logger.error("error unzipping $zipFile", e, project)
        }
    }

    // downloadAndInstall downloads xygeni file zip and decompresses

    data class Step(
        val message: String,
        val fn: (Project?) -> Unit
    )

    private fun installMCPFn(project: Project?) {
        Logger.log("==================================", project)
        Logger.log("== Running MCP installation ==", project)
        Logger.log("==================================", project)
        val settings = XygeniSettings.getInstance()
        val f = downloadFile( settings.getMcpDownloadUrl(),
            this@InstallerService.pluginContext.mcpJarFileName,
            settings.apiToken ?: "",
            project)
        if (f == null) {
            throw IllegalStateException("could not download ${settings.getMcpDownloadUrl()}")
        }
        Logger.log("moving mcp to ${this.pluginContext.mcpJarFile}", project)
        f.copyTo(this.pluginContext.mcpJarFile, true)
        Logger.log("Xygeni MCP installed successfully!", project)
    }

    private fun installScannerFn(project: Project?) {
        Logger.log("==================================", project)
        Logger.log("== Running scanner installation ==", project)
        Logger.log("==================================", project)

        // val f = download(this@InstallerService.pluginContext.scriptUrl + "xygeni_scanner.zip", project)
        val settings = XygeniSettings.getInstance()
        val f = downloadFile( settings.getScannerDownloadUrl(),
            this@InstallerService.pluginContext.scannerZipFileName,
            settings.apiToken ?: "",
            project)
        if (f == null) {
            throw IllegalStateException("could not download ${settings.getScannerDownloadUrl()}")
        }
        unzip(
            f.toPath(),
            Paths.get(this@InstallerService.pluginContext.installDir.absolutePath),
            project
        )
        // + x to xygeni command
        val commandFile = File(this@InstallerService.pluginContext.xygeniCommand)
        commandFile.setExecutable(true)
        Logger.log("Xygeni scanner installed successfully!", project)
    }

    // isInstalled checks if the xygeni command already exists
    fun isInstalled(mcp: Boolean = false): Boolean {
        if (!mcp) return File(this.pluginContext.xygeniCommand).exists()
        return this.pluginContext.mcpJarFile.exists()
    }

    fun publishInstallerState(project: Project?) {
        if (project == null) return
        val installed = isInstalled()
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().messageBus
                .syncPublisher( INSTALLER_STATE_TOPIC)
                .installerStateChanged(project, installed)
        }
    }

    // install checks the xygeni command and install if it does not exist
    fun install(project: Project?) {
        var steps = mutableListOf<Step>()
        if (!isInstalled()) {
            steps.add(Step("Installing scanner", this::installScannerFn,))
        } else {
            Logger.log(">> Xygeni installed", project)
            publishInstallerState(project)
        }
        if (!isInstalled(mcp = true)) {
            steps.add(Step("Installing mcp", this::installMCPFn))
        } else {
            Logger.log(">> Xygeni MCP installed", project)
        }
        if (steps.isNotEmpty()) {
            executeInBackground(project, steps)
        }
    }

    // installOrUpdate forces a new installation removing the previous one. The removal runs as
    // the first step of the SAME background task: it deletes ~250 jars and used to run on the EDT
    // from the Install action, the Settings save and the "Retry installation" notification.
    fun installOrUpdate(project: Project?) {
        val steps = listOf(
            Step("Removing previous installation") { targetProject ->
                uninstallIfNeeded(targetProject)
                publishInstallerState(targetProject)
            },
            Step("Installing scanner", this::installScannerFn),
            Step("Installing mcp", this::installMCPFn),
        )
        executeInBackground(project, steps)
    }

    private fun executeInBackground(
        project: Project?,
        steps: List<Step>
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Installing...") {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val installedComponents = mutableListOf<String>()
                val failedComponents = mutableListOf<String>()
                
                for (step in steps) {
                    val installsComponent = step.message.startsWith("Installing ")
                    val component = if (installsComponent) step.message.replace("Installing ", "") else "previous installation cleanup"
                    try {
                        indicator.text = step.message
                        step.fn(project)
                        if (installsComponent) installedComponents.add(component)
                    } catch (e: Exception) {
                        failedComponents.add(component)
                        Logger.error("error in installing process ", e, project)
                        // Nothing re-triggers the download once the user fixes the cause (e.g. trusts
                        // the corporate CA in Settings > Tools > Server Certificates), so offer it here (#1976).
                        val failureText = if (installsComponent) "Failed to install $component" else "Could not remove the previous installation"
                        NotificationService.notifyError(
                            "$failureText: ${e.message}",
                            project,
                            NotificationAction.createSimple("Retry installation") { installOrUpdate(project) }
                        )
                    }
                }
                
                if (failedComponents.isEmpty()) {
                    Logger.log("Xygeni plugin installed on ${PluginContext().installDir}", project)
                } else {
                    Logger.error(
                        "Xygeni installation incomplete — failed: ${failedComponents.joinToString(", ")}. " +
                            "Fix the cause and retry from Tools > Xygeni > Install.",
                        project
                    )
                }
                
                if (installedComponents.isNotEmpty()) {
                    val openMcpSetup = project?.let { p ->
                        NotificationAction.createSimple(MyBundle.message("mcp.setup.notification.openLink")) {
                            McpSetupView.show(p)
                        }
                    }
                    NotificationService.notifyInfo(
                        "Xygeni installation completed: ${installedComponents.joinToString(", ")}",
                        project,
                        openMcpSetup
                    )
                    // Notify state change if scanner was installed
                    if (installedComponents.any { it.contains("scanner", ignoreCase = true) }) {
                        publishInstallerState(project)
                    }
                }
            }
        })
    }

}
