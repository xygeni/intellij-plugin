package com.github.xygeni.intellij.model.report.sast

import com.github.xygeni.intellij.model.report.RawIssueLocation
import com.github.xygeni.intellij.model.report.RawReportMetadata
import kotlinx.serialization.Serializable

/**
 * RawSast
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/

@Serializable
data class SastReport(
    val metadata: RawReportMetadata,
    val vulnerabilities: List<RawSast>,
    val currentBranch: String? = null
)

@Serializable
data class RawSast (
    val issueId: String,
    val detector: String? = null,
    val kind: String? = null,
    val type: String? = null,
    val severity: String = "",
    val location: RawIssueLocation? = null,
    val language: String? = null,
    val cwes: List<String>? = null,
    val cwe: Int? = null,
    val explanation: String? = null,
    val tags: List<String>? = null,
)

fun RawSast.toIssue(toolName: String?, currentBranch: String?): SastXygeniIssue {
    val loc = this.location

    return SastXygeniIssue(
        id = issueId,
        category = "sast",
        categoryName = "SAST",
        type = kind ?: "",
        detector = detector,
        tool = toolName,
        severity = severity,
        file = loc?.filepath ?: "",
        beginLine = loc?.beginLine ?: 0,
        endLine = loc?.endLine ?: 0,
        beginColumn = loc?.beginColumn ?: 0,
        endColumn = loc?.endColumn ?: 0,
        code = loc?.code ?: "",
        explanation = explanation?: "",
        tags = tags?: emptyList(),
        branch = currentBranch ?: "",
        cwe = cwe ?: 0,
        cwes = cwes ?: emptyList(),
        language = language ?: "",
        kind = kind ?: ""
    )
}