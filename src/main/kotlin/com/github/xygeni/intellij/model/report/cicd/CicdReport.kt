package com.github.xygeni.intellij.model.report.cicd

import com.github.xygeni.intellij.model.report.RawIssueLocation
import com.github.xygeni.intellij.model.report.RawReportMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * CicdReport
 *
 * @author : Carmendelope
 * @version : 16/10/25 (Carmendelope)
 **/
@Serializable
data class CicdReport(
    val metadata: RawReportMetadata,
    val misconfigurations: List<CicdRaw>,
    val currentBranch: String?
)

@Serializable
data class CicdRaw(
    val issueId: String,
    val type: String? = null,
    val detector: String? = null,
    val severity: String = "high",
    val confidence: String = "high",
    val location: RawIssueLocation? = null,
    val tags: List<String>? = null,
    val explanation: String? = null,
    val properties: Map<String, JsonElement>? = null
)

fun CicdRaw.toIssue(toolName: String?, branch: String?): CicdXygeniIssue {
    val loc = this.location
    return CicdXygeniIssue(
        id = issueId,
        type = type ?: "",
        detector = detector ?: "",
        tool = toolName,
        severity = severity ?: "",
        confidence = confidence ?: "",
        explanation = explanation ?: "",
        tags = tags ?: emptyList(),
        // -- location
        file = loc?.filepath ?: "",
        beginLine = loc?.beginLine ?: 0,
        endLine = loc?.endLine ?: 0,
        beginColumn = loc?.beginColumn ?: 0,
        endColumn = loc?.endColumn ?: 0,
        code = loc?.code ?: "",
        where = branch ?: "",
        properties = properties ?: emptyMap()
    )
}