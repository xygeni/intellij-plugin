package com.github.xygeni.intellij.model.report.ai

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.github.xygeni.intellij.model.report.CodeFlowIssue
import com.github.xygeni.intellij.model.report.server.RemediationData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * AiXygeniIssue
 *
 * AI Security finding (OWASP LLM Top 10 / Agentic ASI plus red-team vectors). Single
 * location, no taint flow.
 *
 * Auto-remediable: the scanner's `util rectify --ai` (RectifyCommand.java, `runAiRectify`) takes
 * the same `--file-path/--detector/--line` as SAST and quality, so [remediableLevel] is "AUTO"
 * and [toRemediationData] maps to `--ai` (see [QualityXygeniIssue]).
 *
 * Ticket: xygeni/xygeni-product-backlog#1692.
 **/
@Serializable
data class AiXygeniIssue(
    override val id: String,
    override val type: String,
    override val detector: String = "",
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "high",
    override val category: String = "ai",
    override val categoryName: String = "AI Security",
    override val file: String = "",
    override val beginLine: Int = 0,
    override val endLine: Int = 0,
    override val beginColumn: Int = 0,
    override val endColumn: Int = 0,
    override val code: String = "",
    override val explanation: String,
    override val tags: List<String> = emptyList(),
    override val kind: String = "ia_vulnerability",
    override val remediableLevel: String = "AUTO",

    // -- AI Security --
    val branch: String = "",
    /** AI asset kind the finding applies to (ai_prompt, ai_agent...), when the inventory has one. */
    val assetKind: String = "",
    /** Taxonomy controls the finding maps to (LLM01, ASI05...). */
    val standards: List<String> = emptyList(),
    /** Red-team attack vectors (PromptInjection, Jailbreaks...). */
    val redTeamVectors: List<String> = emptyList(),
    val remediationHint: String = "",

    // No code flow for AI findings.
    override val codeFlows: List<CodeFlowIssue>? = null,

    // JSON field
    override val vulnerabilityRaw: JsonObject? = null

) : BaseXygeniIssue {

    override fun toRemediationData(): RemediationData {
        // kind = category ("ai") so RemediateService builds `--ai`, as quality maps to `--quality`.
        return RemediationData(
            kind = category,
            detector = detector,
            filePath = file,
            dependency = null,
            line = beginLine
        )
    }
}
