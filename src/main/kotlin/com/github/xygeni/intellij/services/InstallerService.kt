package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.events.CONNECTION_STATE_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.PluginContext
import com.github.xygeni.intellij.notifications.NotificationService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
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
import java.util.concurrent.TimeUnit
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

    private val pluginContext = PluginContext() //manager.pluginContext
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()


    private fun uninstallIfNeeded(project: Project? = null) {
        Logger.log("Removing previous installation: ${this.pluginContext.installDir.absolutePath}...", project)
        this.deleteSafe(this.pluginContext.installDir.absolutePath)
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

    fun publishConnectionState(project: Project, urlOk: Boolean, tokenOk: Boolean) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().messageBus
                .syncPublisher(CONNECTION_STATE_TOPIC)
                .connectionStateChanged(project, urlOk, tokenOk)
        }
    }

    private fun download(url: String, project: Project? = null): File? {
        val client = OkHttpClient()
        val fileName = url.substringAfterLast('/')

        return try {

            val request = Request.Builder()
                .url(url)
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
                this.deleteSafe(tempFile.absolutePath)

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

    // deleteSafe removes the path
    private fun deleteSafe(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) {
            file.deleteRecursively()
        } else {
            false
        }
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
        val jarFile = File(this.pluginContext.mcpJarFile)
        jarFile.parentFile.mkdirs()
        val f =
            download(this@InstallerService.pluginContext.mcpUrl + "", project)
        Logger.log("moving mcp to ${jarFile.absolutePath}", project)
        f?.copyTo(jarFile, true)
        Logger.log("Xygeni MCP installed successfully!", project)
    }

    private fun installScannerFn(project: Project?) {
        Logger.log("==================================", project)
        Logger.log("== Running scanner installation ==", project)
        Logger.log("==================================", project)
        val f = download(this@InstallerService.pluginContext.scriptUrl + "xygeni_scanner.zip", project)
        if (f != null) {
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
    }

    private fun executeInBackground(
        project: Project?,
        steps: List<Step>
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Installing...") {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val installedComponents = mutableListOf<String>()
                
                for (step in steps) {
                    try {
                        indicator.text = step.message
                        step.fn(project)
                        installedComponents.add(step.message.replace("Installing ", ""))
                    } catch (e: Exception) {
                        Logger.error("error in installing process ", e, project)
                        NotificationService.notifyError(
                            "Failed to install ${step.message.replace("Installing ", "")}: ${e.message}",
                            project
                        )
                    }
                }
                
                Logger.log("Xygeni plugin installed", project)
                
                if (installedComponents.isNotEmpty()) {
                    NotificationService.notifyInfo(
                        "Xygeni installation completed: ${installedComponents.joinToString(", ")}",
                        project
                    )
                }
            }
        })
    }

    // isInstalled checks if the xygeni command already exists
    private fun isInstalled(mcp: Boolean = false): Boolean {
        if (!mcp) return File(this.pluginContext.xygeniCommand).exists()
        // else (MCP)
        return File(this.pluginContext.mcpJarFile).exists()
    }

    // install checks the xygeni command and install if it does not exist
    fun install(project: Project?) {
        var steps = mutableListOf<Step>()
        if (!isInstalled()) {
            steps.add(Step("Installing scanner", this::installScannerFn,))
        } else {
            Logger.log(">> Xygeni installed", project)
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

    // installOrUpdate forces a new installation removing the previous one
    fun installOrUpdate(project: Project?) {
        uninstallIfNeeded(project)
        install(project)
    }

}
