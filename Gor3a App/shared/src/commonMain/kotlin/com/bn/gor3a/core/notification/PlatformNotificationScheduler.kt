package com.bn.gor3a.core.notification

import com.bn.gor3a.domain.model.DosageReminder

expect object PlatformNotificationScheduler {
    fun scheduleReminder(reminder: DosageReminder)
    fun cancelReminder(reminderId: String)
    fun showNotification(title: String, body: String)
}
