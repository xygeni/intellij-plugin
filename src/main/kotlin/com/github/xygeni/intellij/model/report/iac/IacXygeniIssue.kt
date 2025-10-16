package com.github.xygeni.intellij.model.report.iac

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import kotlinx.serialization.Serializable

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
    override val detector: String? = null,
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "high",
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

    // -- Iac --
    val framework: String = "",
    val resource: String = "",
    val provider: String = ""
) : BaseXygeniIssue
