package com.github.xygeni.intellij.model.report.cicd

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import kotlinx.serialization.Serializable
import kotlin.String

/**
 * CicdXygeniIssue
 *
 * @author : Carmendelope
 * @version : 16/10/25 (Carmendelope)
 **/
@Serializable
data class CicdXygeniIssue(
    override val id: String,
    override val type: String,
    override val detector: String? = null,
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "highest",
    override val category: String = "iac",
    override val categoryName: String = "IAC",
    override val file: String = "",
    override val explanation: String,
    override val tags: List<String> = emptyList(),
    override val url: String = "",

    // -- location
    override val beginLine: Int = 0,
    override val endLine: Int = 0,
    override val beginColumn: Int = 0,
    override val endColumn: Int = 0,
    override val code: String = "",

    // Cicd
    val properties: Map<String, String> = emptyMap()
) : BaseXygeniIssue

fun CicdRaw.toIssue(toolName: String?): CicdXygeniIssue {
    val loc = this.location
    return CicdXygeniIssue(
        id = issueId,
        type = type ?: "",
        detector = detector ?: "",
        tool = toolName,
        severity = severity ?: "",
        confidence = confidence ?: "",
        category = "cicd",
        categoryName = "CICD",
        explanation = explanation ?: "",
        tags = tags ?: emptyList(),
        // -- location
        file = loc?.filepath ?: "",
        beginLine = loc?.beginLine ?: 0,
        endLine = loc?.endLine ?: 0,
        beginColumn = loc?.beginColumn ?: 0,
        endColumn = loc?.endColumn ?: 0,
        code = loc?.code ?: "",

        //properties = properties ?: emptyMap()
        )
}