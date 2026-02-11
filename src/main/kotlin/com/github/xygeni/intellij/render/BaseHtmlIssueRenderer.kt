package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.github.xygeni.intellij.model.report.server.RemediationData
import com.intellij.util.ui.UIUtil
import icons.Icons
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.Json
import java.awt.Color

/**
 * BaseHtmlIssueRenderer
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 *
 * Abstract base class that builds the HTML structure for dynamic issue views.
 *
 * Responsibilities:
 * - Generate a complete HTML document (string) using the Kotlin HTML DSL.
 * - Include shared CSS style and dynamic Javascript helpers.
 * - Define a common layout (header, details, tabs, etc.)
 * - Provide a consistent entry point for injecting live data updates (via window.renderData)
 *
 * The resulting HTML string is loaded by [DynamicHtmlFileEditor] and displayed in a JCEF browser
 *
 * Subclasses (e.g. [SastIssueRenderer], [ScaIssueRenderer], etc) should override:
 * - [renderCommonHeader] to add type-specific header information
 * - [renderTabs] to define issue details or vulnerability panels
 *
 **/

val diagramFunctionScript = """
<script src="https://d3js.org/d3.v7.min.js"></script>
<script>
function renderDiagramInTab(containerId, nodes, links, paths) {
    const container = d3.select(containerId);
    container.selectAll("*").remove();

    const svg = container.append("svg")
        .attr("width","100%")
        .attr("height","100%")
        .style("background","var(--intellij-background)")
        .style("cursor", "grab");
        
    svg.on("active", () => svg.style("cursor", "grabbing"));

    const { width, height } = svg.node().getBoundingClientRect();
    const g = svg.append("g");
    
    const nodeRadius = 30;
    const colSpacing = 220;
    const rowSpacing = 120;

    // NODE MAP (ID__LEVEL)
    const nodeMap = new Map();
    // nodes comes as an array of objects {id, level} from Kotlin
    nodes.forEach(d => nodeMap.set(d.id + "__" + d.level, { ...d, paths: [] }));

    // Registrar en qué paths aparece cada nodo
    paths.forEach((path, pIdx) => {
        path.forEach((id, level) => {
            const key = id + "__" + level;
            const node = nodeMap.get(key);
            if (!node) return;
            node.paths.push({ pathIndex: pIdx });
        });
    });

    // HORIZONTAL 
    const allNodes = Array.from(nodeMap.values());
    allNodes.forEach(node => {
        const avgColumn = node.paths.reduce((sum,p)=>sum+p.pathIndex,0)/node.paths.length;
        node.x = avgColumn * colSpacing + 150;
        node.y = node.level * rowSpacing + 80;
    });

    // CURVE LINKS
    g.selectAll("path.link")
      .data(links)
      .enter()
      .append("path")
      .attr("fill","none")
      .attr("stroke","#999")
      .attr("stroke-width",2)
      .attr("d", d=>{
          const source = nodeMap.get(d.source); // d.source is "id__level" string
          const target = nodeMap.get(d.target);
          if(!source || !target) return "";
          
          const controlX = source.x + Math.max(30,(target.x-source.x)/2);
          const controlY = (source.y + target.y)/2;
          return `M${'$'}{source.x},${'$'}{source.y} Q${'$'}{controlX},${'$'}{controlY} ${'$'}{target.x},${'$'}{target.y}`;
      });

    // FINAL NODES
    const finalNodeKeys = new Set();
    paths.forEach(path => {
        const lastLevel = path.length - 1;
        const lastKey = path[lastLevel] + "__" + lastLevel;
        finalNodeKeys.add(lastKey);
    });

    // PRINT NODES
    const node = g.selectAll("circle")
      .data(allNodes)
      .enter()
      .append("circle")
      .attr("cx", d => d.x)
      .attr("cy", d => d.y)
      .attr("r", nodeRadius)
      .attr("fill", d => finalNodeKeys.has(d.id + "__" + d.level) ? "var(--intellij-foreground)" : "#69b3a2"); // 🔹 color del tema si final

    // PRINT LABELS (staggered to avoid overlap)
    g.selectAll("text.label")
      .data(allNodes.sort((a, b) => a.level !== b.level ? a.level - b.level : a.x - b.x))
      .enter()
      .append("text")
      .attr("class", "label")
      .attr("x", d => d.x)
      .attr("y", (d, i) => d.y + (i % 2 === 0 ? 45 : 65))
      .attr("text-anchor", "middle")
      .text(d => d.label || d.id);

    // COUNT BUBLE
    const badges = g.selectAll(".badge")
      .data(allNodes.filter(d => d.paths.length > 1))
      .enter()
      .append("g")
      .attr("class", "badge")
      .attr("transform", d => `translate(${'$'}{d.x + 15}, ${'$'}{d.y - 15})`);

    badges.append("circle")
      .attr("r", 10)
      .attr("fill", "#9e9e9e")
      .attr("stroke", "white")
      .attr("stroke-width", 1);

    badges.append("text")
      .attr("text-anchor", "middle")
      .attr("dy", "0.3em")
      .style("font-size", "10px")
      .style("font-weight", "bold")
      .style("fill", "white")
      .text(d => d.paths.length);

    // Zoom
    const zoom = d3.zoom()
      .scaleExtent([0.2,4])
      .on("zoom", event => g.attr("transform", event.transform));
    svg.call(zoom);

    // Zoom buttons
     const controls = container.append("div").attr("class","xy-zoom-controls");
        
    controls.append("button").text("+")
        .attr("class", "xy-zoom-btn")
        .on("click", ()=> svg.transition().duration(300).call(zoom.scaleBy,1.2));
    controls.append("button").text("-")
        .attr("class", "xy-zoom-btn")
        .on("click", ()=> svg.transition().duration(300).call(zoom.scaleBy,0.8));
        
    const tooltip = d3.select("body").append("div").attr("class", "tooltip");
    
    node
      .on("mouseover", (event, d) => {
        tooltip
          .style("opacity", 1)
          .html(`
            ${'$'}{d.filePath ? `<strong>${'$'}{d.filePath}</strong><br>` : ''}
            ${'$'}{d.line ? `Line: ${'$'}{d.line}<br>` : ''}
            ${'$'}{d.type ? `Type: ${'$'}{d.type}<br>` : ''}
            ${'$'}{d.category ? `Category: ${'$'}{d.category}<br>` : ''}  
            ${'$'}{d.container ? `Container: ${'$'}{d.container}<br>` : ''}
            ${'$'}{d.injectionPoint ? `InjectionPoint: ${'$'}{d.injectionPoint}<br>` : ''}
            ${'$'}{d.code ? `<pre><code>${'$'}{d.code}</code></pre>` : ''}
          `);
      })
      .on("mousemove", (event) => {
        tooltip
          .style("left", (event.pageX + 15) + "px")
          .style("top", (event.pageY + 15) + "px");
      })
      .on("mouseout", () => {
        tooltip.style("opacity", 0);
      });
    
}

function renderTextFlowInTab(containerId, nodes) {
    const container = d3.select(containerId);
    container.selectAll("*").remove();

    const flowContainer = container.append("div")
        .attr("class", "xy-text-flow-container");

    nodes.sort((a, b) => a.level - b.level).forEach(node => {
        const step = flowContainer.append("div")
            .attr("class", "xy-flow-step");
        
        const fileName = node.filePath ? node.filePath.split('/').pop() : 'Unknown';
        
        step.html(`
            <div class="xy-flow-step-header">
                <span class="xy-flow-step-file">${'$'}{fileName}:${'$'}{node.line}</span>
                <span class="xy-flow-step-type">${'$'}{node.type}</span>
            </div>
            <div class="xy-flow-step-path">${'$'}{node.filePath}</div>
            <div class="xy-flow-step-details">
                ${'$'}{node.category ? `<span>Category: <b>${'$'}{node.category}</b></span>` : ''}            
                ${'$'}{node.container ? `<span>Container: <b>${'$'}{node.container}</b></span>` : ''}
                ${'$'}{node.injectionPoint ? `<span>InjectionPoint: <b>${'$'}{node.injectionPoint}</b></span>` : ''}
            </div>
            ${'$'}{node.code ? `<pre><code>${'$'}{node.code}</code></pre>` : ''}
        `);
    });
}
</script>
""".trimIndent()


abstract class BaseHtmlIssueRenderer<T : BaseXygeniIssue> : IssueRenderer<T> {

    companion object {
        const val RENDER_DATA_JS = """
                  window.renderData = function(data) {
                    if (typeof data === 'string') data = JSON.parse(data);
                    const docEl = document.getElementById('xy-detector-doc');
                    if (docEl) {
                      docEl.innerHTML = data.descriptionDoc || '';
                    }
                    const linkEl = document.getElementById('xy-detector-link');
                    if (linkEl) {
                      linkEl.href = data.linkDocumentation || '#';
                      linkEl.hidden = !data.linkDocumentation;
                    }
                  };                                                
                  window.domReady = true;
                  """
        
        // Cache CSS content to avoid reading from disk on every render
        private val cachedCssContent: String by lazy {
            val cssStream = javaClass.classLoader.getResourceAsStream("html/xygeni.css")
                ?: throw IllegalStateException("html/xygeni.css not found in resources")
            cssStream.bufferedReader().use { it.readText() }
        }
    }

    protected val branchIcon = Icons::class.java.getResource("/icons/branch.svg")
        ?.readText()

    private fun HEAD.inlineCss(css: String) {
        unsafe {
            +"""<style>
            $css
        </style>"""
        }
    }

    private fun colorToCss(color: Color): String = "rgb(${color.red}, ${color.green}, ${color.blue})"

    private fun loadCssResource(): String {
        val fg = UIUtil.getLabelForeground()
        val bg = UIUtil.getPanelBackground()

        val root = ":root {\n" +
                " --intellij-font-size: 17px;\n" +
                " --intellij-foreground: ${colorToCss(fg)};\n" +
                " --intellij-background: ${colorToCss(bg)};\n" +
                "}\n"

        // Use cached CSS content instead of reading from disk every time
        return root + cachedCssContent
    }

    // HEADER
    protected open fun renderCommonHeader(issue: T): String {
        val text =
            if (issue.explanation.length > 50) {
                issue.explanation.substring(0, 50) + "..."
            } else {
                issue.explanation
            }

        return createHTML().div {
            h1 { +"Xygeni ${issue.categoryName} Issue " }
            p {
                span {
                    classes = setOf("xy-slide-${issue.severity}")
                    text(issue.severity)
                }
                +text
            }
        }
    }

    protected open fun renderCustomHeader(issue: T): String {
        return createHTML().p {
            unsafe {
                +"${issue.category}&nbsp;&nbsp;&nbsp;${issue.type}"
            }
        }
    }

    protected open fun renderTabs(issue: T): String {
        val detail = renderCustomIssueDetails(issue)
        val code = renderCustomCodeSnippet(issue)
        val fix = renderCustomFix(issue)
        val codeFlow = renderCodeFlow(issue)
        return createHTML().section(classes = "xy-tabs-section") {
            if (detail.isNotEmpty()) {
                input(type = InputType.radio, name = "tabs") { id = XygeniConstants.ISSUE_DETAILS_TAB_ID; checked = true }
                label { htmlFor = XygeniConstants.ISSUE_DETAILS_TAB_ID; +XygeniConstants.ISSUE_DETAILS_TAB }
            }
            if (code.isNotEmpty()) {
                input(type = InputType.radio, name = "tabs") { id = XygeniConstants.CODE_SNIPPET_TAB_ID }
                label { htmlFor = XygeniConstants.CODE_SNIPPET_TAB_ID; +XygeniConstants.CODE_SNIPPET_TAB }
            }
            if (codeFlow != "") {
                input(type = InputType.radio, name = "tabs") { id = XygeniConstants.CODE_FLOW_TAB_ID }
                label { htmlFor = XygeniConstants.CODE_FLOW_TAB_ID; +XygeniConstants.CODE_FLOW_TAB }
            }
            if (fix.isNotEmpty()) {
                input(type = InputType.radio, name = "tabs") { id = XygeniConstants.FIX_IT_TAB_ID}
                label { htmlFor = XygeniConstants.FIX_IT_TAB_ID; +XygeniConstants.FIX_IT_TAB }
            }
            div {
                id = XygeniConstants.ISSUE_DETAILS_CONTENT_ID
                unsafe { +detail }
            }
            if (code.isNotEmpty()) {
                div {
                    id = XygeniConstants.CODE_SNIPPET_CONTENT_ID
                    unsafe { +code }
                }
            }
            if (codeFlow.isNotEmpty() ) {
                div{
                    id=  XygeniConstants.CODE_FLOW_CONTENT_ID
                    unsafe { +codeFlow }
                }
            }
            if (fix.isNotEmpty()) {
                div {
                    id = XygeniConstants.FIX_IT_CONTENT_ID
                    unsafe { +fix }
                }
            }


        }
    }


    protected abstract fun renderCustomIssueDetails(issue: T): String

    //region CodeSnippet
    protected open fun renderCustomCodeSnippet(issue: T): String {
        val code = issue.code
        val file = issue.file
        val beginLine = issue.beginLine
        if (code.isEmpty() || file.isEmpty()) return ""
        return renderCodeSnippet(file, code, beginLine)
    }

    private fun renderCodeSnippet(file: String, code: String, beginLine: Int): String {
        if (code.isEmpty()) return ""
        return createHTML().div {
            p(classes = "file") {
                text(file)
            }
            table {
                tbody {
                    code.lines().mapIndexed { index, line ->
                        tr {
                            val pos = beginLine + index
                            td(classes = "line-number") { +"$pos" }
                            td(classes = "code-line") { unsafe { +line } }
                        }
                    }
                }
            }
        }
    }
    //endregion

    // region Fix
    protected open fun renderCustomFix(issue: T): String {
        if (issue.remediableLevel.isEmpty() || !issue.remediableLevel.contentEquals("AUTO")) {
            return ""
        }

        val remediation = issue.toRemediationData()
        val json = Json.encodeToString(RemediationData.serializer(), remediation)

        return createHTML().div {
            p {
                text(XygeniConstants.REMEDIATION_TEXT)
            }
            form {
                id = XygeniConstants.REMEDIATION_FORM_ID
                p {
                    text(XygeniConstants.REMEDIATION_EXPLANATION)
                }
                hiddenInput { id = "remediation-data"; value = json }

                div {
                    id = XygeniConstants.REMEDIATION_BUTTONS_ID
                    button {
                        id = XygeniConstants.REMEDIATION_BUTTON_ID; type = ButtonType.button; classes = setOf("xy-button");
                        onClick =
                            "this.disabled = true; this.innerText = 'Processing...';pluginAction('remediate', document.getElementById('remediation-data').value)"
                        +"Remediate with Xygeni Agent"
                    }
                    button {
                        hidden = true
                        id = XygeniConstants.REMEDIATION_SAVE_BUTTON_ID; type = ButtonType.button; classes = setOf("xy-button");
                        onClick =
                            "this.style.display='none'; this.disabled = true; this.innerText = 'Saving...';pluginAction('save', document.getElementById('remediation-data').value)"
                        +"Save"
                    }
                }
            }
        }
    }
    // endregion

    // region Helpers
    protected fun renderTags(tags: List<String>): String {
        return createHTML().div(classes = "xy-container-chip") {
            tags.forEach { tag ->
                div(classes = "xy-blue-chip") {
                    +tag
                }
            }
        }
    }

    protected fun renderLink(link: String, text: String): String {
        return createHTML().a(href = "$link", target = "_blank") {
            text(text)
        }
    }

    protected fun renderDetailTableLine(key: String?, value: String?): String {
        if (key.isNullOrBlank() || value.isNullOrBlank()) return ""
        return createHTML().tr {
            th { +key }
            td { +value }
        }
    }

    protected fun renderDetailBranch(branch: String?): String {
        if (branch.isNullOrBlank()) return ""
        return createHTML().tr {
            th { +"Where" }; td {
            unsafe { +branchIcon.orEmpty() }
            +branch
        }
        }
    }

    protected fun renderDetailTags(issueTags: List<String>?): String {
        if (issueTags.isNullOrEmpty()) return ""
        val tags = renderTags(issueTags)
        if (tags.isEmpty()) return ""

        return createHTML().tr {
            th { +"Tags" }
            td { unsafe { +tags } }
        }

    }
    //endregionF"D

    //region CodeFlow
    protected open fun renderCodeFlow(issue: T): String {
        return ""
    }
    //endregion

    protected fun renderDetectorInfo(issue: T): String {
        if (issue.kind == "") {
            return ""
        }
        return createHTML().p {
            span { id = XygeniConstants.LOADING_SPAN_ID; text(XygeniConstants.LOADING_TEXT) }
            p {}
            a(href = "#", target = "_blank") {
                id = XygeniConstants.LINK_TO_DOC_ID
                hidden = true
                text(XygeniConstants.LINK_TO_DOC)
            }
        }
    }

    /**
     * render renders the full HTM
     */
    override fun render(issue: T): String {
        val cssContent = loadCssResource()
        val customHeader = renderCustomHeader(issue)
        val header = renderCommonHeader(issue)
        val issueDetail = renderTabs(issue)

        return createHTML().html {
            head {
                title(issue.type)
                meta(charset = "UTF-8")
                inlineCss(cssContent)
                unsafe { + diagramFunctionScript}
            }
            body {
                script {
                    // Inject the global JS helper to allow live data rendering
                    unsafe {
                        +RENDER_DATA_JS.trimIndent()
                    }
                }
                // Render static sections (header, issue details, etc.)
                unsafe {
                    +header
                    +customHeader
                    +issueDetail
                }
            }
        }
    }
}