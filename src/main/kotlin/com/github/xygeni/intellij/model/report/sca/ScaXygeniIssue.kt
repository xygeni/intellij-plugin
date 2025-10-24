package com.github.xygeni.intellij.model.report.sca

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import kotlinx.html.B
import kotlinx.serialization.Serializable

/**
 * ScaXygeniIssue
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/
@Serializable
data class ScaXygeniIssue(
    override val id: String,
    override val type: String,
    override val detector: String? = null,
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "high",
    override val category: String = "sca",
    override val categoryName: String = "SCA",
    override val file: String = "",
    override val explanation: String,
    override val tags: List<String> = emptyList(),

    // -- location
    override val beginLine: Int = 0,
    override val endLine: Int = 0,
    override val beginColumn: Int = 0,
    override val endColumn: Int = 0,
    override val code: String = "",

    // -- Sca
    val virtual: Boolean? = false,
    val repositoryType: String? = null,
    val displayFileName: String? = null,
    val group: String? = null,
    val name: String? = null,
    val version: String? = null,
    val dependencyPaths: List<String>? = null,
    val directDependency: Boolean = false,
    val baseScore: Double? = null ,
    val publicationDate: String = "",
    val weakness: List<String>? = null,
    val references: List<String>? = null,
    val versions: String = "",
    val vector: String = "",
    val remediableLevel: String = "",
    val language: String = "",
    val url: String = "",

    ) : BaseXygeniIssue