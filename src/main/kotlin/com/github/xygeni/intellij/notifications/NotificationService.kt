package com.github.xygeni.intellij.notifications

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object NotificationService {
    private const val GROUP_ID = "Xygeni Notification Group"

    fun notifyInfo(content: String, project: Project? = null, action: NotificationAction? = null) {
        notify(content, NotificationType.INFORMATION, project, action)
    }

    fun notifyError(content: String, project: Project? = null) {
        notify(content, NotificationType.ERROR, project)
    }

    fun notifyWarn(content: String, project: Project? = null) {
        notify(content, NotificationType.WARNING, project)
    }

    private fun notify(
        content: String,
        type: NotificationType,
        project: Project?,
        action: NotificationAction? = null
    ) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(content, type)
        if (action != null) {
            notification.addAction(action)
        }
        notification.notify(project)
    }
}
