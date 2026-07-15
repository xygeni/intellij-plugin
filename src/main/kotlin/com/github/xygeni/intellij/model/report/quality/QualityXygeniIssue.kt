package com.github.xygeni.intellij.model.report.quality

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.github.xygeni.intellij.model.report.CodeFlowIssue
import com.github.xygeni.intellij.model.report.server.RemediationData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * QualityXygeniIssue
 *
 * Code Quality issue (OpenGrep-based quality rules: code smells, complexity,
 * naming, maintainability...). Modelled after {@link SastXygeniIssue} but
 * simpler: quality findings are single-location (no taint / code-flow). They
 * ARE auto-remediable via the scanner's `util rectify --quality` (see
 * [toRemediationData]).
 *
 * Ticket: xygeni/xygeni-product-backlog#56.
 **/
@Serializable
data class QualityXygeniIssue(
    override val id: String,
    override val type: String,
    override val detector: String = "",
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "high",
    override val category: String = "quality",
    override val categoryName: String = "Code Quality",
    override val file: String = "",
    override val beginLine: Int = 0,
    override val endLine: Int = 0,
    override val beginColumn: Int = 0,
    override val endColumn: Int = 0,
    override val code: String = "",
    override val explanation: String,
    override val tags: List<String> = emptyList(),
    override val kind: String = "quality_issue",
    override val remediableLevel: String = "AUTO",

    // -- Quality --
    val branch: String = "",
    val language: String = "",
    /** Quality rule family (style, complexity, maintainability...) when provided. */
    val qualityCategory: String = "",

    // No code flow for quality findings.
    override val codeFlows: List<CodeFlowIssue>? = null,

    // JSON field
    override val vulnerabilityRaw: JsonObject? = null

) : BaseXygeniIssue {

    override fun toRemediationData(): RemediationData {
        // kind = category ("quality") so RemediateService builds `--quality`,
        // mirroring how SastXygeniIssue maps to `--sast`.
        return RemediationData(
            kind = category,
            detector = detector,
            filePath = file,
            dependency = null,
            line = beginLine
        )
    }
}
