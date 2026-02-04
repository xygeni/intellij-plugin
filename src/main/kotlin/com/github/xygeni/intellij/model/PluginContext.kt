package com.github.xygeni.intellij.model

/**
 * PluginContext
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/

import com.github.xygeni.intellij.logger.Logger
import com.intellij.openapi.project.Project
import java.io.File

class PluginContext (){

    val installDir: File
    val scriptFiletName: String
    val scriptUrl = "https://get.xygeni.io/latest/scanner/"
    val xygeniCommand: String
    val xygeniReportSuffix = "xygeni.plugin.json"
    val mcpJarFile: String
    val mcpUrl = "https://get.xygeni.io/latest/mcp-server/xygeni-mcp-server.jar"

    init {
        installDir = File(this.initInstallationDir(), PluginInfo.name + "/" + PluginInfo.version)
        scriptFiletName = this.initScriptFileName()
        ensureTargetExists(installDir)
        xygeniCommand = "${installDir.canonicalPath}/xygeni_scanner/${getXygeniCommandName()}" // installDir.canonicalPath + "/xygeni_scanner/xygeni"
        Logger.log("Xygeni command: $xygeniCommand", null)
        mcpJarFile = installDir.canonicalPath + "/mcp/xygeni-mcp-server.jar"
    }

    private fun initInstallationDir(): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.xygeni/plugins/"
    }

    private fun getXygeniCommandName(): String {
        var xygeniCommand = "xygeni"
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        if (isWindows){
            xygeniCommand += ".ps1"
        }
        return xygeniCommand
    }

    private fun initScriptFileName(): String {
        val osName = System.getProperty("os.name").lowercase()
        val osType = when {
            osName.contains("win") -> "windows"
            osName.contains("mac") -> "macos"
            osName.contains("nux") || osName.contains("nix") -> "linux"
            else -> "unknown"
        }
        return when (osType) {
            "windows" -> "install.ps1"
            "macos", "linux" -> "install.sh"
            else -> "unknown"
        }
    }

    private fun getScanDir (project: Project): File {
        return File(project.basePath, ".idea/.xygeni/scan")
    }

    fun cleanScanResultDir(project : Project): File{
        val dir = getScanDir(project)
        if (dir.exists() ){
            //dir.deleteRecursively()
        }
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