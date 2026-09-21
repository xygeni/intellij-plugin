package com.github.xygeni.intellij.dynamichtml.editor

import com.github.xygeni.intellij.model.JsonConfig
import com.github.xygeni.intellij.render.XygeniConstants
import com.google.gson.Gson
import com.intellij.openapi.util.text.StringUtil
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One node of the SAST code flow, as the renderer serialises it for D3. */
data class FlowNode(
    val id: String? = null,
    val label: String? = null,
    val level: Int = 0,
    val filePath: String? = null,
    val line: Int = 0,
    val category: String? = null,
    val container: String? = null,
    val type: String? = null,
    val code: String? = null,
    val injectionPoint: String? = null,
) {
    /** The renderer keys nodes by id + level because one location can appear at several steps. */
    val key: String get() = "${id}__$level"
}

data class FlowLink(val source: String? = null, val target: String? = null)

data class CodeFlowData(
    val nodes: List<FlowNode>,
    val links: List<FlowLink>,
    val paths: List<List<String>>,
    /** Raw vulnerability JSON the "Explanation" action sends to the AI explain service. */
    val vulnerabilityJson: String,
)

/** A piece of the detail page: plain HTML for a JEditorPane, or a place where a Swing component goes. */
sealed interface DetailSegment {
    data class Html(val html: String) : DetailSegment
    data class CodeFlow(val data: CodeFlowData) : DetailSegment
    data class FixActions(val remediationJson: String) : DetailSegment
}

/**
 * SwingHtmlAdapter — rewrites the detail HTML produced by the renderers (built for a real browser:
 * CSS radio tabs, scripts, a D3 diagram and buttons bridged to plugin actions) into segments the
 * Swing viewer can show without JCEF (#1976): HTML 3.2 / CSS1 text plus native components for the
 * interactive parts (code flow graph + path list + Explanation, remediation buttons).
 **/
object SwingHtmlAdapter {

    private const val CODE_FLOW_MARKER = "<!--XY:CODE-FLOW-->"
    private const val FIX_ACTIONS_MARKER = "<!--XY:FIX-ACTIONS-->"
    private val markerSplit = Regex("(?=$CODE_FLOW_MARKER)|(?<=$CODE_FLOW_MARKER)|(?=$FIX_ACTIONS_MARKER)|(?<=$FIX_ACTIONS_MARKER)")

    private val dotAll = RegexOption.DOT_MATCHES_ALL
    private val scriptBlock = Regex("<script[^>]*>.*?</script>", dotAll)
    private val styleBlock = Regex("<style[^>]*>.*?</style>", dotAll)
    private val svgBlock = Regex("<svg[^>]*>.*?</svg>", dotAll)
    private val buttonBlock = Regex("<button[^>]*>.*?</button>", dotAll)
    private val linkTag = Regex("<link[^>]*>")
    private val radioInput = Regex("<input[^>]*type=\"radio\"[^>]*>")
    private val hiddenInput = Regex("<input[^>]*type=\"hidden\"[^>]*>")
    private val tabLabel = Regex("<label[^>]*for=\"tab-\\d+\"[^>]*>.*?</label>", dotAll)
    private val detectorDoc = Regex("<span[^>]*id=\"${XygeniConstants.LOADING_SPAN_ID}\"[^>]*>.*?</span>", dotAll)
    private val detectorLink = Regex("<a[^>]*id=\"${XygeniConstants.LINK_TO_DOC_ID}\"[^>]*>.*?</a>", dotAll)

    private val codeFlowContainer =
        Regex("<div class=\"xy-code-flow-container\">.*?<div id=\"code-flow-container\"[^>]*>.*?</div>\\s*</div>", dotAll)
    private val remediationButtons =
        Regex("<div id=\"${XygeniConstants.REMEDIATION_BUTTONS_ID}\">.*?</div>", dotAll)
    private val remediationData = Regex("<input[^>]*id=\"remediation-data\"[^>]*value=\"([^\"]*)\"")
    private val flowNodesJs = Regex("const flowNodes = (.*?);\\s*\\n")
    private val flowLinksJs = Regex("const flowLinks = (.*?);\\s*\\n")
    private val flowPathsJs = Regex("const flowPaths = (.*?);\\s*\\n")
    private val vulnerabilityJson = Regex("<script type=\"application/json\" id=\"vuln-json\">(.*?)</script>", dotAll)

    /** Tab content ids → the heading that replaces the tab strip, in the renderer's order. */
    private val tabHeadings = mapOf(
        XygeniConstants.ISSUE_DETAILS_CONTENT_ID to XygeniConstants.ISSUE_DETAILS_TAB,
        XygeniConstants.CODE_SNIPPET_CONTENT_ID to XygeniConstants.CODE_SNIPPET_TAB,
        XygeniConstants.FIX_IT_CONTENT_ID to XygeniConstants.FIX_IT_TAB,
        XygeniConstants.CODE_FLOW_CONTENT_ID to XygeniConstants.CODE_FLOW_TAB,
    )

    private val gson = Gson()

    fun toSegments(html: String, detectorDataJson: String?): List<DetailSegment> {
        // Pull the interactive data out BEFORE the scripts and inputs that carry it are stripped.
        val codeFlow = extractCodeFlow(html)
        val remediation = extractRemediationData(html)

        var out = html
        if (codeFlow != null) out = out.replace(codeFlowContainer, CODE_FLOW_MARKER)
        if (remediation != null) out = out.replace(remediationButtons, FIX_ACTIONS_MARKER)

        out = out
            .replace(scriptBlock, "")
            .replace(styleBlock, "")
            .replace(linkTag, "")
            .replace(svgBlock, "")
            .replace(buttonBlock, "")
            .replace(radioInput, "")
            .replace(hiddenInput, "")
            .replace(tabLabel, "")

        // The tab strip is CSS-only (radio + label); without it every pane is visible, so give
        // each pane the heading the tab used to carry.
        tabHeadings.forEach { (contentId, heading) ->
            out = out.replace("<div id=\"$contentId\">", "<h2>$heading</h2><div id=\"$contentId\">")
        }

        // `window.renderData` used to fill the detector documentation; do it in place instead.
        val (description, link) = parseDetectorData(detectorDataJson)
        // Lambda form on purpose: the String overload treats `$` and `\` in the detector doc as
        // group references and throws on content like `$_GET` (review M1).
        out = out.replace(detectorDoc) { description ?: "" }
        out = out.replace(detectorLink) {
            if (link.isNullOrBlank()) "" else "<a href=\"$link\">${XygeniConstants.LINK_TO_DOC}</a>"
        }

        return out.split(markerSplit).filter { piece -> piece.isNotBlank() }.map { piece ->
            when (piece) {
                CODE_FLOW_MARKER -> DetailSegment.CodeFlow(codeFlow!!)
                FIX_ACTIONS_MARKER -> DetailSegment.FixActions(remediation!!)
                else -> DetailSegment.Html(piece)
            }
        }
    }

    private fun extractCodeFlow(html: String): CodeFlowData? {
        val nodesJson = flowNodesJs.find(html)?.groupValues?.get(1) ?: return null
        val linksJson = flowLinksJs.find(html)?.groupValues?.get(1) ?: return null
        val pathsJson = flowPathsJs.find(html)?.groupValues?.get(1) ?: return null
        return try {
            CodeFlowData(
                nodes = gson.fromJson(nodesJson, Array<FlowNode>::class.java).toList(),
                links = gson.fromJson(linksJson, Array<FlowLink>::class.java).toList(),
                paths = gson.fromJson(pathsJson, Array<Array<String>>::class.java).map { path -> path.toList() },
                vulnerabilityJson = vulnerabilityJson.find(html)?.groupValues?.get(1)?.trim() ?: "",
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractRemediationData(html: String): String? =
        remediationData.find(html)?.groupValues?.get(1)?.let { escaped -> StringUtil.unescapeXmlEntities(escaped) }

    private fun parseDetectorData(json: String?): Pair<String?, String?> {
        if (json.isNullOrBlank()) return null to null
        return try {
            val obj = JsonConfig.relaxed.parseToJsonElement(json).jsonObject
            val description = obj["descriptionDoc"]?.jsonPrimitive?.content
            val link = obj["linkDocumentation"]?.jsonPrimitive?.content
            description to link
        } catch (_: Exception) {
            null to null
        }
    }
}
