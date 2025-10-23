package com.github.xygeni.intellij.render

import com.github.xygeni.intellij.model.report.secret.SecretsXygeniIssue
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.unsafe

/**
 * SecertIssueRenderer
 *
 * @author : Carmendelope
 * @version : 22/10/25 (Carmendelope)
 **/
class SecretIssueRenderer : BaseHtmlIssueRenderer<SecretsXygeniIssue>() {
    override fun renderCustomHeader(issue: SecretsXygeniIssue): String {
        return createHTML().p {
            unsafe { +"Secret&nbsp;&nbsp;&nbspFamily:&nbsp;&nbsp;${issue.type}&nbsp;&nbsp;&nbsp;" }
        }
    }

}