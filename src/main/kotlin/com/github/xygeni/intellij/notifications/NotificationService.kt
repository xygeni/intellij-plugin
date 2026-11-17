package com.github.xygeni.intellij.notifications

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object NotificationService {
    private const val GROUP_ID = "Xygeni Notification Group"

    fun notifyInfo(content: String, project: Project? = null, action: NotificationAction? = null) {
        notify(content, NotificationType.INFORMATION, project, listOfNotNull(action))
    }

    fun notifyError(content: String, project: Project? = null, action: NotificationAction? = null) {
        notify(content, NotificationType.ERROR, project, listOfNotNull(action))
    }

    fun notifyWarn(content: String, project: Project? = null, action: NotificationAction? = null) {
        notify(content, NotificationType.WARNING, project, listOfNotNull(action))
    }

    /** A notification with several actions, e.g. "Update" / "Ignore this version". */
    fun notify(
        content: String,
        type: NotificationType,
        project: Project?,
        actions: List<NotificationAction>
    ) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(content, type)
        actions.forEach { notification.addAction(it) }
        notification.notify(project)
    }
}
