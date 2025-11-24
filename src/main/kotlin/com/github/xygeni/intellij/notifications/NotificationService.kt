package com.github.xygeni.intellij.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object NotificationService {
    private const val GROUP_ID = "Xygeni Notification Group"

    fun notifyInfo(content: String, project: Project? = null) {
        notify(content, NotificationType.INFORMATION, project)
    }

    fun notifyError(content: String, project: Project? = null) {
        notify(content, NotificationType.ERROR, project)
    }

    fun notifyWarn(content: String, project: Project? = null) {
        notify(content, NotificationType.WARNING, project)
    }

    private fun notify(content: String, type: NotificationType, project: Project?) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(content, type)
            .notify(project)
    }
}
