package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.github.xygeni.intellij.model.report.server.RemediationData
import com.intellij.util.ui.UIUtil
import icons.Icons
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.Json
import java.awt.Color
import java.io.File
import kotlin.text.lines

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
        val font = UIUtil.getLabelFont()
        val fg = UIUtil.getLabelForeground()
        val bg = UIUtil.getPanelBackground()


        var root = ":root {\n" +
                " --intellij-font-size: 15px;\n" +
                " --intellij-foreground: ${colorToCss(fg)};\n" +
                " --intellij-background: ${colorToCss(bg)};\n" +
                "}\n"

        val cssFile = File("src/main/resources/html/xygeni.css")
        val cssContent = if (cssFile.exists()) {
            cssFile.readText()
        } else {
            // fallback al classloader para cuando esté empaquetado
            val cssStream = javaClass.classLoader.getResourceAsStream("html/xygeni.css")
                ?: throw IllegalStateException("html/xygeni.css not found")
            cssStream.bufferedReader().use { it.readText() }
        }
        return root + cssContent
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
        return createHTML().section(classes = "xy-tabs-section") {
            if (detail.isNotEmpty()) {
                input(type = InputType.radio, name = "tabs") { id = XygeniConstants.ISSUE_DETAILS_TAB_ID; checked = true }
                label { htmlFor = XygeniConstants.ISSUE_DETAILS_TAB_ID; +XygeniConstants.ISSUE_DETAILS_TAB }
            }
            if (code.isNotEmpty()) {
                input(type = InputType.radio, name = "tabs") { id = XygeniConstants.CODE_SNIPPET_TAB_ID }
                label { htmlFor = XygeniConstants.CODE_SNIPPET_TAB_ID; +XygeniConstants.CODE_SNIPPET_TAB }
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