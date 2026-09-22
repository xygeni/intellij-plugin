package com.github.xygeni.intellij.model.report.apisec

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.github.xygeni.intellij.model.report.CodeFlowIssue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * ApisecXygeniIssue
 *
 * API Security flaw (missing authentication, BOLA, BFLA, excessive data exposure,
 * CORS / JWT misconfigurations...). Single-location like the quality findings, but a flaw
 * scoped to a module or a service carries no location at all — [file] is then empty.
 *
 * Not auto-remediable: the scanner has no `util rectify --apisec`, so [remediableLevel]
 * stays "NONE" and the Fix action never renders.
 *
 * Ticket: xygeni/xygeni-product-backlog#1691.
 **/
@Serializable
data class ApisecXygeniIssue(
    override val id: String,
    override val type: String,
    override val detector: String = "",
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "high",
    override val category: String = "apisec",
    override val categoryName: String = "API Security",
    override val file: String = "",
    override val beginLine: Int = 0,
    override val endLine: Int = 0,
    override val beginColumn: Int = 0,
    override val endColumn: Int = 0,
    override val code: String = "",
    override val explanation: String,
    override val tags: List<String> = emptyList(),
    override val kind: String = "api_flaw",
    override val remediableLevel: String = "NONE",

    // -- API Security --
    /** Human-readable label of the flaw, e.g. "Endpoint reachable without authentication: GET /users". */
    val title: String = "",
    val branch: String = "",
    /** HTTP method of the affected endpoint, when the flaw is endpoint-scoped. */
    val endpointMethod: String = "",
    /** Path of the affected endpoint, when the flaw is endpoint-scoped. */
    val endpointPath: String = "",
    val moduleName: String = "",
    val serviceName: String = "",
    val owaspApiTop10: List<String> = emptyList(),
    val cwes: List<String> = emptyList(),
    val remediation: String = "",

    // No code flow for API flaws.
    override val codeFlows: List<CodeFlowIssue>? = null,

    // JSON field
    override val vulnerabilityRaw: JsonObject? = null

) : BaseXygeniIssue {

    /** `POST /users/v1/login` when endpoint-scoped, empty otherwise. */
    val endpoint: String
        get() = when {
            endpointPath.isEmpty() -> ""
            endpointMethod.isEmpty() -> endpointPath
            else -> "$endpointMethod $endpointPath"
        }
}
