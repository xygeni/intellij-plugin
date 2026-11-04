package com.github.xygeni.intellij.model

/**
 * PluginContext
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.APP)
class PluginContext {

    companion object {
        /** The fixed scanner folder under `~/.xygeni/plugins/xygeni/`, independent of the plugin version. */
        const val SCANNER_DIR = "scanner"
        const val SCANNER_SUBDIR = "xygeni_scanner"
        private const val MIN_SCANNER_LIBS = 50
    }

    val installDir: File
    val xygeniCommand: String
    val scannerZipFileName = "xygeni_scanner.zip"
    val xygeniReportSuffix = "xygeni.plugin.json"
    val mcpJarFileName = "xygeni-mcp-server.jar"
    val mcpInstallDir: File
    val mcpJarFile: File
    /** Version of the installed MCP server JAR, as published in the remote `version.txt` (#2382). */
    val mcpVersionFile: File
    /** Epoch millis of the last remote version check, shared by every IDE so each one does not check on its own. */
    val mcpLastCheckFile: File
    /** A downloaded update that could not replace a JAR locked by a running MCP client; applied on the next start. */
    val mcpPendingJarFile: File
    val mcpPendingVersionFile: File

    init {
        // install dir — use the stable plugin id (no spaces) rather than the display name,
        // which is now "Xygeni Security" and would put a space in the scanner path used for shell-out.
        // The folder no longer carries the plugin version: every plugin update re-downloaded the ~250 MB
        // scanner into a new `<version>` folder, and a cut download left it half installed. Like the VS Code
        // extension, one fixed folder that survives updates; a previous versioned install is moved into it.
        installDir = File(this.initInstallationDir(), PluginInfo.id + "/" + SCANNER_DIR)
        migrateVersionedScannerInstall()
        ensureTargetExists(installDir)

        // mcp data (install dir, file): shared by every Xygeni IDE plugin and independent of the plugin
        // version, so the path the user pasted into their MCP client survives plugin updates (#2382)
        mcpInstallDir = File(System.getProperty("user.home"), ".xygeni/mcp")
        ensureTargetExists(mcpInstallDir)
        mcpJarFile = File(mcpInstallDir, mcpJarFileName)
        mcpVersionFile = File(mcpInstallDir, "version.txt")
        mcpLastCheckFile = File(mcpInstallDir, "last-check.txt")
        mcpPendingJarFile = File(mcpInstallDir, "$mcpJarFileName.pending")
        mcpPendingVersionFile = File(mcpInstallDir, "version.txt.pending")

        // scanner dir -> it is created moving the scanner on unzip

        xygeniCommand = "${installDir.canonicalPath}/$SCANNER_SUBDIR/${getXygeniCommandName()}"
       //  mcpJarFile = installDir.canonicalPath + "/mcp/xygeni-mcp-server.jar"

    }

    private fun initInstallationDir(): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.xygeni/plugins/"
    }

    /**
     * Moves the newest complete scanner of the old `~/.xygeni/plugins/xygeni/<plugin version>/` folders into the fixed
     * folder, so an update needs no download, and drops the other versioned copies (one scanner is 250 MB). A
     * scanner the Hub wired as a link is unlinked, never followed (#1976).
     */
    private fun migrateVersionedScannerInstall() {
        try {
            val pluginDir = File(initInstallationDir(), PluginInfo.id)
            val versioned = pluginDir.listFiles { file -> file.isDirectory && file.name != SCANNER_DIR && file.name.firstOrNull()?.isDigit() == true }
                .orEmpty().sortedByDescending { it.name }
            if (versioned.isEmpty()) return
            val target = File(installDir, SCANNER_SUBDIR)
            if (!java.nio.file.Files.exists(target.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                // A scanner the Hub wired as a link keeps pointing where the Hub put it: the link is recreated, not followed.
                val linked = versioned.map { File(it, SCANNER_SUBDIR).toPath() }.firstOrNull { java.nio.file.Files.isSymbolicLink(it) }
                val complete = versioned.map { File(it, SCANNER_SUBDIR) }
                    .firstOrNull { !java.nio.file.Files.isSymbolicLink(it.toPath()) && (File(it, "lib").listFiles()?.size ?: 0) >= MIN_SCANNER_LIBS }
                installDir.mkdirs()
                if (linked != null) {
                    java.nio.file.Files.createSymbolicLink(target.toPath(), java.nio.file.Files.readSymbolicLink(linked))
                } else if (complete != null) {
                    java.nio.file.Files.move(complete.toPath(), target.toPath())
                }
            }
            for (dir in versioned) {
                val old = File(dir, SCANNER_SUBDIR).toPath()
                if (java.nio.file.Files.isSymbolicLink(old)) java.nio.file.Files.deleteIfExists(old)
                else if (java.nio.file.Files.exists(old)) old.toFile().deleteRecursively()
                // The versioned folder is kept while it still holds something else (a legacy MCP copy, see #2382).
                if (dir.listFiles().isNullOrEmpty()) dir.delete()
            }
        } catch (e: Exception) {
            // A failed move only costs a download: the installer fills the fixed folder.
        }
    }

    /** MCP folders left by versions before #2382, one per installed plugin version: ~/.xygeni/plugins/<name>/<version>/mcp. */
    fun legacyMcpInstallDirs(): List<File> =
        File(initInstallationDir()).listFiles().orEmpty()
            .flatMap { pluginDir -> pluginDir.listFiles().orEmpty().toList() }
            .filter { it.name != SCANNER_DIR }
            .map { versionDir -> File(versionDir, "mcp") }
            .filter { legacyMcpDir -> File(legacyMcpDir, mcpJarFileName).exists() }

    private fun getXygeniCommandName(): String {
        var xygeniCommand = "xygeni"
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        if (isWindows){
            xygeniCommand += ".ps1"
        }
        return xygeniCommand
    }

    private fun getScanDir (project: Project): File {
        return File(project.basePath, ".idea/.xygeni/scan")
    }

    fun cleanScanResultDir(project : Project): File{
        val dir = getScanDir(project)
        //if (dir.exists()){
        //    dir.deleteRecursively()
        //}
        ensureTargetExists(dir)
        return dir
    }

    fun ensureScanResultDirExists(project : Project) : File{
        val scanResultDir = getScanDir(project)
        ensureTargetExists(scanResultDir)
        return scanResultDir
    }

    private fun ensureTargetExists(target: File) {
        if (!target.exists() && !target.mkdirs()) {
            throw IllegalStateException("error creating dir: ${target.absolutePath}")
        }
    }



}