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

    val installDir: File
    val xygeniCommand: String
    val scannerZipFileName = "xygeni_scanner.zip"
    val xygeniReportSuffix = "xygeni.plugin.json"
    val mcpJarFileName = "xygeni-mcp-server.jar"
    val mcpInstallDir: File
    val mcpJarFile: File

    init {
        // install dir
        installDir = File(this.initInstallationDir(), PluginInfo.name + "/" + PluginInfo.version)
        ensureTargetExists(installDir)

        // mcp data (install dir, file)
        mcpInstallDir = File(installDir, "mcp")
        ensureTargetExists(mcpInstallDir)
        mcpJarFile = File(mcpInstallDir, mcpJarFileName)

        // scanner dir -> it is created moving the scanner on unzip

        xygeniCommand = "${installDir.canonicalPath}/xygeni_scanner/${getXygeniCommandName()}"
       //  mcpJarFile = installDir.canonicalPath + "/mcp/xygeni-mcp-server.jar"

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