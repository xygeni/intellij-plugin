package com.github.xygeni.intellij.dynamichtml.editor

/**
 * HtmlDetailEditor — what the issue views need from whichever editor shows the detail HTML:
 * the JCEF-backed [DynamicHtmlFileEditor] or, on IDEs whose JBR ships without JCEF
 * (e.g. Android Studio), the Swing-backed [SwingHtmlFileEditor] (#1976).
 **/
interface HtmlDetailEditor {
    fun loadHtml(html: String)
    fun renderData(json: String)
}
