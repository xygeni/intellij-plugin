package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.BaseXygeniIssue
import com.intellij.util.ui.UIUtil
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
                " --intellij-font-family: '${font.family}';\n" +
                " --intellij-foreground: ${colorToCss(fg)};\n" +
                " --intellij-background: ${colorToCss(bg)};\n" +
                "}\n" +
                "body {\n" +
                " font-family: var(--intellij-font-family);\n" +
                " font-size: var(--intellij-font-size);\n" +
                " color: var(--intellij-foreground);\n" +
                " background-color: var(--intellij-background);\n" +
                "            }"

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

    /*
    <section class="xy-tabs-section">
    <input type="radio" name="tabs" id="tab-1" checked="">
    <label for="tab-1">ISSUE DETAILS</label>
    <input type="radio" name="tabs" id="tab-2">
    <label for="tab-2">CODE SNIPPET</label>
    <input type="radio" name="tabs" id="tab-3">
    <label id="tab-3-label" for="tab-3">FIX IT</label>
     */
    protected open fun renderTabs(issue: T): String {
        val detail = renderCustomIssueDetails(issue)
        val code = renderCustomCodeSnippet(issue)
        val fix = renderCustomFix(issue)
        return createHTML().section(classes = "xy-tabs-section") {
            if (detail.isNotEmpty() ){
                input(type = InputType.radio, name = "tabs") { id = "tab-1"; checked=true}
                label { htmlFor = "tab-1"; +"ISSUE DETAILS"}
            }
            if (code.isNotEmpty()) {
                input(type = InputType.radio, name = "tabs") {id = "tab-2"}
                label {htmlFor = "tab-2"; +"CODE SNIPPET"}
            }
            if (fix.isNotEmpty()) {
                input(type = InputType.radio, name = "tabs") {id = "tab-3"}
                label { htmlFor = "tab-3"; +"FIX IT"}
            }
        }
    }

    protected open fun renderCustomIssueDetails(issue: T): String = "asdasd"
    protected open fun renderCustomCodeSnippet(issue: T): String = "asdasd"
    protected open fun renderCustomFix(issue: T): String = "adsasd"



    protected abstract fun renderCustomHeader(issue: T): String

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
                unsafe {
                    +header
                    +customHeader
                    +issueDetail
                }
            }
        }
    }
}