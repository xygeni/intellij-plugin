package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.BaseXygeniIssue

/**
 * IssueRenderer
 *
 * @author : Carmendelope
 * @version : 21/10/25 (Carmendelope)
 **/
interface IssueRenderer<T : BaseXygeniIssue> {
    fun render(issue: T): String
}