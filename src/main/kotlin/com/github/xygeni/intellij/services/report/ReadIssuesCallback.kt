package com.github.xygeni.intellij.services.report

import com.github.xygeni.intellij.model.report.BaseXygeniIssue

/**
 * ReadIssuesCallback
 *
 * @author : Carmendelope
 * @version : 10/10/25 (Carmendelope)
 **/
fun interface ReadIssuesCallback {
    fun onComplete(success: Boolean,issues: List<BaseXygeniIssue>)
}