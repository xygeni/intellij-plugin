package com.github.xygeni.intellij.model

/**
 * PluginContext
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/

import com.intellij.openapi.project.Project
import java.io.File

class PluginContext (){

    val installDir: File
    val scriptFiletName: String
    val scriptUrl = "https://get.xygeni.io/latest/scanner/"
    val xygeniCommand: String
    val xygeniReportSuffix = "xygeni.plugin.json"

    init {
        installDir = File(this.initInstallationDir(), PluginInfo.name + "/" + PluginInfo.version)
        scriptFiletName = this.initScriptFileName()
        ensureTargetExists(installDir)
        xygeniCommand = installDir.canonicalPath + "/xygeni"

    }

    private fun initInstallationDir(): String {
        val userHome = System.getProperty("user.home")
        return "$userHome/.xygeni/plugins/"
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

    fun ensureScanResultDirExists(project : Project) : File{
        val scanResultDir = File(project.basePath, ".idea/.xygeni/scan")
        if (!scanResultDir.exists()) scanResultDir.mkdirs()
        return scanResultDir
    }

    private fun ensureTargetExists(target: File) {
        if (!target.exists() && !target.mkdirs()) {
            throw IllegalStateException("error creating plugin dir: ${target.absolutePath}")
        }
    }



}