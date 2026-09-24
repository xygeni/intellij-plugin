package com.github.xygeni.intellij.dynamichtml.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Swing fallback (#1976) must keep every piece of the detail: text, code flow data and fix actions. */
class SwingHtmlAdapterTest {

    private val detailHtml = """
        <html><head><title>CVE-2024-1</title><style>body { color: var(--intellij-foreground); }</style></head>
        <body><script>window.renderData = function(data) {};</script>
        <div><h1>Xygeni SAST Issue </h1><p><span class="xy-severity-chip xy-severity-high">high</span>Tainted input</p></div>
        <section class="xy-tabs-section">
          <input type="radio" name="tabs" id="tab-1" checked><label for="tab-1">ISSUE DETAILS</label>
          <input type="radio" name="tabs" id="tab-2"><label for="tab-2">CODE SNIPPET</label>
          <input type="radio" name="tabs" id="tab-4"><label for="tab-4">CODE FLOW</label>
          <input type="radio" name="tabs" id="tab-3"><label for="tab-3">FIX IT</label>
          <script src="https://d3js.org/d3.v7.min.js"></script><script>function renderDiagramInTab() {}</script>
          <div id="tab-content-1"><table><tr><td>Detector</td><td>sql_injection</td></tr></table>
            <p><span id="xy-detector-doc">Loading ...</span><p></p><a href="#" target="_blank" id="xy-detector-link" hidden="hidden">Link to documentation</a></p>
          </div>
          <div id="tab-content-4">
            <div class="xy-code-flow-container">
            <div class="xy-view-toggle">
                <button id="how-to-fix" class="xy-toggle-btn active left">Explanation</button>
                <button id="btn-graph" class="xy-toggle-btn active" onclick="switchView('graph')">Graph view</button>
                <button id="btn-text" class="xy-toggle-btn" onclick="switchView('text')">Path</button>
            </div>
            <div id="code-flow-container" style="min-height: auto; position: relative;">
                <!-- Content will be rendered here via JS -->
            </div>
            </div>
            <script type="application/json" id="vuln-json">{"id":"vuln-1"}</script>
            <script>
                const flowNodes = [{"id":"a.js(3)","label":"a.js(3)","level":0,"filePath":"src/a.js","line":3,"category":"","container":"","type":"Source","code":"req.query.q","injectionPoint":""},{"id":"b.js(9)","label":"b.js(9)","level":1,"filePath":"src/b.js","line":9,"category":"","container":"","type":"Sink","code":"db.run(q)","injectionPoint":""}];
                const flowLinks = [{"source":"a.js(3)__0","target":"b.js(9)__1"}];
                const flowPaths = [["a.js(3)","b.js(9)"]];
                let currentView = 'graph';
            </script>
          </div>
          <div id="tab-content-2"><table><tr><td class="line-number">12</td><td class="code-line">db.run(q)</td></tr></table></div>
          <div id="tab-content-3"><div><p>Fix text</p><form id="rem-form"><p>Explanation</p>
            <input type="hidden" id="remediation-data" value="{&quot;filePath&quot;:&quot;src/b.js&quot;,&quot;line&quot;:9}">
            <div id="rem-buttons"><button id="rem-preview-button" type="button" class="xy-button" onclick="x()">Remediate with Xygeni Agent</button><button hidden="hidden" id="rem-save-button" type="button" class="xy-button" onclick="y()">Save</button></div>
          </form></div></div>
        </section></body></html>
    """.trimIndent()

    private fun htmlOf(segments: List<DetailSegment>): String =
        segments.filterIsInstance<DetailSegment.Html>().joinToString("") { segment -> segment.html }

    @Test
    fun `strips scripts styles radio tabs hidden inputs buttons and svg from the text segments`() {
        val html = htmlOf(SwingHtmlAdapter.toSegments(detailHtml, null))
        listOf("<script", "<style", "type=\"radio\"", "type=\"hidden\"", "<label", "<button", "<svg").forEach { forbidden ->
            assertFalse("still contains $forbidden", html.contains(forbidden))
        }
    }

    @Test
    fun `groups the panes into tabs in the browser tab strip order and keeps the content`() {
        val page = SwingHtmlAdapter.toPage(detailHtml, null)
        assertEquals(listOf("ISSUE DETAILS", "CODE SNIPPET", "CODE FLOW", "FIX IT"), page.tabs.map { tab -> tab.title })
        assertTrue(htmlOf(page.header).contains("Xygeni SAST Issue"))
        assertFalse(htmlOf(page.header).contains("sql_injection"))
        assertTrue(htmlOf(page.tabs[0].segments).contains("sql_injection"))
        assertTrue(htmlOf(page.tabs[1].segments).contains("db.run(q)"))
        assertTrue(htmlOf(page.tabs[3].segments).contains("Fix text"))
        assertFalse(htmlOf(page.tabs.flatMap { tab -> tab.segments }).contains("<!--XY:TAB:"))
    }

    @Test
    fun `the code flow and fix actions stay native components inside their own tab`() {
        val tabs = SwingHtmlAdapter.toPage(detailHtml, null).tabs.associateBy { tab -> tab.title }
        assertEquals(1, tabs.getValue("CODE FLOW").segments.filterIsInstance<DetailSegment.CodeFlow>().size)
        assertEquals(1, tabs.getValue("FIX IT").segments.filterIsInstance<DetailSegment.FixActions>().size)
        assertTrue(tabs.getValue("ISSUE DETAILS").segments.all { segment -> segment is DetailSegment.Html })
    }

    @Test
    fun `code flow segment carries the D3 nodes links paths and vulnerability json`() {
        val codeFlow = SwingHtmlAdapter.toSegments(detailHtml, null).filterIsInstance<DetailSegment.CodeFlow>().single().data
        assertEquals(2, codeFlow.nodes.size)
        assertEquals("a.js(3)__0", codeFlow.nodes[0].key)
        assertEquals("Sink", codeFlow.nodes[1].type)
        assertEquals("db.run(q)", codeFlow.nodes[1].code)
        assertEquals(listOf(FlowLink("a.js(3)__0", "b.js(9)__1")), codeFlow.links)
        assertEquals(listOf(listOf("a.js(3)", "b.js(9)")), codeFlow.paths)
        assertEquals("""{"id":"vuln-1"}""", codeFlow.vulnerabilityJson)
    }

    @Test
    fun `fix actions segment carries the unescaped remediation json`() {
        val fix = SwingHtmlAdapter.toSegments(detailHtml, null).filterIsInstance<DetailSegment.FixActions>().single()
        assertEquals("""{"filePath":"src/b.js","line":9}""", fix.remediationJson)
    }

    @Test
    fun `fills the detector documentation from the renderData payload`() {
        val payload = """{"descriptionDoc":"<p>Untrusted SQL.</p>","linkDocumentation":"https://docs.xygeni.io/sqli"}"""
        val html = htmlOf(SwingHtmlAdapter.toSegments(detailHtml, payload))
        assertTrue(html.contains("<p>Untrusted SQL.</p>"))
        assertTrue(html.contains("<a href=\"https://docs.xygeni.io/sqli\">Link to documentation</a>"))
        assertFalse(html.contains("Loading ..."))
    }

    @Test
    fun `detector documentation with dollar signs and backslashes is inserted literally`() {
        val payload = """{"descriptionDoc":"<p>Use ${'$'}_GET['id'], $1 and C:\\\\temp</p>","linkDocumentation":"https://docs.xygeni.io/x?a=1&b=$2"}"""
        val html = htmlOf(SwingHtmlAdapter.toSegments(detailHtml, payload))
        assertTrue(html.contains("<p>Use \$_GET['id'], \$1 and C:\\\\temp</p>"))
        assertTrue(html.contains("<a href=\"https://docs.xygeni.io/x?a=1&b=\$2\">Link to documentation</a>"))
    }

    @Test
    fun `without payload the loading placeholder and the dead link disappear`() {
        val html = htmlOf(SwingHtmlAdapter.toSegments(detailHtml, null))
        assertFalse(html.contains("Loading ..."))
        assertFalse(html.contains("xy-detector-link"))
    }

    @Test
    fun `a detail without code flow or fix is one header and one html tab`() {
        val simple = "<html><body><h1>Xygeni SCA Issue</h1><div id=\"tab-content-1\"><p>x</p></div></body></html>"
        val page = SwingHtmlAdapter.toPage(simple, null)
        assertTrue(page.header.single() is DetailSegment.Html)
        assertEquals("ISSUE DETAILS", page.tabs.single().title)
        assertTrue(page.tabs.single().segments.single() is DetailSegment.Html)
    }

    @Test
    fun `the graph component places nodes by level and path column like the D3 layout`() {
        val codeFlow = SwingHtmlAdapter.toSegments(detailHtml, null).filterIsInstance<DetailSegment.CodeFlow>().single().data
        val graph = CodeFlowGraphComponent(codeFlow)
        // Two levels → height covers 80 + 120 plus label room; single column → x around 150.
        assertTrue(graph.preferredSize.height >= 200 + 110)
        assertTrue(graph.preferredSize.width >= 150 + 170)
    }

    @Test
    fun `keeps detail keys on one line and pads the severity chip`() {
        val html = htmlOf(SwingHtmlAdapter.toSegments(
            "<p><span class=\"xy-severity-chip xy-severity-high\">high</span>Tainted input</p>" +
                "<table><tr><th>Red Team Vectors</th><td>PromptInjection</td></tr></table>",
            null
        ))
        assertTrue(html.contains("<th nowrap align=\"left\" valign=\"top\">Red&nbsp;Team&nbsp;Vectors</th>"))
        // Single class: the Swing stylesheet does not match multi-class attributes.
        assertTrue(html.contains("<span class=\"xy-severity-high\">&nbsp;high&nbsp;</span>&nbsp;&nbsp;Tainted input"))
    }

    @Test
    fun `the graph zooms within its limits and fitting never enlarges it past 1 to 1`() {
        val codeFlow = SwingHtmlAdapter.toSegments(detailHtml, null).filterIsInstance<DetailSegment.CodeFlow>().single().data
        val graph = CodeFlowGraphComponent(codeFlow)
        val base = graph.preferredSize
        graph.zoomBy(2.0)
        assertEquals((base.width * 2).toDouble(), graph.preferredSize.width.toDouble(), 1.0)
        repeat(20) { graph.zoomBy(2.0) }
        assertEquals(CodeFlowGraphComponent.MAX_SCALE, graph.scale, 0.0)
        graph.fitTo(10_000, 10_000)
        assertEquals(1.0, graph.scale, 0.0)
        graph.fitTo(base.width / 2, 10_000)
        assertEquals(0.5, graph.scale, 0.01)
    }

    @Test
    fun `marks the fix it title for the swing stylesheet`() {
        val html = htmlOf(SwingHtmlAdapter.toSegments("<div><p>XYGENI AGENT - REMEDIATE ISSUE</p></div>", null))
        assertTrue(html.contains("<p class=\"xy-fix-title\">XYGENI AGENT - REMEDIATE ISSUE</p>"))
    }
}
