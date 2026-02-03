package com.github.xygeni.intellij.model.report.sca

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.github.xygeni.intellij.model.report.server.RemediationData
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
    override val kind: String = "sca",
    override val detector: String = "",
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "",
    override val category: String = "",
    override val categoryName: String = "",
    override val file: String = "",
    override val explanation: String,
    override val tags: List<String> = emptyList(),
    override val remediableLevel: String = "NONE",

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
    val language: String = "",
    val url: String = "",

    val branch: String = ""

    ) : BaseXygeniIssue {

    override fun toRemediationData(): RemediationData {
        var dep = "${name}:${version}:${language}"
        if (!group.isNullOrEmpty()) {
            dep = "${group}${dep}"
        }
        return RemediationData(
            kind = kind,
            detector = null,
            filePath = file,
            dependency = dep,
            line = null
        )
    }
}

