package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.sast.SastXygeniIssue
import com.github.xygeni.intellij.render.XygeniConstants.EXPLANATION_KEY
import com.github.xygeni.intellij.render.XygeniConstants.FOUND_BY_KEY
import com.github.xygeni.intellij.render.XygeniConstants.LOCATION_KEY
import com.google.gson.Gson
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * SastIssueRenderer
 *
 * @author : Carmendelope
 * @version : 22/10/25 (Carmendelope)
 **/
class SastIssueRenderer : BaseHtmlIssueRenderer<SastXygeniIssue>() {
    override fun renderCustomHeader(issue: SastXygeniIssue): String {
        return createHTML().p {
            unsafe {
                +"${issue.categoryName}&nbsp;&nbsp;&nbsp;${issue.kind}&nbsp;&nbsp;&nbsp;"
            }
            issue.cwes?.forEach { cve ->
                val value = cve.substringAfterLast("-")
                unsafe {
                    +renderLink("https://cwe.mitre.org/data/definitions/$value.html", cve)
                    "&nbsp;&nbsp;&nbsp;"
                }
            }
        }
    }

    override fun renderCustomIssueDetails(issue: SastXygeniIssue): String {
        val tags = renderTags(issue.tags)
        return createHTML().div {
            table {
                tbody {
                    unsafe { +renderDetailTableLine(EXPLANATION_KEY, issue.explanation) }
                    unsafe { +renderDetailTableLine("Type", issue.type) }
                    unsafe { +renderDetailBranch(issue.branch) }
                    unsafe { +renderDetailTableLine("Language", issue.language) }
                    unsafe { +renderDetailTableLine(LOCATION_KEY, issue.file) }
                    unsafe { +renderDetailTableLine(FOUND_BY_KEY, issue.detector) }
                    unsafe { +renderDetailTags(issue.tags) }
                }
            }
            unsafe {
                +renderDetectorInfo(issue)
            }
        }
    }

    override fun renderCodeFlow(issue: SastXygeniIssue): String {
        if (issue.codeFlows.isNullOrEmpty() ){
            return ""
        }

        val paths = mutableListOf<List<String>>()
        // Use String keys for the map, but store objects with "id" (String) and "level" (Int)
        // because we will serialize this map's values to JSON for the 'nodes' argument.
        val nodesMap = mutableMapOf<String, Map<String, Any>>()
        val diagramLinks = mutableListOf<Map<String, String>>()

        issue.codeFlows.forEach { flow ->
            val currentPathNodes = mutableListOf<String>()
            val frames = flow.frames ?: emptyList()
            
            frames.forEachIndexed { index, frame ->
                // Construct a display label like "VulnerableApp.java(9)"
                val filePath = frame.location?.filepath ?: ""
                val fileName =  frame.location?.filepath?.substringAfterLast("/") ?: "Unknown"
                val lineNum = frame.location?.beginLine ?: 0
                val id = "$filePath($lineNum)"
                val label = "$fileName($lineNum)"
                val category = frame.category ?: ""
                val container = frame.container ?: ""
                val kind = frame.kind  ?: "Unknown"
                val code = frame.location?.code ?: ""
                val  injectionPoint = frame.injectionPoint ?: ""

                
                currentPathNodes.add(id)

                val key = "${id}__${index}"
                if (!nodesMap.containsKey(key)) {
                    nodesMap[key] = mapOf(
                        "id" to id,
                        "label" to label,
                        "level" to index,
                        "filePath" to filePath,
                        "line" to lineNum,
                        "category" to category,
                        "container" to container,
                        "type" to kind,
                        "code" to code,
                        "injectionPoint" to injectionPoint
                    )
                }
                
                if (index > 0) {
                    val prevFrame = frames[index - 1]
                    val prevFilePath = prevFrame.location?.filepath ?: ""
                    val prevFileName = prevFrame.location?.filepath?.substringAfterLast("/") ?: "Unknown"
                    val prevLineNum = prevFrame.location?.beginLine ?: 0
                    val prevId = "$prevFilePath($prevLineNum)"
                    
                    val sourceKey = "${prevId}__${index - 1}"
                    val targetKey = key
                    // The D3 script expects links with "source" and "target" matching the node keys (id__level)
                    // Wait, checking the JS: 
                    //   d.source is "id__level" string -> nodeMap.get(d.source) 
                    // So we must pass the keys here.
                    diagramLinks.add(mapOf("source" to sourceKey, "target" to targetKey))
                }
            }
            if (currentPathNodes.isNotEmpty()) {
                paths.add(currentPathNodes)
            }
        }

        val nodes = nodesMap.values.toList()


        return """
            <div class="xy-view-toggle">
                <button id="btn-graph" class="xy-toggle-btn active" onclick="switchView('graph')">Graph view</button>
                <button id="btn-text" class="xy-toggle-btn" onclick="switchView('text')">Path</button>
            </div>
            <div id="code-flow-container" style="min-height: auto; position: relative;">
                <!-- Content will be rendered here -->
            </div>

            <script>
                const flowNodes = ${Gson().toJson(nodes)};
                const flowLinks = ${Gson().toJson(diagramLinks)};
                const flowPaths = ${Gson().toJson(paths)};
                let currentView = 'graph';

                function switchView(view) {
                    currentView = view;
                    document.getElementById('btn-graph').classList.toggle('active', view === 'graph');
                    document.getElementById('btn-text').classList.toggle('active', view === 'text');
                    
                    const container = document.getElementById('code-flow-container');
                    if (view === 'graph') {
                        const maxLevel = Math.max(...flowNodes.map(n => n.level), 0);
                        const rowSpacing = 120; // Match BaseHtmlIssueRenderer.kt
                        const requiredHeight = (maxLevel * rowSpacing) + 180;
                        container.style.height = requiredHeight + 'px';
                        container.style.minHeight = '0';
                    } else {
                        container.style.minHeight = '0';
                        container.style.height = 'auto';
                    }
                    
                    render();
                }

                function render() {
                    const containerId = "#code-flow-container";
                    if (currentView === 'graph') {
                        renderDiagramInTab(containerId, flowNodes, flowLinks, flowPaths);
                    } else {
                        renderTextFlowInTab(containerId, flowNodes);
                    }
                }

                document.getElementById('${XygeniConstants.CODE_FLOW_TAB_ID}').addEventListener('change', function() {
                    if (this.checked) {
                        setTimeout(() => switchView(currentView), 100); // Small delay to ensure container is visible and has size
                    }
                });
            </script>
            """.trimIndent()


    }


}