package com.github.xygeni.intellij.model.report

import kotlinx.serialization.Serializable

/**
 * SecretsReportMetadata
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/
@Serializable
data class RawReportMetadata(
    val reportProperties: Map<String, String> = emptyMap()
)