package com.github.xygeni.intellij.model.report.cicd

import com.github.xygeni.intellij.model.report.RawIssueLocation
import com.github.xygeni.intellij.model.report.RawReportMetadata
import kotlinx.serialization.Serializable

/**
 * CicdReport
 *
 * @author : Carmendelope
 * @version : 16/10/25 (Carmendelope)
 **/
@Serializable
data class CicdReport(
    val metadata: RawReportMetadata,
    val misconfigurations: List<CicdRaw>
)

@Serializable
data class CicdRaw(
    val issueId: String,
    val type: String? = null,
    val detector: String? = null,
    val severity: String = "high",
    val confidence: String = "high",
    val location: RawIssueLocation? = null,
    val tags: List<String>? = null,
    val explanation: String? = null,
    //val properties: Map<String, Any>? = null
)