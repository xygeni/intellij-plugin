package com.github.xygeni.intellij.model.report.server

import kotlinx.serialization.Serializable

/**
 * RemediatonData
 *
 * @author : Carmendelope
 * @version : 30/10/25 (Carmendelope)
 **/

@Serializable
data class RemediationData(
    val kind: String?,
    val detector: String?,
    val filePath: String?,
    val dependency: String?,
    val line: Int?
)
