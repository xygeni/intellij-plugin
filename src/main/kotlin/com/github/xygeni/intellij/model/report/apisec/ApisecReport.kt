package com.github.xygeni.intellij.model.report.apisec

import com.github.xygeni.intellij.model.report.RawIssueLocation
import com.github.xygeni.intellij.model.report.RawReportMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*

/**
 * ApisecReport — parsing of `apisec.<report-suffix>.json`.
 *
 * The findings live under `flaws`, NOT under `vulnerabilities` as in the SAST-family
 * reports: the rest of this report is the discovered API inventory (`services`,
 * `dataObjects`), which is not a finding stream. Verified against a real report.
 **/
@Serializable
data class ApisecReport(
    val metadata: RawReportMetadata,
    val flaws: List<RawApiFlaw>,
    val currentBranch: String? = null
)

@Serializable
data class RawApiFlaw(
    val issueId: String,
    val detector: String? = null,
    /** Machine type: missing_authentication, bola, bfla... */
    val flawType: String? = null,
    /** Human-readable label; preferred over [flawType] for the tree node. */
    val title: String? = null,
    val severity: String = "",
    val explanation: String? = null,
    val remediation: String? = null,
    /** Absent for module- and service-scoped flaws. */
    val location: RawIssueLocation? = null,
    val endpointMethod: String? = null,
    val endpointPath: String? = null,
    val moduleName: String? = null,
    val serviceName: String? = null,
    val owaspApiTop10: List<String>? = null,
    val cwes: List<String>? = null,
    val tags: List<String>? = null,

    @Transient
    val raw: JsonObject? = null
)

fun parseApisecReport(jsonString: String): ApisecReport {

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

    val array = root["flaws"]
        ?.jsonArray
        ?: JsonArray(emptyList())

    val flaws = array.map { element ->
        val obj = element.jsonObject
        json.decodeFromJsonElement<RawApiFlaw>(obj).copy(raw = obj)
    }

    return ApisecReport(
        metadata = metadata,
        flaws = flaws,
        currentBranch = currentBranch
    )
}

fun RawApiFlaw.toIssue(toolName: String?, currentBranch: String?): ApisecXygeniIssue {
    val loc = this.location

    return ApisecXygeniIssue(
        id = issueId,
        // The human label wins so the tree node is never blank; fall back to the machine type.
        type = (title ?: flawType) ?: "",
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
        endpointMethod = endpointMethod ?: "",
        endpointPath = endpointPath ?: "",
        moduleName = moduleName ?: "",
        serviceName = serviceName ?: "",
        owaspApiTop10 = owaspApiTop10 ?: emptyList(),
        cwes = cwes ?: emptyList(),
        remediation = remediation ?: "",
        vulnerabilityRaw = raw
    )
}
