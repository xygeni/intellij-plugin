package com.github.xygeni.intellij.model.report.quality

import com.github.xygeni.intellij.model.report.RawIssueLocation
import com.github.xygeni.intellij.model.report.RawReportMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*

/**
 * QualityReport — parsing of `quality.<report-suffix>.json`.
 *
 * Code Quality reuses the SAST scanner infra (QualityScanConfigLoader ->
 * SastScanConfig), so the JSON shape parallels SAST: findings live under the
 * top-level `vulnerabilities` key (CONFIRMED against a real `quality.*.json`).
 * [parseQualityReport] keeps one narrow fallback (`qualityIssues`) for forward-compat.
 **/
@Serializable
data class QualityReport(
    val metadata: RawReportMetadata,
    val vulnerabilities: List<RawQuality>,
    val currentBranch: String? = null
)

@Serializable
data class RawQuality(
    val issueId: String,
    val detector: String? = null,
    val kind: String? = null,
    val type: String? = null,
    /** Quality rule family. */
    val category: String? = null,
    val severity: String = "",
    val location: RawIssueLocation? = null,
    val language: String? = null,
    val explanation: String? = null,
    val tags: List<String>? = null,

    @Transient
    val raw: JsonObject? = null
)

fun parseQualityReport(jsonString: String): QualityReport {

    val json = Json {
        ignoreUnknownKeys = true
    }

    val root = json.parseToJsonElement(jsonString).jsonObject

    val metadata = json.decodeFromJsonElement<RawReportMetadata>(
        root["metadata"]!!
    )

    val currentBranch = root["currentBranch"]
        ?.jsonPrimitive
        ?.contentOrNull

    // Confirmed key `vulnerabilities` (see QualityReportParseTest); narrow fallback only.
    val array = (root["vulnerabilities"] ?: root["qualityIssues"])
        ?.jsonArray
        ?: JsonArray(emptyList())

    val vulnerabilities = array.map { element ->
        val obj = element.jsonObject
        json.decodeFromJsonElement<RawQuality>(obj).copy(raw = obj)
    }

    return QualityReport(
        metadata = metadata,
        vulnerabilities = vulnerabilities,
        currentBranch = currentBranch
    )
}

fun RawQuality.toIssue(toolName: String?, currentBranch: String?): QualityXygeniIssue {
    val loc = this.location

    return QualityXygeniIssue(
        id = issueId,
        category = "quality",
        categoryName = "Code Quality",
        kind = "quality_issue",
        detector = detector ?: "",
        tool = toolName,
        severity = severity,
        file = loc?.filepath ?: "",
        beginLine = loc?.beginLine ?: 0,
        endLine = loc?.endLine ?: 0,
        beginColumn = loc?.beginColumn ?: 0,
        endColumn = loc?.endColumn ?: 0,
        code = loc?.code ?: "",
        explanation = explanation ?: "",
        tags = tags ?: emptyList(),
        branch = currentBranch ?: "",
        language = language ?: "",
        // Real reports carry the quality dimension in `kind` (e.g. "reliability");
        // `category` is only present in older/other shapes → fall back to it.
        qualityCategory = category ?: kind ?: "",
        type = (type ?: kind) ?: "",
        vulnerabilityRaw = raw
    )
}
