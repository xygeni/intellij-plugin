package com.github.xygeni.intellij.model.report.iac

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * IacXygeniIssue
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/
@Serializable
data class IacXygeniIssue(
    override val id: String,
    override val type: String,
    override val kind: String = "iac_flaw",
    override val detector: String = "",
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "",
    override val category: String = "Iac",
    override val categoryName: String = "IAC",
    override val explanation: String,
    override val tags: List<String> = emptyList(),
    override val remediableLevel: String = "NONE",

    // -- location
    override val file: String = "",
    override val beginLine: Int = 0,
    override val endLine: Int = 0,
    override val beginColumn: Int = 0,
    override val endColumn: Int = 0,
    override val code: String = "",

    // -- Iac --
    val properties: Map<String, JsonElement> = emptyMap(),
    val framework: String = "",
    val resource: String = "",
    val provider: String = "",
    val where: String = ""

) : BaseXygeniIssue
