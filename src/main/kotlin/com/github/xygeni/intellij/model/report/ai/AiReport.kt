package com.github.xygeni.intellij.model.report.ai

import com.github.xygeni.intellij.model.report.RawIssueLocation
import com.github.xygeni.intellij.model.report.RawReportMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*

/**
 * AiReport — parsing of `ai.<report-suffix>.json`.
 *
 * The AI report does NOT reuse the SAST field names. Its `getSeverity()`, `getIssueId()`,
 * `getDetector()` and `getExplanation()` are all @JsonIgnore on the backend model, so the
 * serialized keys are `severityFloor`, `id`, `detectorId` and `description`. Reading
 * `severity` / `issueId` / `detector` / `explanation` here would silently yield empty
 * values. There is no kind/type either — the detector id doubles as the finding's label.
 **/
@Serializable
data class AiReport(
    val metadata: RawReportMetadata,
    val vulnerabilities: List<RawAiVulnerability>,
    val currentBranch: String? = null
)

@Serializable
data class RawAiVulnerability(
    val id: String,
    val detectorId: String? = null,
    /** The severity: NOT `severity`. */
    val severityFloor: String = "",
    val confidence: String? = null,
    /** The explanation: NOT `explanation`. */
    val description: String? = null,
    val location: RawIssueLocation? = null,
    val assetKind: String? = null,
    val standards: List<RawAiStandard>? = null,
    val redTeamVectors: List<String>? = null,
    val remediationHint: String? = null,
    val tags: List<String>? = null,

    @Transient
    val raw: JsonObject? = null
)

/** One taxonomy mapping: `{ std, version, controlId }`. */
@Serializable
data class RawAiStandard(
    @SerialName("std") val std: String? = null,
    val version: String? = null,
    val controlId: String? = null
)

fun parseAiReport(jsonString: String): AiReport {

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

    val array = root["vulnerabilities"]
        ?.jsonArray
        ?: JsonArray(emptyList())

    val vulnerabilities = array.map { element ->
        val obj = element.jsonObject
        json.decodeFromJsonElement<RawAiVulnerability>(obj).copy(raw = obj)
    }

    return AiReport(
        metadata = metadata,
        vulnerabilities = vulnerabilities,
        currentBranch = currentBranch
    )
}

fun RawAiVulnerability.toIssue(toolName: String?, currentBranch: String?): AiXygeniIssue {
    val loc = this.location
    val detectorName = detectorId ?: ""

    return AiXygeniIssue(
        id = id,
        // No kind/type in this report: the detector id is the finding's label.
        type = detectorName,
        detector = detectorName,
        tool = toolName,
        severity = severityFloor,
        confidence = confidence ?: "high",
        file = loc?.filepath ?: "",
        beginLine = loc?.beginLine ?: 0,
        endLine = loc?.endLine ?: 0,
        beginColumn = loc?.beginColumn ?: 0,
        endColumn = loc?.endColumn ?: 0,
        code = loc?.code ?: "",
        explanation = description ?: "",
        tags = tags ?: emptyList(),
        branch = currentBranch ?: "",
        assetKind = assetKind ?: "",
        // The control id (LLM01, ASI05...) is what reads well in the detail panel.
        standards = standards?.mapNotNull { it.controlId ?: it.std }?.filter { it.isNotEmpty() } ?: emptyList(),
        redTeamVectors = redTeamVectors ?: emptyList(),
        remediationHint = remediationHint ?: "",
        vulnerabilityRaw = raw
    )
}
