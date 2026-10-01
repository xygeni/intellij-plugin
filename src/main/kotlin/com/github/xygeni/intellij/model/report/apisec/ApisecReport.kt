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
    val currentBranch: String? = null,
    @Transient
    val locations: ApiFlawLocations = ApiFlawLocations.EMPTY
)

/** A source position taken from the API inventory (`services[].modules[].endpoints[].handler`). */
data class ApiSourceLocation(val file: String, val line: Int)

/**
 * ApiFlawLocations — the report never puts a `location` on a flaw, but its inventory knows
 * where each endpoint handler lives. Flaws are keyed to endpoints by `endpointId`, which is
 * exactly `"<METHOD> <path>"`; module-scoped flaws only have the module's OpenAPI file.
 */
data class ApiFlawLocations(
    private val handlerByEndpoint: Map<String, ApiSourceLocation>,
    private val specFileByModule: Map<String, String>,
) {
    fun resolve(flaw: RawApiFlaw): ApiSourceLocation? {
        val endpointKey = flaw.endpointId ?: listOfNotNull(flaw.endpointMethod, flaw.endpointPath).joinToString(" ")
        handlerByEndpoint[endpointKey]?.let { return it }
        flaw.raw?.get("properties")?.jsonObject?.get("handler_file")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }?.let { return ApiSourceLocation(it, 0) }
        flaw.moduleName?.let { specFileByModule[it] }?.let { return ApiSourceLocation(it, 0) }
        return null
    }

    companion object {
        val EMPTY = ApiFlawLocations(emptyMap(), emptyMap())

        fun fromInventory(root: JsonObject): ApiFlawLocations {
            val handlerByEndpoint = mutableMapOf<String, ApiSourceLocation>()
            val specFileByModule = mutableMapOf<String, String>()
            val services = root["services"]?.jsonArray ?: return EMPTY
            for (service in services) {
                val modules = service.jsonObject["modules"]?.jsonArray ?: continue
                for (module in modules) {
                    val moduleObject = module.jsonObject
                    val moduleName = moduleObject["name"]?.jsonPrimitive?.contentOrNull
                    val specFile = moduleObject["location"]?.jsonObject?.get("file")?.jsonPrimitive?.contentOrNull
                    if (moduleName != null && !specFile.isNullOrBlank()) specFileByModule[moduleName] = specFile
                    val endpoints = moduleObject["endpoints"]?.jsonArray ?: continue
                    for (endpoint in endpoints) {
                        val endpointObject = endpoint.jsonObject
                        val method = endpointObject["method"]?.jsonPrimitive?.contentOrNull ?: continue
                        val path = endpointObject["path"]?.jsonPrimitive?.contentOrNull ?: continue
                        val handler = endpointObject["handler"]?.jsonObject ?: continue
                        val file = handler["file"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
                        val line = handler["line"]?.jsonPrimitive?.intOrNull ?: 0
                        handlerByEndpoint["$method $path"] = ApiSourceLocation(file, line)
                    }
                }
            }
            return ApiFlawLocations(handlerByEndpoint, specFileByModule)
        }
    }
}

@Serializable
data class RawApiFlaw(
    val issueId: String,
    val detector: String? = null,
    /** Machine type: missing_authentication, bola, bfla... */
    val flawType: String? = null,
    /** Human-readable label (embeds the endpoint); shown in the details, the tree uses [flawType]. */
    val title: String? = null,
    val severity: String = "",
    val explanation: String? = null,
    val remediation: String? = null,
    /** Absent for module- and service-scoped flaws. */
    val location: RawIssueLocation? = null,
    /** `"<METHOD> <path>"`, the key into the inventory's endpoints. */
    val endpointId: String? = null,
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
        currentBranch = currentBranch,
        locations = ApiFlawLocations.fromInventory(root)
    )
}

fun RawApiFlaw.toIssue(
    toolName: String?,
    currentBranch: String?,
    locations: ApiFlawLocations = ApiFlawLocations.EMPTY,
): ApisecXygeniIssue {
    val loc = this.location
    // The flaw's own `location` is usually absent; the inventory then gives the handler's file/line.
    val resolved = if (loc?.filepath.isNullOrBlank()) locations.resolve(this) else null

    return ApisecXygeniIssue(
        id = issueId,
        // The machine type is the tree label, like every other category; the title only fills in
        // when the type is missing so the node is never blank.
        type = (flawType ?: title) ?: "",
        title = title ?: "",
        detector = detector ?: "",
        tool = toolName,
        severity = severity,
        file = resolved?.file ?: loc?.filepath ?: "",
        beginLine = resolved?.line ?: loc?.beginLine ?: 0,
        endLine = resolved?.line ?: loc?.endLine ?: 0,
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
