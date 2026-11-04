package com.github.xygeni.intellij.services

import com.github.xygeni.intellij.MyBundle
import com.github.xygeni.intellij.events.CONNECTION_STATE_TOPIC
import com.github.xygeni.intellij.events.INSTALLER_STATE_TOPIC
import com.github.xygeni.intellij.events.MCP_STATE_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.model.PluginContext
import com.github.xygeni.intellij.notifications.NotificationService
import com.github.xygeni.intellij.settings.McpUpdateMode
import com.github.xygeni.intellij.settings.XygeniSettings
import com.github.xygeni.intellij.views.McpSetupView
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.FileVisitResult
import java.nio.file.LinkOption
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException
import java.security.MessageDigest
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

    companion object {
        /** What the user must do after an MCP update, in their words: the plugin only replaces the JAR, the assistant owns the process. */
        const val MCP_RESTART_NOTE = "Your AI assistant (Copilot, Claude Code, Cursor…) will use it the next time it starts: if it is open now, restart it."

        const val MCP_LOCK_NAME = ".lock"
        const val MCP_LOCK_STALE_MS = 10 * 60 * 1000L
        const val MCP_LOCK_WAIT_MS = 2 * 60 * 1000L
        /** The remote version is checked at most this often, whichever Xygeni IDE plugin did the last check. */
        const val MCP_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        /** The scanner ships ~245 jars; fewer than this means a truncated download or extraction. */
        const val MIN_SCANNER_JARS = 50
        const val PING_ATTEMPTS = 3
        const val PING_RETRY_MS = 3000L
    }

    private val pluginContext = service<PluginContext>() 
    private val client: OkHttpClient = XygeniHttpClient.create(connectTimeoutSeconds = 15, readTimeoutSeconds = 30)
    // version.txt / .sha256 are tiny and checked at startup: fail fast rather than hold anything up.
    private val textClient: OkHttpClient = XygeniHttpClient.create(connectTimeoutSeconds = 5, readTimeoutSeconds = 10)


    private fun uninstallIfNeeded(project: Project? = null) {
        Logger.log("Removing previous installation: ${this.pluginContext.installDir.absolutePath}...", project)
        this.deleteRecursively(this.pluginContext.installDir.absolutePath)
    }

    private fun checkUrlAccessible(url: String, project: Project? = null, callback: (Boolean) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val validateUrl = if (url.endsWith("/")) "${url}ping" else "$url/ping"
            var accessible = false
            // A backend that is starting (a local one, a deploy) answers 5xx or refuses the connection for a few
            // seconds: retry before calling it disconnected, so the user does not have to click to reconnect.
            for (attempt in 1..PING_ATTEMPTS) {
                val transient = try {
                    val request = Request.Builder().url(validateUrl).get().build()
                    client.newCall(request).execute().use { response ->
                        Logger.log("=== Connection ready: ${response.code}", project)
                        // 503 = the server answered but is busy (its /ping reports 503 above a CPU threshold, see
                        // PingController.max.cpu): the URL is right, the token check decides. Only an unreachable
                        // host or a wrong path (404) make the URL invalid.
                        accessible = response.isSuccessful || response.code in 300..399 || response.code == 503
                        if (response.code == 503) Logger.log("=== The server is busy (503 on /ping); going on with the token check", project)
                        response.code >= 500 && response.code != 503
                    }
                } catch (e: Exception) {
                    Logger.log("=== Connection check failed: ${e.message}", project)
                    true
                }
                if (accessible || !transient || attempt == PING_ATTEMPTS) break
                Logger.log("=== Retrying the connection in ${PING_RETRY_MS / 1000}s ($attempt/$PING_ATTEMPTS)", project)
                Thread.sleep(PING_RETRY_MS)
            }

            SwingUtilities.invokeLater {
                callback(accessible)
            }
        }
    }

    /** Why the last token check failed when the server, not the token, was the problem; null otherwise. */
    @Volatile
    var lastConnectionProblem: String? = null
        private set

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
                    // The server is there but cannot answer (down behind a proxy, overloaded): not a bad token.
                    lastConnectionProblem = if (response.code >= 500) "The Xygeni server is unavailable (HTTP ${response.code}). Try again later" else null
                    response.isSuccessful || response.code in 300..399
                }
            } catch (e: Exception) {
                Logger.log("=== Token check failed: ${e.message}", project)
                lastConnectionProblem = "The Xygeni server is unreachable (${e.message}). Try again later"
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
            // Swallowing this left half-extracted scanners (a `lib` with 24 of 245 jars) reported as installed.
            Logger.error("error unzipping $zipFile", e, project)
            throw IllegalStateException("could not extract the scanner: ${e.message}", e)
        }
    }

    // downloadAndInstall downloads xygeni file zip and decompresses

    data class Step(
        val message: String,
        val fn: (Project?) -> Unit
    )

    /** What the last MCP server version check found; shown in the MCP Setup view. */
    data class McpUpdateCheck(val installed: String?, val available: String?, val updateAvailable: Boolean)

    /** Tells the views the MCP state changed (installed, updated, restart note dismissed). */
    fun publishMcpState() {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().messageBus.syncPublisher(MCP_STATE_TOPIC).mcpStateChanged()
        }
    }

    /** The user restarted (or never had running) the AI assistant: the "restart pending" note goes away. */
    fun dismissMcpRestart() {
        XygeniSettings.getInstance().mcpRestartPending = null
        publishMcpState()
    }

    /** The notification after an update: what happened, what to do, with "Show me how" (the setup guide) and OK (hides the note). */
    private fun notifyMcpUpdated(version: String, project: Project?) {
        XygeniSettings.getInstance().mcpRestartPending = version
        publishMcpState()
        val actions = mutableListOf<NotificationAction>()
        project?.let { p -> actions.add(NotificationAction.createSimple("Show me how") { McpSetupView.show(p) }) }
        actions.add(NotificationAction.createSimpleExpiring("OK") { dismissMcpRestart() })
        NotificationService.notify(
            StringUtil.escapeXmlEntities("Xygeni MCP server updated to $version. $MCP_RESTART_NOTE"),
            NotificationType.INFORMATION, project, actions
        )
    }

    private enum class McpDownloadOutcome { INSTALLED, PENDING }

    @Volatile
    var lastMcpUpdateCheck: McpUpdateCheck? = null
        private set

    /** The version written next to the installed JAR, or null when the JAR is missing or predates version.txt. */
    fun installedMcpVersion(): String? {
        if (!this.pluginContext.mcpJarFile.exists()) return null
        return readTextOrNull(this.pluginContext.mcpVersionFile)?.let { McpVersion.fromVersionFile(it) }
    }

    // installMCPFn installs the MCP server in the folder shared by every Xygeni IDE plugin: when it is
    // missing, or always with `force` (reinstall)
    private fun installMCPFn(project: Project?, force: Boolean) {
        Logger.log("==================================", project)
        Logger.log("== Running MCP installation ==", project)
        Logger.log("==================================", project)
        val mcpJarFile = this.pluginContext.mcpJarFile
        withMcpLock {
            // Another IDE may have installed it while this one waited for the lock.
            if (!force && mcpJarFile.exists()) return@withMcpLock
            when (downloadMcpJarLocked(project)) {
                McpDownloadOutcome.INSTALLED -> { Logger.log("Xygeni MCP installed successfully!", project); publishMcpState() }
                McpDownloadOutcome.PENDING -> Logger.warn("Xygeni MCP update downloaded; it is applied on the next start.", project)
            }
        }
        removeLegacyMcpInstalls(project)
    }

    /**
     * Downloads the published JAR and puts it in place. Runs under the MCP lock. The remote `version.txt` is
     * read first and written next to the JAR once it is installed; the download is verified against the
     * published `.sha256` when that file is available, and a mismatch keeps the JAR already installed (#2382).
     * On Windows a running MCP client locks the JAR: the download is then kept as `.pending` and applied on
     * the next start.
     */
    private fun downloadMcpJarLocked(project: Project?): McpDownloadOutcome {
        val settings = XygeniSettings.getInstance()
        val mcpJarFile = this.pluginContext.mcpJarFile
        val remoteVersion = fetchText(settings.getMcpVersionUrl(), project)?.let { McpVersion.fromVersionFile(it) }
        Logger.log("MCP server published version: ${remoteVersion ?: "unknown"}", project)

        val f = downloadFile(settings.getMcpDownloadUrl(), this.pluginContext.mcpJarFileName, settings.apiToken ?: "", project)
            ?: throw IllegalStateException("could not download ${settings.getMcpDownloadUrl()}")
        try {
            val expectedChecksum = fetchText(settings.getMcpChecksumUrl(), project)?.let { McpVersion.fromChecksumFile(it) }
            if (expectedChecksum == null) {
                Logger.warn("MCP server checksum is not available at ${settings.getMcpChecksumUrl()}; the download is not verified", project)
            } else {
                val actualChecksum = sha256(f)
                if (actualChecksum != expectedChecksum) {
                    throw IllegalStateException("MCP server checksum mismatch (expected $expectedChecksum, got $actualChecksum); the installed copy is kept")
                }
                Logger.log("MCP server checksum verified", project)
            }

            Logger.log("moving mcp to $mcpJarFile", project)
            // Copy next to the JAR and rename it into place, so an MCP client never runs a half-written JAR.
            val temporaryJar = File(mcpJarFile.parentFile, mcpJarFile.name + ".tmp").toPath()
            f.copyTo(temporaryJar.toFile(), true)
            try {
                Files.move(temporaryJar, mcpJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: IOException) {
                if (!mcpJarFile.exists()) {
                    Files.deleteIfExists(temporaryJar)
                    throw e
                }
                // On Windows a running MCP client locks the JAR; the copy already installed keeps working and the
                // update waits as `.pending` until the next start.
                Files.move(temporaryJar, this.pluginContext.mcpPendingJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                writeVersionFile(this.pluginContext.mcpPendingVersionFile, remoteVersion)
                Logger.warn("Could not replace $mcpJarFile (${e.message}). Close your MCP client; the update is applied on the next start.", project)
                return McpDownloadOutcome.PENDING
            }
            writeVersionFile(this.pluginContext.mcpVersionFile, remoteVersion)
            discardPendingMcpUpdate()
            return McpDownloadOutcome.INSTALLED
        } finally {
            Files.deleteIfExists(f.toPath())
        }
    }

    /** Puts a `.pending` download in place once the MCP client that locked the JAR is gone. Under the MCP lock. */
    private fun applyPendingMcpUpdateLocked(project: Project?): Boolean {
        val pendingJar = this.pluginContext.mcpPendingJarFile
        if (!pendingJar.exists()) return false
        return try {
            Files.move(pendingJar.toPath(), this.pluginContext.mcpJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            val pendingVersion = readTextOrNull(this.pluginContext.mcpPendingVersionFile)?.let { McpVersion.fromVersionFile(it) }
            writeVersionFile(this.pluginContext.mcpVersionFile, pendingVersion)
            Files.deleteIfExists(this.pluginContext.mcpPendingVersionFile.toPath())
            Logger.log("Pending Xygeni MCP server update applied (${pendingVersion ?: "unknown version"})", project)
            true
        } catch (e: IOException) {
            Logger.warn("Pending Xygeni MCP server update not applied yet, the JAR is still in use: ${e.message}", project)
            false
        }
    }

    private fun discardPendingMcpUpdate() {
        Files.deleteIfExists(this.pluginContext.mcpPendingJarFile.toPath())
        Files.deleteIfExists(this.pluginContext.mcpPendingVersionFile.toPath())
    }

    /**
     * Checks whether a newer MCP server is published and acts on the "MCP server updates" setting: notify
     * (Update / Ignore this version), update in the background, or nothing. Runs at most once every 24 h
     * across IDEs unless `force` (the manual action, which also reports "up to date" and failures). A failed
     * check (no network, 403 while the version file is not published) is only logged (#2382).
     */
    fun checkMcpUpdate(project: Project?, force: Boolean = false) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                doCheckMcpUpdate(project, force)
            } catch (e: Exception) {
                Logger.error("MCP server update check failed", e, project)
                if (force) NotificationService.notifyWarn(StringUtil.escapeXmlEntities("Could not check for MCP server updates: ${e.message}"), project)
            }
        }
    }

    private fun doCheckMcpUpdate(project: Project?, force: Boolean) {
        val settings = XygeniSettings.getInstance()
        if (!isInstalled(mcp = true)) {
            if (force) NotificationService.notifyWarn("The Xygeni MCP server is not installed yet. Run Tools > Xygeni > Install first.", project)
            return
        }
        if (this.pluginContext.mcpPendingJarFile.exists()) {
            val applied = withMcpLock { applyPendingMcpUpdateLocked(project) }
            if (applied) notifyMcpUpdated(installedMcpVersion() ?: "the downloaded version", project)
        }
        val mode = settings.mcpUpdateMode
        if (!force && mode == McpUpdateMode.OFF) return
        if (!force && !isMcpCheckDue()) return

        val remoteVersion = fetchText(settings.getMcpVersionUrl(), project)?.let { McpVersion.fromVersionFile(it) }
        if (remoteVersion == null) {
            Logger.log("MCP server version not available at ${settings.getMcpVersionUrl()}; update check skipped", project)
            if (force) NotificationService.notifyWarn("Could not check for MCP server updates: the published version is not available.", project)
            return
        }
        writeTextQuietly(this.pluginContext.mcpLastCheckFile, System.currentTimeMillis().toString())

        val installedVersion = installedMcpVersion()
        val check = McpUpdateCheck(installedVersion, remoteVersion, McpVersion.isNewer(remoteVersion, installedVersion))
        lastMcpUpdateCheck = check
        publishMcpState()
        Logger.log("MCP server installed: ${installedVersion ?: "unknown"}, published: $remoteVersion", project)
        if (!check.updateAvailable) {
            if (force) NotificationService.notifyInfo("The Xygeni MCP server is up to date ($remoteVersion).", project)
            return
        }
        if (!force && settings.mcpIgnoredVersion == remoteVersion) {
            Logger.log("MCP server $remoteVersion is ignored by the user", project)
            return
        }
        if (!force && mode == McpUpdateMode.AUTO) {
            updateMcp(project)
            return
        }
        val message = "A new version of the Xygeni MCP server is available (${installedVersion ?: "unknown"} → $remoteVersion)."
        NotificationService.notify(
            StringUtil.escapeXmlEntities(message),
            NotificationType.INFORMATION,
            project,
            listOf(
                NotificationAction.createSimpleExpiring("Update") { updateMcp(project) },
                NotificationAction.createSimpleExpiring("Ignore this version") {
                    settings.mcpIgnoredVersion = remoteVersion
                    Logger.log("MCP server $remoteVersion will not be offered again", project)
                }
            )
        )
    }

    /** Downloads the published MCP server over the installed one, in the background, and says when it is loaded. */
    fun updateMcp(project: Project?) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Updating Xygeni MCP server...") {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    val outcome = withMcpLock { downloadMcpJarLocked(project) }
                    val version = installedMcpVersion() ?: "the published version"
                    lastMcpUpdateCheck = lastMcpUpdateCheck?.let { McpUpdateCheck(installedMcpVersion(), it.available, outcome == McpDownloadOutcome.PENDING) }
                    val openMcpSetup = project?.let { p ->
                        NotificationAction.createSimple(MyBundle.message("mcp.setup.notification.openLink")) { McpSetupView.show(p) }
                    }
                    when (outcome) {
                        McpDownloadOutcome.INSTALLED -> notifyMcpUpdated(version, project)
                        McpDownloadOutcome.PENDING -> NotificationService.notifyWarn(
                            "The Xygeni MCP server update was downloaded but the installed JAR is in use. Close your MCP client; " +
                                "the update is applied the next time the IDE starts.", project, openMcpSetup
                        )
                    }
                } catch (e: Exception) {
                    Logger.error("error updating the MCP server", e, project)
                    NotificationService.notifyError(
                        StringUtil.escapeXmlEntities("Failed to update the Xygeni MCP server: ${e.message}"),
                        project,
                        NotificationAction.createSimple("Retry") { updateMcp(project) }
                    )
                }
            }
        })
    }

    private fun isMcpCheckDue(): Boolean {
        val lastCheck = readTextOrNull(this.pluginContext.mcpLastCheckFile)?.trim()?.toLongOrNull() ?: return true
        return System.currentTimeMillis() - lastCheck > MCP_CHECK_INTERVAL_MS
    }

    /** GET a small text resource (version, checksum); null on any failure, which is logged and never thrown. */
    private fun fetchText(url: String, project: Project?): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            textClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.log("GET $url: ${response.code}", project)
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            Logger.log("GET $url failed: ${e.message}", project)
            null
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readTextOrNull(file: File): String? = try {
        if (file.exists()) file.readText() else null
    } catch (e: IOException) {
        null
    }

    private fun writeTextQuietly(file: File, content: String) {
        try {
            file.writeText(content)
        } catch (e: IOException) {
            Logger.warn("Could not write $file: ${e.message}")
        }
    }

    /** Writes the version next to the JAR; an unknown version removes the file so the next check updates it. */
    private fun writeVersionFile(file: File, version: String?) {
        if (version == null) Files.deleteIfExists(file.toPath()) else writeTextQuietly(file, version + "\n")
    }

    // removeLegacyMcpInstalls deletes the MCP folders of versions before #2382. The path in the user's MCP
    // client config still points to one of them, so they are told to update it.
    private fun removeLegacyMcpInstalls(project: Project?) {
        val legacyMcpDirs = this.pluginContext.legacyMcpInstallDirs()
        if (legacyMcpDirs.isEmpty() || !this.pluginContext.mcpJarFile.exists()) return
        for (legacyMcpDir in legacyMcpDirs) {
            Logger.log("Removing previous MCP server folder: $legacyMcpDir", project)
            try {
                this.deleteRecursively(legacyMcpDir.absolutePath)
            } catch (e: IOException) {
                // An MCP client still running the old JAR locks it on Windows; it is retried on the next start.
                Logger.warn("Could not remove $legacyMcpDir: ${e.message}", project)
            }
        }
        val message = "The Xygeni MCP server moved to ${this.pluginContext.mcpJarFile.absolutePath}. " +
            "Update the path in your MCP client configuration."
        Logger.warn(message, project)
        NotificationService.notifyWarn(
            StringUtil.escapeXmlEntities(message),
            project,
            project?.let { p ->
                NotificationAction.createSimple(MyBundle.message("mcp.setup.notification.openLink")) { McpSetupView.show(p) }
            }
        )
    }

    // withMcpLock runs `action` holding <mcp dir>/.lock, created atomically: the MCP folder is shared by every
    // Xygeni IDE plugin. A lock held by another IDE is waited for; a stale one (crashed IDE) is taken over.
    private fun <T> withMcpLock(action: () -> T): T {
        val lockFile = File(this.pluginContext.mcpInstallDir, MCP_LOCK_NAME).toPath()
        val waitUntil = System.currentTimeMillis() + MCP_LOCK_WAIT_MS
        while (true) {
            try {
                Files.createFile(lockFile)
                break
            } catch (e: FileAlreadyExistsException) {
                val lockAgeMs = try {
                    System.currentTimeMillis() - Files.getLastModifiedTime(lockFile).toMillis()
                } catch (e: NoSuchFileException) {
                    continue
                }
                when {
                    lockAgeMs > MCP_LOCK_STALE_MS -> Files.deleteIfExists(lockFile)
                    System.currentTimeMillis() > waitUntil ->
                        throw IllegalStateException("MCP server is being installed by another IDE ($lockFile)")
                    else -> Thread.sleep(1000)
                }
            }
        }
        try {
            return action()
        } finally {
            Files.deleteIfExists(lockFile)
        }
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
        // A scanner without its libraries cannot even run its updater: fail the install so it is retried.
        val libDir = File(commandFile.parentFile, "lib")
        val jars = libDir.listFiles { file -> file.name.endsWith(".jar") }?.size ?: 0
        if (jars < MIN_SCANNER_JARS) {
            throw IllegalStateException("incomplete scanner install: $jars jar(s) in ${libDir.absolutePath}")
        }
        Logger.log("Xygeni scanner installed successfully! ($jars libraries)", project)
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
            steps.add(Step("Installing mcp") { targetProject -> installMCPFn(targetProject, force = false) })
        } else {
            Logger.log(">> Xygeni MCP installed", project)
            // Another IDE may have installed the shared MCP server while this one still has its own copy.
            if (this.pluginContext.legacyMcpInstallDirs().isNotEmpty()) {
                steps.add(Step("Removing previous MCP server folder", this::removeLegacyMcpInstalls))
            }
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
            Step("Installing mcp") { targetProject -> installMCPFn(targetProject, force = true) },
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
                val failureMessages = mutableListOf<String>()
                
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
                        val failureText = if (installsComponent) "Failed to install $component" else "Could not remove the previous installation"
                        failureMessages.add(StringUtil.escapeXmlEntities("$failureText: ${e.message}"))
                    }
                }
                
                if (failedComponents.isEmpty()) {
                    Logger.log("Xygeni plugin installed on ${PluginContext().installDir}", project)
                } else {
                    // One notification for the whole run, however many steps failed. Nothing re-triggers the
                    // download once the user fixes the cause (e.g. trusts the corporate CA in Settings > Tools >
                    // Server Certificates), so it offers the retry (#1976).
                    NotificationService.notifyError(
                        failureMessages.joinToString("<br>"),
                        project,
                        NotificationAction.createSimple("Retry installation") { installOrUpdate(project) }
                    )
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
