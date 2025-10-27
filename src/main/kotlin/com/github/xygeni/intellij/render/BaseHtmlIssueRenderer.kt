package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.intellij.util.ui.UIUtil
import icons.Icons
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import java.awt.Color
import java.io.File

/**
 * BaseHtmlIssueRenderer
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/
abstract class BaseHtmlIssueRenderer<T : BaseXygeniIssue> : IssueRenderer<T> {

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
                // " --intellij-font-size: ${font.size}px;\n" +
                " --intellij-font-size: 15px;\n" +
                //" --intellij-font-family: '${font.family}';\n" +
                " --intellij-foreground: ${colorToCss(fg)};\n" +
                " --intellij-background: ${colorToCss(bg)};\n" +
                "}\n"

        val cssFile = File("src/main/resources/html/xygeni.css")
        val cssContent = if (cssFile.exists()) {
            cssFile.readText()
        } else {
            // fallback al classloader para cuando esté empaquetado
            val cssStream = javaClass.classLoader.getResourceAsStream("html/xygeni.css")
                ?: throw IllegalStateException("No se encontró html/xygeni.css en resources")
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
    protected abstract fun renderCustomHeader(issue: T): String

    // CODE SNIPPET
    protected open fun renderCustomIssueDetails(issue: T): String = ""
    protected open fun renderCustomCodeSnippet(issue: T): String {
        val code = issue.code
        val file = issue.file
        val beginLine = issue.beginLine
        if (code.isEmpty() || file.isEmpty()) return ""
        return renderCodeSnippet(file, code, beginLine)
    }
    protected open fun renderTabs(issue: T): String {
        val detail = renderCustomIssueDetails(issue)
        val code = renderCustomCodeSnippet(issue)
        val fix = renderCustomFix(issue)
        return createHTML().section(classes = "xy-tabs-section") {
            if (detail.isNotEmpty()) {
                input(type = InputType.radio, name = "tabs") { id = "tab-1"; checked = true }
                label { htmlFor = "tab-1"; +"ISSUE DETAILS" }
            }
            if (code.isNotEmpty()) {
                input(type = InputType.radio, name = "tabs") { id = "tab-2" }
                label { htmlFor = "tab-2"; +"CODE SNIPPET" }
            }
            if (fix.isNotEmpty()) {
                input(type = InputType.radio, name = "tabs") { id = "tab-3" }
                label { htmlFor = "tab-3"; +"FIX IT" }
            }
            unsafe { +detail }
            unsafe { +code }
        }
    }
    private fun renderCodeSnippet(file: String, code: String, beginLine: Int): String {
        if (code.isEmpty()) return ""
        return createHTML().div {
            id = "tab-content-2"
            p (classes ="file" ){
                text(file)
            }
            table {
                tbody {
                    code.lines().mapIndexed { index, line ->
                        tr {
                            val pos = beginLine + index
                            td(classes = "line-number"){+"$pos"}
                            td(classes= "code-line"){unsafe{+line}}
                        }
                    }
                }
            }
        }
    }

    // FIX
    protected open fun renderCustomFix(issue: T): String = "adsasd"

    // HELPERS
    protected fun renderTags(tags: List<String>): String {
        return createHTML().div(classes = "xy-container-chip") {
            tags.forEach { tag ->
                div(classes = "xy-blue-chip") {
                    +tag
                }
            }
        }
    }
    protected fun renderLink(link: String, text: String):String{
        return createHTML().a (href = "$link", target = "_blank") {
            text(text)
        }
    }

    protected fun renderDetailTableLine(key: String?, value: String?) :String{
        if (key.isNullOrBlank() || value.isNullOrBlank()) return ""
        return createHTML().tr {
            th { +key }
            td { +value } }
    }

    protected fun renderDetailBranch(branch: String?) :String{
        if (branch.isNullOrBlank()) return ""
        return createHTML().tr { th { +"Where" }; td {
            unsafe{ +branchIcon.orEmpty() }
            +branch } }
    }

    protected fun renderDetailTags(issueTags: List<String>?) :String{
        if (issueTags.isNullOrEmpty()) return ""
        val tags = renderTags(issueTags)
        if (tags.isEmpty()) return ""

        return createHTML().tr {
            th { +"Tags" }
            td { unsafe { +tags } }
        }

    }

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
                h1 {
                    id = "title"
                    text("Loading...")
                }
                script {
                    unsafe {
                        +"""
            window.renderData = function(data) {
                if (typeof data === 'string')  data = JSON.parse(data);                
                document.getElementById('title').innerText = data.descriptionDoc || 'Sin título';
            };
             window.domReady = true;
        """.trimIndent()
                    }
                }

                unsafe {
                    +header
                    +customHeader
                    +issueDetail
                }
            }
        }
    }
}