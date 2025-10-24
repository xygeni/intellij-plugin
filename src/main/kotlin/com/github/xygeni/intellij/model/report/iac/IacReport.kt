package com.github.xygeni.intellij.model.report.iac

import com.github.xygeni.intellij.model.report.RawIssueLocation
import com.github.xygeni.intellij.model.report.RawReportMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * IacReport
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/

@Serializable
data class IacReport(
    val metadata: RawReportMetadata,
    val flaws: List<IacRaw>,
    val currentBranch: String?
)

@Serializable
data class IacRaw(
    val issueId: String,
    val type: String? = null,
    val framework: String? = null,
    val detector: String? = null,
    val severity: String = "",
    val confidence: String? = null,
    val resource: String? = "",
    val provider: String? = "",
    val location: RawIssueLocation? = null,
    val tags: List<String>? = null,
    val explanation: String? = null,
    val properties: Map<String, JsonElement>? = null
)


fun IacRaw.toIssue(toolName: String?,  branch: String?): IacXygeniIssue {
    val loc = this.location
    return IacXygeniIssue(
        id = issueId,
        category = "Iac",
        categoryName = "IAC",
        type = type?: "",
        detector = detector ?: "",
        tool = toolName,
        severity = severity,
        confidence = confidence ?: "",
        explanation = explanation ?: "",
        tags = tags ?: emptyList(),

        file = loc?.filepath ?: "",
        beginLine = loc?.beginLine ?: 0,
        endLine = loc?.endLine ?: 0,
        beginColumn = loc?.beginColumn ?: 0,
        endColumn = loc?.endColumn ?: 0,
        code = loc?.code ?: "",

        properties = properties ?: emptyMap(),
        resource = resource?: "",
        provider = provider ?: "",
        framework = framework?:"",
        where = branch ?: ""
    )
}