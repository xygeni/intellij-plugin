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
    val vulnerabilities: List<RawSast>
)

@Serializable
data class RawSast (
    val issueId: String,
    val type: String? = null,
    val hash: String? = null,
    val detector: String? = null,
    val severity: String = "high",
    val confidence: String = "high",
    val resource: String? = "",
    val location: RawIssueLocation? = null,
    val tags: List<String>? = null,
    val url: String? = null,
    val explanation: String? = null,
    val currentBranch: String? = null,
    val cwe: Int? = null,
    val cwes: List<String>? = null,
    val container: String? = null,
    val kind: String? = null,
    val language: String? = null
)

fun RawSast.toIssue(toolName: String?): SastXygeniIssue {
    val loc = this.location

    return SastXygeniIssue(
        id = issueId,
        type = type?: "",
        detector = detector,
        tool = toolName,
        severity = severity,
        confidence = confidence,
        file = loc?.filepath ?: "",
        beginLine = loc?.beginLine ?: 0,
        endLine = loc?.endLine ?: 0,
        beginColumn = loc?.beginColumn ?: 0,
        endColumn = loc?.endColumn ?: 0,
        code = loc?.code ?: "",
        explanation = explanation?: "",
        tags = tags?: emptyList(),
        url = url ?: "",
        branch = currentBranch ?: "",
        cwe = cwe ?: 0,
        cwes = cwes ?: emptyList(),
        container = container ?: "",
        language = language ?: "",
        kind = kind ?: ""
    )
}