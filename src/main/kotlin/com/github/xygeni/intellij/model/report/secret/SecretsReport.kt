package com.github.xygeni.intellij.model.report.secret

import com.github.xygeni.intellij.model.report.RawIssueLocation
import com.github.xygeni.intellij.model.report.RawReportMetadata
import kotlinx.serialization.Serializable

/**
 * SecretsReport
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/
@Serializable
data class SecretsReport(
    val metadata: RawReportMetadata,
    val secrets: List<RawSecret>,
    val currentBranch: String?
)

@Serializable
data class RawSecret(
    val issueId: String,
    val type: String,
    val secret: String? = null,
    val key: String? = null,
    val hash: String? = null,
    val detector: String? = null,
    val severity: String = "high",
    val confidence: String = "high",
    val resource: String? = "",
    val location: RawIssueLocation? = null,
    val tags: List<String>? = null,
)

fun RawSecret.toIssue(toolName: String?, branch: String? ): SecretsXygeniIssue {
    val loc = this.location

    return SecretsXygeniIssue(
        id = issueId,
        type = type,
        detector = detector ?: "",
        tool = toolName,
        severity = severity,
        confidence = confidence,
        resource = resource ?: "",
        foundBy = detector ?: "",
        file = loc?.filepath ?: "",
        beginLine = loc?.beginLine ?: 0,
        endLine = loc?.endLine ?: 0,
        beginColumn = loc?.beginColumn ?: 0,
        endColumn = loc?.endColumn ?: 0,
        code = loc?.code ?: "",
        explanation = "Secret of type '$type' detected by '$detector'",
        tags = tags ?: emptyList() ,
        secret = secret ?: "",
        timeAdded = loc?.timeAdded ?: 0L,
        branch = branch ?: "",
        commitHash = hash ?: "",
        user = loc?.user ?: ""
    )
}