package com.github.xygeni.intellij.model.report

import kotlinx.serialization.Serializable

/**
 * IssueLocation
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/
@Serializable
data class RawIssueLocation(
    val filepath: String? = "",
    val beginLine: Int? = 0,
    val endLine: Int? = 0,
    val beginColumn: Int? = 0,
    val endColumn: Int? = 0,
    val code: String? = "",
    val secret: String? = "",
    val timeAdded: Long? = 0L,
    val branch: String? = "",
    val commitHash: String? = "",
    val user: String? = ""
)