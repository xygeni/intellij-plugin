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
            /**
             * Renders a graphical diagram (graph view) of the vulnerability code flow using D3.js.
             * 
             * @param {string} containerId - The CSS selector for the SVG container.
             * @param {Array} nodes - Array of node objects containing metadata (id, level, type, etc.).
             * @param {Array} links - Array of link objects defining connections between nodes.
             * @param {Array} paths - Array of paths, where each path is an array of node IDs representing a trace.
             */
            function renderDiagramInTab(containerId, nodes, links, paths) {
                // Initialize the D3 container and clear any existing content
                const container = d3.select(containerId);
                container.selectAll("*").remove();

                /**
                 * Helper function to determine the appropriate stack icon ID for a node.
                 * 
                 * @param {Object} d - The node data object.
                 * @param {Set} finalNodeKeys - A set containing keys of nodes that are 'sinks' (ends of paths).
                 * @returns {string} The SVG symbol ID (e.g., '#stack-top', '#stack-middle').
                 */
                function getStackIconId(d, finalNodeKeys) {
                    const key = d.id + "__" + d.level;
                    const type = (d.type || "").toLowerCase();

                    // Bottom stack for sinks (final nodes)
                    if (finalNodeKeys.has(key)) return '#stack-bottom';
                    // Top stack for sources (level 0)
                    if (d.level === 0) return '#stack-top';
                    // Middle stack for intermediate nodes (sanitizers, propagation, etc.)
                    if (type.includes('sanitizer') || type.includes('propagation')|| type.includes('sink')) return '#stack-middle';
                    // Default stack icon
                    return '#stack-default';
                }

                // Create the root SVG element with 100% dimensions
                const svg = container.append("svg")
                    .attr("width","100%")
                    .attr("height","100%")
                    .style("background","var(--intellij-background)")
                    .style("cursor", "grab");
                    
                // Update cursor during interaction
                svg.on("active", () => svg.style("cursor", "grabbing"));

                // Get container dimensions and append a group 'g' for zooming/panning
                const { width, height } = svg.node().getBoundingClientRect();
                const g = svg.append("g");

                // Define SVG 'defs' for reusable symbol definitions
                const defs = svg.append("defs");
                
                /**
                 * Helper function to create stack symbols (custom SVG icons).
                 * 
                 * @param {string} id - The unique ID for the symbol.
                 * @param {Array} rects - Array of rectangle definitions {x, y, w, h, o, sw}.
                 */
                const createStackSymbol = (id, rects) => {
                    const symbol = defs.append("symbol")
                        .attr("id", id)
                        .attr("viewBox", "0 0 20 20");
                    
                    rects.forEach(r => {
                        symbol.append("rect")
                            .attr("x", r.x).attr("y", r.y)
                            .attr("width", r.w).attr("height", r.h)
                            .attr("rx", 1.5)
                            .attr("fill", "#ffffff")
                            .attr("opacity", r.o)
                            .attr("stroke", "#e2e8f0")
                            .attr("stroke-width", r.sw);
                    });
                };

                // Define different stack icon variants
                createStackSymbol("stack-top", [
                    {x: 1, y: 3, w: 18, h: 4, o: 1.0, sw: 0.4},
                    {x: 2, y: 8, w: 16, h: 4, o: 0.70, sw: 0.6},
                    {x: 3, y: 13, w: 14, h: 4, o: 0.40, sw: 0.8}
                ]);
                createStackSymbol("stack-middle", [
                    {x: 4, y: 3, w: 14, h: 4, o: 0.45, sw: 0.4},
                    {x: 2, y: 8, w: 18, h: 4, o: 1.0, sw: 0.8},
                    {x: 4, y: 13, w: 14, h: 4, o: 0.45, sw: 0.4}
                ]);
                createStackSymbol("stack-bottom", [
                    {x: 3, y: 3, w: 14, h: 4, o: 0.40, sw: 0.8},
                    {x: 2, y: 8, w: 16, h: 4, o: 0.70, sw: 0.6},
                    {x: 1, y: 13, w: 18, h: 4, o: 1.0, sw: 0.4}
                ]);
                createStackSymbol("stack-default", [
                    {x: 2, y: 3, w: 16, h: 4, o: 0.85, sw: 0.5},
                    {x: 2, y: 9, w: 16, h: 4, o: 0.85, sw: 0.5},
                    {x: 2, y: 15, w: 16, h: 4, o: 0.85, sw: 0.5}
                ]);
                
                // Layout constants
                const nodeRadius = 30;
                const colSpacing = 220;
                const rowSpacing = 120;

                // Create a map (ID__LEVEL) to handle nodes that appear at different levels
                const nodeMap = new Map();
                nodes.forEach(d => nodeMap.set(d.id + "__" + d.level, { ...d, paths: [] }));

                // Track which paths (traces) include each node
                paths.forEach((path, pIdx) => {
                    path.forEach((id, level) => {
                        const key = id + "__" + level;
                        const node = nodeMap.get(key);
                        if (!node) return;
                        node.paths.push({ pathIndex: pIdx });
                    });
                });

                // Calculate node positions: X depends on the average path column, Y depends on vertical level
                const allNodes = Array.from(nodeMap.values());
                allNodes.forEach(node => {
                    const avgColumn = node.paths.reduce((sum,p)=>sum+p.pathIndex,0)/node.paths.length;
                    node.x = avgColumn * colSpacing + 150;
                    node.y = node.level * rowSpacing + 80;
                });

                // Render connections (links) as quadratic Bezier curves
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

                // Render directional arrows in the middle of each link
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
                      
                      // Calculate midpoint of the Quadratic Bezier curve at t=0.5
                      const mx = 0.25 * source.x + 0.5 * controlX + 0.25 * target.x;
                      const my = 0.25 * source.y + 0.5 * controlY + 0.25 * target.y;
                      
                      // Arrow head triangle shape
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
                      // Rotate arrow to align with the connection direction
                      const angle = Math.atan2(target.y - source.y, target.x - source.x) * 180 / Math.PI;
                      return `rotate(${'$'}{angle}, ${'$'}{mx}, ${'$'}{my})`;
                  });

                // Identify 'sinks' (the last node in each path)
                const finalNodeKeys = new Set();
                paths.forEach(path => {
                    const lastLevel = path.length - 1;
                    const lastKey = path[lastLevel] + "__" + lastLevel;
                    finalNodeKeys.add(lastKey);
                });

                // Render nodes (groups containing circle and icon)
                const nodeGroups = g.selectAll(".node")
                  .data(allNodes)
                  .enter()
                  .append("g")
                  .attr("class", "node")
                  .attr("transform", d => `translate(${'$'}{d.x}, ${'$'}{d.y})`)
                  .style("cursor", "pointer");

                // Background circle for each node, color-coded by role
                const node = nodeGroups.append("circle")
                  .attr("r", nodeRadius)
                  .attr("fill", d => {
                      const key = d.id + "__" + d.level;
                      const type = (d.type || "").toLowerCase();
                      
                      if (d.level === 0) return "#59C9A6";                // Source (Green)
                      if (finalNodeKeys.has(key)) return "#1f2937";       // Sink (Dark/Black)
                      if (type.includes("sanitizer")) return "#f59e0b";   // Sanitizer (Orange)
                      if (type.includes("propagation")) return "#3b82f6"; // Propagation (Blue)
                      return "#10b981"; // Default (Greenish)
                  })
                  .attr("stroke", d => {
                      const key = d.id + "__" + d.level;
                      const type = (d.type || "").toLowerCase();
                      
                      if (d.level === 0) return "#4d8a7c";
                      if (finalNodeKeys.has(key)) return "#111827";
                      if (type.includes("propagation")) return "#4d8a7c";
                      if (type.includes("sanitizer")) return "#d97706";
                      return "#059669";
                  })
                  .style("filter", d => {
                      const key = d.id + "__" + d.level;
                      const type = (d.type || "").toLowerCase();
                      
                      // Apply shadow effects for depth
                      if (d.level === 0) return "(0 2px 6px rgba(89, 201, 166, 0.4))";
                      if (finalNodeKeys.has(key)) return "(0 3px 8px rgba(0, 0, 0, 0.5))";
                      if (type.includes("propagation")) return "(0 2px 4px rgba(59, 130, 246, 0.4))";
                      if (type.includes("sanitizer")) return "(0 2px 4px rgba(245, 158, 11, 0.3))";
                      return "(0 2px 4px rgba(16, 185, 129, 0.3))";
                  })
                  .attr("stroke-width", 2);

                // Overlay the stack icon symbol on the node circle
                nodeGroups.append("use")
                  .attr("href", d => getStackIconId(d, finalNodeKeys))
                  .attr("x", -15)
                  .attr("y", -15)
                  .attr("width", 30)
                  .attr("height", 30)
                  .style("pointer-events", "none");

                // Render node labels (staggered vertically to avoid overlaps)
                g.selectAll("text.label")
                  .data(allNodes.sort((a, b) => a.level !== b.level ? a.level - b.level : a.x - b.x))
                  .enter()
                  .append("text")
                  .attr("class", "label")
                  .attr("x", d => d.x)
                  .attr("y", (d, i) => d.y + (i % 2 === 0 ? 45 : 65))
                  .attr("text-anchor", "middle")
                  .text(d => d.label || d.id);

                // Render count badges for nodes that are part of multiple flows
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

                // Configure Zoom and Pan behavior
                const zoom = d3.zoom()
                  .scaleExtent([0.2,4])
                  .on("zoom", event => g.attr("transform", event.transform));
                svg.call(zoom);

                // Create Zoom control buttons (+/-)
                 const controls = container.append("div").attr("class","xy-zoom-controls");
                    
                controls.append("button").text("+")
                    .attr("class", "xy-zoom-btn")
                    .on("click", ()=> svg.transition().duration(300).call(zoom.scaleBy,1.2));
                controls.append("button").text("-")
                    .attr("class", "xy-zoom-btn")
                    .on("click", ()=> svg.transition().duration(300).call(zoom.scaleBy,0.8));
                    
                // Create tooltip selection and handle hover interactions
                const tooltip = d3.select("body").append("div").attr("class", "tooltip");
                
                nodeGroups
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

            /**
             * Renders a textual list view (Path view) of the vulnerability steps.
             * 
             * @param {string} containerId - The CSS selector for the container.
             * @param {Array} nodes - Array of node objects to list.
             */
            function renderTextFlowInTab(containerId, nodes) {
                const container = d3.select(containerId);
                container.selectAll("*").remove();

                const steps = container.append("div")
                    .attr("class", "xy-text-flow-container")
                    .selectAll(".xy-flow-step")
                    .data(nodes.sort((a, b) => a.level - b.level))
                    .enter()
                    .append("div")
                    .attr("class", "xy-flow-step");

                // Build each step item in the textual flow
                steps.each(function(d) {
                    const step = d3.select(this);
                    const fileName = d.filePath ? d.filePath.split('/').pop() : 'Unknown';
                    
                    // Header with file name and vulnerability type
                    const header = step.append("div").attr("class", "xy-flow-step-header");
                    header.append("span").attr("class", "xy-flow-step-file").text(`${'$'}{fileName}:${'$'}{d.line}`);
                    header.append("span").attr("class", "xy-flow-step-type").text(d.type);

                    // Full file path section
                    step.append("div").attr("class", "xy-flow-step-path").text(d.filePath);

                    // Detailed metadata section
                    const details = step.append("div").attr("class", "xy-flow-step-details");
                    if (d.category) details.append("span").text("Category: ").append("b").text(d.category);
                    if (d.container) details.append("span").text(" Container: ").append("b").text(d.container);
                    if (d.injectionPoint) details.append("span").text(" InjectionPoint: ").append("b").text(d.injectionPoint);

                    // Code snippet section
                    if (d.code) {
                        step.append("pre").append("code").text(d.code);
                    }
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

    /**
     * Prepares and renders the HTML and JavaScript for the Code Flow tab.
     * This method transforms the issue's code flows into a format suitable for the D3 diagram
     *
     * @param issue The SAST vulnerability issue containing code flow information.
     * @return A string containing HTML and a <script> block for rendering the code flow.
     */
    private fun renderCodeFlowContent(issue: SastXygeniIssue): String {
        // Return empty if there are no code flows to display
        if (issue.codeFlows.isNullOrEmpty()) {
            return ""
        }

        // Data structures to hold processed nodes, links, and paths for the frontend
        val paths = mutableListOf<List<String>>() // List of path traces (node IDs)
        val nodesMap = mutableMapOf<String, Map<String, Any>>() // Map to ensure unique nodes per level
        val diagramLinks = mutableListOf<Map<String, String>>() // List of connections (source -> target)

        // Iterate through each code flow (trace) provided in the issue
        issue.codeFlows.forEach { flow ->
            val currentPathNodes = mutableListOf<String>()
            val frames = flow.frames ?: emptyList()
            
            // Process each frame (step) in the current code flow
            frames.forEachIndexed { index, frame ->
                // Extract metadata from the frame location
                val filePath = frame.location?.filepath ?: ""
                val fileName = frame.location?.filepath?.substringAfterLast("/") ?: "Unknown"
                val lineNum = frame.location?.beginLine ?: 0
                
                // Unique identifiers for the node
                val id = "$filePath($lineNum)" // Logical ID based on file and line
                val label = "$fileName($lineNum)" // User-friendly label
                
                // Detailed step metadata
                val category = frame.category ?: ""
                val container = frame.container ?: ""
                val kind = frame.kind ?: "Unknown"
                val code = frame.location?.code ?: ""
                val injectionPoint = frame.injectionPoint ?: ""

                currentPathNodes.add(id)

                // The key incorporates the 'index' (level) because the same physical node
                // (file:line) can appear at different steps in a single flow.
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
                
                // If this isn't the first node in the flow, create a link from the previous node
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
            // Add the current trace to the list of paths
            if (currentPathNodes.isNotEmpty()) {
                paths.add(currentPathNodes)
            }
        }

        // Convert the nodes map to a list for JSON serialization
        val nodes = nodesMap.values.toList()

        // Generate the HTML container and embedded JavaScript logic
        return """
            <div class="xy-code-flow-container">
            <div class="xy-view-toggle">
                <button id="btn-graph" class="xy-toggle-btn active" onclick="switchView('graph')">Graph view</button>
                <button id="btn-text" class="xy-toggle-btn" onclick="switchView('text')">Path</button>
            </div>
            <div id="code-flow-container" style="min-height: auto; position: relative;">
                <!-- Content will be rendered here via JS -->
            </div>
            </div>
            <script type="application/json" id="vuln-json">${issue.vulnerabilityRaw}</script>
            <div class="xy-view-toggle">
                <button id="how-to-fix" class="xy-toggle-btn active">How to fix</button>                
            </div>
            <script>
                // Pass the processed Kotlin data structures into the JavaScript context
                const flowNodes = ${Gson().toJson(nodes)};
                const flowLinks = ${Gson().toJson(diagramLinks)};
                const flowPaths = ${Gson().toJson(paths)};
                let currentView = 'graph';

                /**
                 * Switches between the Graphical Diagram and textual Path view.
                 * @param {string} view - Either 'graph' or 'text'.
                 */
                function switchView(view) {
                    currentView = view;
                    document.getElementById('btn-graph').classList.toggle('active', view === 'graph');
                    document.getElementById('btn-text').classList.toggle('active', view === 'text');
                    
                    const container = document.getElementById('code-flow-container');
                    if (view === 'graph') {
                        container.style.height = '65%';                        
                        container.style.minHeight = '0';
                    } else {
                        container.style.minHeight = '0';
                        container.style.height = 'auto';
                    }
                    
                    render();
                }

                /**
                 * Delegates the rendering of the current view to the appropriate function.
                 */
                function render() {
                    const containerId = "#code-flow-container";
                    if (currentView === 'graph') {
                        renderDiagramInTab(containerId, flowNodes, flowLinks, flowPaths);
                    } else {
                        renderTextFlowInTab(containerId, flowNodes);
                    }
                }

                // Handle 'How to fix' button clicks to trigger a plugin action
                const button = document.getElementById("how-to-fix");
                button.addEventListener("click", () => {
                    const json = document.getElementById("vuln-json").textContent;
                    pluginAction('explain', json);
                    button.innerText="Processing..."
                    button.disabled=true;
                });

                // Re-render the view when the tab is displayed (to handle dynamic sizing)
                document.getElementById('${XygeniConstants.CODE_FLOW_TAB_ID}').addEventListener('change', function() {
                    if (this.checked) {
                        setTimeout(() => switchView(currentView), 100); 
                    }
                });
            </script>
                       
            """.trimIndent()
    }
}