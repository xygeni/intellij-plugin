package com.github.xygeni.intellij.model.report

import kotlinx.serialization.Serializable

/**
 * RawCodeFlow
 *
 * @author : Carmendelope 
 * @version : 19/2/26 (Carmendelope)
 **/
@Serializable
data class RawFrame(
    val kind: String,
    val location: RawIssueLocation? = null,
    val container: String? = null,
    val category: String? = null,
    val injectionPoint: String? = null
)

@Serializable
data class RawCodeFlow(
    val frames: List<RawFrame>? = null
)

fun RawFrame.toFrameIssue(): FrameIssue {
    return FrameIssue(kind = kind, location = location, container = container, category = category, injectionPoint = injectionPoint)
}

fun RawCodeFlow.toCodeFlowIssue(): CodeFlowIssue {
    return CodeFlowIssue(frames = frames?.map { it.toFrameIssue() })
}