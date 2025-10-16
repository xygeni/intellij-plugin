package com.github.xygeni.intellij.model.report.iac

import com.github.xygeni.intellij.model.report.RawIssueLocation
import com.github.xygeni.intellij.model.report.RawReportMetadata
import kotlinx.serialization.Serializable

/**
 * IacReport
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/

@Serializable
data class IacReport(
    val metadata: RawReportMetadata,
    val flaws: List<IacRaw>
)

@Serializable
data class IacRaw(
    val issueId: String,
    val type: String? = null,
    val framework: String? = null,
    val detector: String? = null,
    val severity: String = "high",
    val confidence: String = "high",
    val resource: String? = "",
    val provider: String? = "",
    val location: RawIssueLocation? = null,
    val tags: List<String>? = null,
    val explanation: String? = null
)


fun IacRaw.toIssue(toolName: String?): IacXygeniIssue {
    val loc = this.location
    return IacXygeniIssue(
        id = issueId,
        type = type?: "",
        detector = detector,
        tool = toolName,
        severity = severity,
        confidence = confidence,
        explanation = explanation?: "",
        tags = tags?: emptyList(),

        file = loc?.filepath ?: "",
        beginLine = loc?.beginLine ?: 0,
        endLine = loc?.endLine ?: 0,
        beginColumn = loc?.beginColumn ?: 0,
        endColumn = loc?.endColumn ?: 0,
        code = loc?.code ?: "",

        resource = resource?: "",
        provider = provider ?: "",
        framework = framework?:""
    )
}