package com.github.xygeni.intellij.model.report

import com.github.xygeni.intellij.services.server.ServerClient
import icons.Icons
import javax.swing.Icon

/**
 * XygeniIssue
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/

interface BaseXygeniIssue {
    val id: String
    val kind: String
    val type: String
    val detector: String
    val tool: String?
    val severity: String
    val confidence: String
    val category: String
    val categoryName: String
    val file: String
    val beginLine: Int
    val endLine: Int
    val beginColumn: Int
    val endColumn: Int
    val code: String
    val explanation: String
    val tags: List<String>

    fun getIcon(): Icon {
        return when(severity.lowercase()) {
            "critical" -> Icons.CRITICAL_ICON
            "high" -> Icons.HIGH_ICON
            "info" -> Icons.INFO_ICON
            else -> Icons.LOW_ICON
        }
    }

    fun fetchData() : String{
        println("--> $this")
        val data = ServerClient().getDetectorDetails(tool ?: "xygeni", kind, detector)
        return data
    }

}
