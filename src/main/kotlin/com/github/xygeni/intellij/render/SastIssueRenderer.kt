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

    companion object {
        private val diagramFunctionScript = """
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

            // ADD DIRECTIONAL ARROWS IN THE MIDDLE
            g.selectAll("path.link-arrow")
              .data(links)
              .enter()
              .append("path")
              .attr("fill", "#999")
              .attr("stroke", "#999")
              .attr("stroke-width", 2)
              .attr("d", d => {
                  const source = nodeMap.get(d.source);
                  const target = nodeMap.get(d.target);
                  if(!source || !target) return "";
                  
                  const controlX = source.x + Math.max(30,(target.x-source.x)/2);
                  const controlY = (source.y + target.y)/2;
                  
                  // Midpoint of Quadratic Bezier at t=0.5
                  const mx = 0.25 * source.x + 0.5 * controlX + 0.25 * target.x;
                  const my = 0.25 * source.y + 0.5 * controlY + 0.25 * target.y;
                  
                  // Tangent at t=0.5 is parallel to P2 - P0
                  const angle = Math.atan2(target.y - source.y, target.x - source.x) * 180 / Math.PI;
                  
                  // Filled triangle arrow head
                  return `M${'$'}{mx-7},${'$'}{my-5} L${'$'}{mx},${'$'}{my} L${'$'}{mx-7},${'$'}{my+5} Z`;
              })
              .attr("transform", d => {
                  const source = nodeMap.get(d.source);
                  const target = nodeMap.get(d.target);
                  if(!source || !target) return "";
                  const controlX = source.x + Math.max(30,(target.x-source.x)/2);
                  const controlY = (source.y + target.y)/2;
                  const mx = 0.25 * source.x + 0.5 * controlX + 0.25 * target.x;
                  const my = 0.25 * source.y + 0.5 * controlY + 0.25 * target.y;
                  const angle = Math.atan2(target.y - source.y, target.x - source.x) * 180 / Math.PI;
                  return `rotate(${'$'}{angle}, ${'$'}{mx}, ${'$'}{my})`;
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
                  .attr("fill", d => {
                      const key = d.id + "__" + d.level;
                      if (d.level === 0) return "#69b3a2"; // First is always Green
                      if (finalNodeKeys.has(key)) return "var(--intellij-foreground)"; // Last is always Black/Dark
                      
                      const type = (d.type || "").toLowerCase();
                      if (type.includes("sink")) return "#69b3a2"; // Sink is Green
                      if (type.includes("propagation")) return "#3794FF"; // Propagation is Blue
                      
                      return "#3794FF"; // Default Blue
                  })
                  .attr("stroke", d => {
                      const key = d.id + "__" + d.level;
                      if (d.level === 0) return "#4d8a7c"; // Darker Green
                      if (finalNodeKeys.has(key)) return "#888";     // Grey for Black nodes
                      
                      const type = (d.type || "").toLowerCase();
                      if (type.includes("sink")) return "#4d8a7c";   // Darker Green
                      return "#2a70c2";                             // Darker Blue
                  })
                  .attr("stroke-width", 2);

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
    }

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

    override fun renderAdditionalScripts(issue: SastXygeniIssue, head: HEAD) {
        if (!issue.codeFlows.isNullOrEmpty()) {
            head.unsafe { +diagramFunctionScript }
        }
    }

    override fun renderAdditionalTabs(issue: SastXygeniIssue, section: SECTION) {
        if (!issue.codeFlows.isNullOrEmpty()) {
            section.input(type = InputType.radio, name = "tabs") { id = XygeniConstants.CODE_FLOW_TAB_ID }
            section.label { htmlFor = XygeniConstants.CODE_FLOW_TAB_ID; +XygeniConstants.CODE_FLOW_TAB }
        }
    }

    override fun renderAdditionalTabsContent(issue: SastXygeniIssue, section: SECTION) {
        val codeFlow = renderCodeFlowContent(issue)
        if (codeFlow.isNotEmpty()) {
            section.div {
                id = XygeniConstants.CODE_FLOW_CONTENT_ID
                unsafe { +codeFlow }
            }
        }
    }

    private fun renderCodeFlowContent(issue: SastXygeniIssue): String {
        if (issue.codeFlows.isNullOrEmpty()) {
            return ""
        }

        val paths = mutableListOf<List<String>>()
        val nodesMap = mutableMapOf<String, Map<String, Any>>()
        val diagramLinks = mutableListOf<Map<String, String>>()

        issue.codeFlows.forEach { flow ->
            val currentPathNodes = mutableListOf<String>()
            val frames = flow.frames ?: emptyList()
            
            frames.forEachIndexed { index, frame ->
                val filePath = frame.location?.filepath ?: ""
                val fileName = frame.location?.filepath?.substringAfterLast("/") ?: "Unknown"
                val lineNum = frame.location?.beginLine ?: 0
                val id = "$filePath($lineNum)"
                val label = "$fileName($lineNum)"
                val category = frame.category ?: ""
                val container = frame.container ?: ""
                val kind = frame.kind ?: "Unknown"
                val code = frame.location?.code ?: ""
                val injectionPoint = frame.injectionPoint ?: ""

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
                    val prevLineNum = prevFrame.location?.beginLine ?: 0
                    val prevId = "$prevFilePath($prevLineNum)"
                    
                    val sourceKey = "${prevId}__${index - 1}"
                    val targetKey = key
                    diagramLinks.add(mapOf("source" to sourceKey, "target" to targetKey))
                }
            }
            if (currentPathNodes.isNotEmpty()) {
                paths.add(currentPathNodes)
            }
        }

        val nodes = nodesMap.values.toList()

        return """
            <div class="xy-code-flow-container">
            <div class="xy-view-toggle">
                <button id="btn-graph" class="xy-toggle-btn active" onclick="switchView('graph')">Graph view</button>
                <button id="btn-text" class="xy-toggle-btn" onclick="switchView('text')">Path</button>
            </div>
            <div id="code-flow-container" style="min-height: auto; position: relative;">
                <!-- Content will be rendered here -->
            </div>
            </div>
            <script type="application/json" id="vuln-json">${issue.vulnerabilityRaw}</script>
            <div class="xy-view-toggle">
                <button id="how-to-fix" class="xy-toggle-btn active">How to fix</button>                
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
                        const rowSpacing = 120; 
                        //const requiredHeight = (maxLevel * rowSpacing) + 180;
                        //container.style.height = requiredHeight + 'px';
                        container.style.height = '65%';                        
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
                const button = document.getElementById("how-to-fix");
                // document.getElementById("how-to-fix").addEventListener("click", () => {
                button.addEventListener("click", () => {
                    const json = document.getElementById("vuln-json").textContent;
                    pluginAction('explain', json);
                    button.innerText="Processing..."
                    button.disabled=true;
                });

                document.getElementById('${XygeniConstants.CODE_FLOW_TAB_ID}').addEventListener('change', function() {
                    if (this.checked) {
                        setTimeout(() => switchView(currentView), 100); 
                    }
                });
            </script>
                       
            """.trimIndent()
    }
}