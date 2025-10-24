package com.github.xygeni.intellij.model.report.sast

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import kotlinx.serialization.Serializable

/**
 * SastXygeniIssue
 *
 * @author : Carmendelope
 * @version : 15/10/25 (Carmendelope)
 **/

@Serializable
data class SastXygeniIssue(
    override  val id: String,
    override val type: String,
    override val detector: String? = null,
    override val tool: String? = null,
    override val severity: String,
    override val confidence: String = "high",
    override val category: String = "sast",
    override val categoryName: String = "SAST",
    override val file: String = "",
    override val beginLine: Int = 0,
    override val endLine: Int = 0,
    override val beginColumn: Int = 0,
    override val endColumn: Int = 0,
    override val code: String = "",
    override val explanation: String,
    override val tags: List<String> = emptyList(),

    // -- Sast --
    val kind: String = "",
    val branch: String = "",
    val cwe: Int = 0,
    val cwes: List<String> = emptyList(),
    val language: String = "",
): BaseXygeniIssue
