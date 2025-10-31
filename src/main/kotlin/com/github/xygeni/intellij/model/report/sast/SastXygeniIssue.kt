package com.github.xygeni.intellij.model.report.sast

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.github.xygeni.intellij.model.report.server.RemediationData
import kotlinx.serialization.Serializable

/**
 * SastXygeniIssue
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/

@Serializable
data class SastXygeniIssue(
    override  val id: String,
    override val type: String,
    override val detector: String = "",
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "high",
    override val category: String = "",
    override val categoryName: String = "",
    override val file: String = "",
    override val beginLine: Int = 0,
    override val endLine: Int = 0,
    override val beginColumn: Int = 0,
    override val endColumn: Int = 0,
    override val code: String = "",
    override val explanation: String,
    override val tags: List<String> = emptyList(),
    override val kind: String = "sast",
    override val remediableLevel: String = "AUTO",

    // -- Sast --
    // val kind: String = "",
    val branch: String = "",
    val cwe: Int = 0,
    val cwes: List<String> = emptyList(),
    val language: String = "",
): BaseXygeniIssue{

    override fun toRemediationData(): RemediationData {

        return RemediationData(
            kind = category, // sast
            detector = detector,
            filePath = file,
            dependency = null,
            line = beginLine
        )
    }
}
