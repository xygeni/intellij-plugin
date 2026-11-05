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
    /** Where a tab pane starts; the segments up to the next one belong to that tab. */
    data class TabStart(val title: String) : DetailSegment
}

data class DetailTab(val title: String, val segments: List<DetailSegment>)

/** The detail split as the browser shows it: the header above the tab strip, then one entry per tab. */
data class DetailPage(val header: List<DetailSegment>, val tabs: List<DetailTab>)

/**
 * SwingHtmlAdapter — rewrites the detail HTML produced by the renderers (built for a real browser:
 * CSS radio tabs, scripts, a D3 diagram and buttons bridged to plugin actions) into segments the
 * Swing viewer can show without JCEF (#1976): HTML 3.2 / CSS1 text plus native components for the
 * interactive parts (code flow graph + path list + Explanation, remediation buttons).
 **/
object SwingHtmlAdapter {

    private const val CODE_FLOW_MARKER = "<!--XY:CODE-FLOW-->"
    private const val FIX_ACTIONS_MARKER = "<!--XY:FIX-ACTIONS-->"
    private const val TAB_MARKER_PREFIX = "<!--XY:TAB:"
    private val tabMarker = Regex("<!--XY:TAB:([^>]*)-->")
    private val markerSplit = Regex(
        "(?=$CODE_FLOW_MARKER)|(?<=$CODE_FLOW_MARKER)|(?=$FIX_ACTIONS_MARKER)|(?<=$FIX_ACTIONS_MARKER)" +
            "|(?=$TAB_MARKER_PREFIX)|(?<=$TAB_MARKER_PREFIX[^>]{1,40}-->)"
    )

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
    private val tableHeader = Regex("<th>(.*?)</th>", dotAll)
    private val severityChip = Regex("<span class=\"xy-severity-chip (xy-severity-[a-z]+)\">([^<]*)</span>")

    private val codeFlowContainer =
        Regex("<div class=\"xy-code-flow-container\">.*?<div id=\"code-flow-container\"[^>]*>.*?</div>\\s*</div>", dotAll)
    private val remediationButtons =
        Regex("<div id=\"${XygeniConstants.REMEDIATION_BUTTONS_ID}\">.*?</div>", dotAll)
    private val remediationData = Regex("<input[^>]*id=\"remediation-data\"[^>]*value=\"([^\"]*)\"")
    private val flowNodesJs = Regex("const flowNodes = (.*?);\\s*\\n")
    private val flowLinksJs = Regex("const flowLinks = (.*?);\\s*\\n")
    private val flowPathsJs = Regex("const flowPaths = (.*?);\\s*\\n")
    private val vulnerabilityJson = Regex("<script type=\"application/json\" id=\"vuln-json\">(.*?)</script>", dotAll)

    /** Tab content ids → tab title, in the order the browser tab strip shows them. */
    private val tabTitles = linkedMapOf(
        XygeniConstants.ISSUE_DETAILS_CONTENT_ID to XygeniConstants.ISSUE_DETAILS_TAB,
        XygeniConstants.CODE_SNIPPET_CONTENT_ID to XygeniConstants.CODE_SNIPPET_TAB,
        XygeniConstants.CODE_FLOW_CONTENT_ID to XygeniConstants.CODE_FLOW_TAB,
        XygeniConstants.FIX_IT_CONTENT_ID to XygeniConstants.FIX_IT_TAB,
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

        // The browser tab strip is CSS-only (radio + label), which the HTMLEditorKit cannot run: mark
        // where each pane starts so the viewer draws its own strip and shows one pane at a time.
        tabTitles.forEach { (contentId, title) ->
            out = out.replace("<div id=\"$contentId\">", "$TAB_MARKER_PREFIX$title--><div id=\"$contentId\">")
        }

        // The word-wrap view factory breaks inside words, so the table hands the key column its
        // minimum width and splits "Explanation" into "Explanat/ion": keep each key on one line.
        out = out.replace(tableHeader) { match ->
            "<th nowrap align=\"left\" valign=\"top\">${match.groupValues[1].trim().replace(" ", "&nbsp;")}</th>"
        }
        // The browser chip is a padded pill with a right margin; CSS1 has neither, so pad it with spaces.
        // The Swing stylesheet matches a single class name only, so keep just the severity one.
        out = out.replace(severityChip) { match ->
            "<span class=\"${match.groupValues[1]}\">&nbsp;${match.groupValues[2].trim()}&nbsp;</span>&nbsp;&nbsp;"
        }

        // The FIX IT title is a plain paragraph styled by the browser tab CSS; mark it for the Swing stylesheet.
        out = out.replace("<p>${XygeniConstants.REMEDIATION_TEXT}</p>", "<p class=\"xy-fix-title\">${XygeniConstants.REMEDIATION_TEXT}</p>")

        // `window.renderData` used to fill the detector documentation; do it in place instead.
        val (description, link) = parseDetectorData(detectorDataJson)
        // Lambda form on purpose: the String overload treats `$` and `\` in the detector doc as
        // group references and throws on content like `$_GET` (review M1).
        out = out.replace(detectorDoc) { description ?: "" }
        out = out.replace(detectorLink) {
            if (link.isNullOrBlank()) "" else "<a href=\"$link\">${XygeniConstants.LINK_TO_DOC}</a>"
        }

        return out.split(markerSplit).filter { piece -> piece.isNotBlank() }.map { piece ->
            val tab = tabMarker.matchEntire(piece)
            when {
                tab != null -> DetailSegment.TabStart(tab.groupValues[1])
                piece == CODE_FLOW_MARKER -> DetailSegment.CodeFlow(codeFlow!!)
                piece == FIX_ACTIONS_MARKER -> DetailSegment.FixActions(remediation!!)
                else -> DetailSegment.Html(piece)
            }
        }
    }

    /** [toSegments] grouped into the header and the tabs, ordered like the browser tab strip. */
    fun toPage(html: String, detectorDataJson: String?): DetailPage {
        val header = mutableListOf<DetailSegment>()
        val tabs = mutableListOf<DetailTab>()
        toSegments(html, detectorDataJson).forEach { segment ->
            when {
                segment is DetailSegment.TabStart -> tabs.add(DetailTab(segment.title, emptyList()))
                tabs.isEmpty() -> header.add(segment)
                else -> tabs[tabs.lastIndex] = tabs.last().let { tab -> tab.copy(segments = tab.segments + segment) }
            }
        }
        val order = tabTitles.values.toList()
        return DetailPage(header, tabs.sortedBy { tab -> order.indexOf(tab.title) })
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
