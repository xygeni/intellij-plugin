package com.github.xygeni.intellij.model.report.cicd

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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
    override val kind: String = "misconfiguration",
    override val detector: String = "",
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "",
    override val category: String = "Misconfiguration",
    override val categoryName: String = "CICD",
    override val file: String = "",
    override val explanation: String,
    override val tags: List<String> = emptyList(),

    // -- location
    override val beginLine: Int = 0,
    override val endLine: Int = 0,
    override val beginColumn: Int = 0,
    override val endColumn: Int = 0,
    override val code: String = "",

    // Cicd
    val properties: Map<String, JsonElement> = emptyMap(),
    val where: String = ""

) : BaseXygeniIssue

