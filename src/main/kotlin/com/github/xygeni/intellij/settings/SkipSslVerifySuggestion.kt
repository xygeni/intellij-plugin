package com.github.xygeni.intellij.settings

import com.github.xygeni.intellij.events.SETTINGS_CHANGED_TOPIC
import com.github.xygeni.intellij.logger.Logger
import com.github.xygeni.intellij.notifications.NotificationService
import com.intellij.notification.NotificationAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages

/**
 * --skip-ssl-verify for corporate proxies that inspect TLS traffic (xygeni/tech-support#378). The user cannot
 * be expected to know the CLI option, so a scanner call failing on the certificate points at the setting;
 * turning it on weakens transport security, so it is always confirmed first.
 */
object SkipSslVerifySuggestion {

    private const val MESSAGE = "The Xygeni scanner could not validate the server SSL certificate. If you are behind a " +
        "corporate proxy that inspects TLS traffic, enable \"Skip SSL verification\" in the Xygeni configuration."

    @Volatile
    private var suggested = false

    /** Once per IDE session: concurrent scanner calls would repeat it. */
    fun suggest(project: Project?) {
        if (suggested) return
        suggested = true
        Logger.log(MESSAGE, project)
        NotificationService.notifyError(MESSAGE, project, NotificationAction.createSimpleExpiring("Enable Skip SSL verification") {
            confirmAndEnable(project)
        })
    }

    /** Asks for confirmation and turns on --skip-ssl-verify; false when the user cancels. */
    fun confirmAndEnable(project: Project?): Boolean {
        val confirmed = MessageDialogBuilder.yesNo(
            "Skip SSL Certificate Validation?",
            "Use it only when a corporate proxy inspects TLS traffic and re-signs it with an internal CA, so the scan " +
                "fails with certificate errors. This reduces transport security: use it only in trusted networks."
        )
            .yesText("Enable")
            .noText("Cancel")
            .icon(Messages.getWarningIcon())
            .ask(project)
        if (confirmed) {
            XygeniSettings.getInstance().skipSslVerify = true
            project?.messageBus?.syncPublisher(SETTINGS_CHANGED_TOPIC)?.settingsChanged()
        }
        return confirmed
    }
}
