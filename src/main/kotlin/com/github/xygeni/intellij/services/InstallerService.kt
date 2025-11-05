package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.events.CONNECTION_STATE_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.PluginConfig
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
class InstallerService {

    private val manager = service<PluginManagerService>()
    private val pluginContext = manager.pluginContext
    private val executor = manager.executor
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun checkAndInstall(project: Project, config: PluginConfig) {
        if (!config.isValid()) {
            Logger.log("Configuration error")
            return
        }

        // Validamos URL y token
        validateConnection(config.url, config.token) { urlOk, tokenOk ->
            publishConnectionState(project, urlOk, tokenOk)

            when {
                !urlOk -> Logger.warn("URL ${config.url} no accessible")
                !tokenOk -> Logger.warn("Invalid token")
                else -> {
                    install(config)
                }
            }
        }
    }

    private fun uninstallIfNeeded() {
        Logger.log("Removing previous installation: ${this.pluginContext.installDir.absolutePath}...")
        this.deleteSafe(this.pluginContext.installDir.absolutePath)
    }

    private fun checkUrlAccessible(url: String, callback: (Boolean) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val accessible = try {
                val validateUrl = if (url.endsWith("/")) "${url}ping" else "$url/ping"
                val request = Request.Builder()
                    .url(validateUrl)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    Logger.log("=== Connection ready: ${response.code}")
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

    private fun checkTokenValid(apiUrl: String, token: String, callback: (Boolean) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val valid = try {
                val validateUrl = if (apiUrl.endsWith("/")) "${apiUrl}language" else "$apiUrl/language"

                val request = Request.Builder()
                    .url(validateUrl)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    Logger.log("=== Token authorized: ${response.code}")
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

    fun validateConnection(apiUrl: String, token: String, callback: (urlOk: Boolean, tokenOk: Boolean) -> Unit) {
        if (apiUrl.isBlank()) {
            SwingUtilities.invokeLater { callback(false, false) }
            return
        }
        Logger.log("===========================")
        Logger.log("== Validating connection ==")
        Logger.log("===========================")

        checkUrlAccessible(apiUrl) { urlOk ->
            if (!urlOk) {
                callback(false, false)
                return@checkUrlAccessible
            }

            if (token.isBlank()) {
                callback(true, false)
                return@checkUrlAccessible
            }

            checkTokenValid(apiUrl, token) { tokenOk ->
                callback(true, tokenOk)
            }
        }
    }

    private fun install(config: PluginConfig) {
        Logger.log("==================================")
        Logger.log("== Running scanner installation ==")
        Logger.log("==================================")
        uninstallIfNeeded()

        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Installing External App") {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val downloaded =
                        downloadScript(this@InstallerService.pluginContext.scriptUrl + this@InstallerService.pluginContext.scriptFiletName)
                    if (downloaded != null) {
                        downloaded.setExecutable(true)
                        this@InstallerService.executeScript(downloaded.absolutePath, config)
                    }
                } catch (e: Exception) {
                    Logger.error("installing error: ${e.message}")
                }
            }
        })
    }

    fun publishConnectionState(project: Project, urlOk: Boolean, tokenOk: Boolean) {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().messageBus
                .syncPublisher(CONNECTION_STATE_TOPIC)
                .connectionStateChanged(project, urlOk, tokenOk)
        }
    }

    private fun downloadScript(url: String): File? {
        val client = OkHttpClient()
        val fileName = url.substringAfterLast('/')

        return try {

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.warn("error downloading script from $url: ${response.code}")
                    return null
                }

                val body = response.body ?: run {
                    Logger.warn("Empty response downloading script from $url")
                    return null
                }

                val tempDir = File(System.getProperty("java.io.tmpdir"), "xygeni-plugin").apply { mkdirs() }
                val tempFile = File(tempDir, fileName)

                // delete the previous script
                this.deleteSafe(tempFile.absolutePath)

                Logger.log("Downloading install script from:  $url to: $tempFile")

                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                // tempFile.setExecutable(true)
                Logger.log("Script downloaded successfully: ${tempFile.absolutePath}")
                tempFile
            }
        } catch (e: Exception) {
            Logger.error("error downloading script $url", e)
            null
        }
    }

    private fun executeScript(scriptPath: String, config: PluginConfig) {

        val args = mutableMapOf<String, String>()
        if (!config.url.isBlank()) {
            args["-s"] = config.url
        }
        if (!config.token.isBlank()) {
            args["-t"] = config.token
        }
        args["-d"] = this.pluginContext.installDir.absolutePath
        args["-o"] = ""

        this.executor.executeProcess(scriptPath, args, null) {
            // TODO: añadir aquí lo q pueda necesuitar
        }

    }

    private fun deleteSafe(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) {
            file.deleteRecursively()
        } else {
            false
        }
    }

    private fun unzip(zipFile: Path, destDir: Path) {
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
            Logger.log("✅ Unzip finished:  ${elapsed}ms")
        }
        catch (e: Exception) {
            Logger.error("error unzipping $zipFile", e)
        }
    }

    // manualInstall downloads xygeni file zip and decompresses
    fun manualInstall (project: Project) {
        Logger.log("==================================")
        Logger.log("== Running scanner installation ==")
        Logger.log("==================================")
        Logger.log("PluginContext: ${pluginContext.xygeniCommand}")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Downloading App..") {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Downloading..."
                    // Download zip file
                    val f = downloadScript(this@InstallerService.pluginContext.scriptUrl + "xygeni_scanner.zip")
                    indicator.text = "Unzip file..."
                    if (f != null) {
                        unzip(f.toPath(), Paths.get( this@InstallerService.pluginContext.installDir.absolutePath))
                        // + x to xygeni command
                        val commandFile = File( this@InstallerService.pluginContext.xygeniCommand)
                        commandFile.setExecutable(true)
                    }
                }
                catch (e: Exception) {
                    Logger.error("error in installing process ", e)
                }

            }
        })

    }

}
