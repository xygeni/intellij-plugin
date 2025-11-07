package com.github.xygeni.intellij.model.report.secret

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import kotlinx.serialization.Serializable

/**
 * SecretsXygeniIssue
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/
@Serializable
data class SecretsXygeniIssue(
    override val id: String,
    override val type: String,
    override val kind: String = "secret",
    override val detector: String = "",
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "",
    override val category: String = "Secret",
    override val categoryName: String = "Secret",
    override val file: String = "",
    override val beginLine: Int = 0,
    override val endLine: Int = 0,
    override val beginColumn: Int = 0,
    override val endColumn: Int = 0,
    override val code: String = "",
    override val explanation: String,
    override val tags: List<String> = emptyList(),
    override val remediableLevel: String = "NONE",

    // -- Secret --
    val hash: String = "",
    val key: String = "",
    val secret: String = "",
    val timeAdded: Long = 0L,
    val branch: String = "",
    val commitHash: String = "",
    val user: String = "",
    val foundBy: String = "",
    val resource: String = ""
): BaseXygeniIssue