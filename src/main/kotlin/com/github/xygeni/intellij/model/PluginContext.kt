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
        // An old versioned install is moved into it by InstallerService.install(), off the EDT.
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