package com.bn.gor3a.core.notification

import com.bn.gor3a.domain.model.DosageReminder
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

actual object PlatformNotificationScheduler {

    private val center: UNUserNotificationCenter
        get() = UNUserNotificationCenter.currentNotificationCenter()

    fun requestPermission() {
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { _, _ -> }
    }

    actual fun scheduleReminder(reminder: DosageReminder) {
        if (!reminder.isActive) return
        requestPermission()

        val timesToSchedule = if (reminder.times.isNotEmpty()) reminder.times else listOf(reminder.timeOfDay)

        timesToSchedule.forEachIndexed { index, timeStr ->
            val (hour, minute) = parseHourAndMinute(timeStr)

            val content = UNMutableNotificationContent().apply {
                setTitle("⏰ ميعاد دواء: ${reminder.drugName}")
                setBody("حان الآن موعد جرعتك (${reminder.dose}) - ${reminder.form}${if (!reminder.instructions.isNullOrBlank()) "\nتعليمات: ${reminder.instructions}" else ""}")
                setSound(UNNotificationSound.defaultSound())
            }

            val dateComponents = NSDateComponents().apply {
                setHour(hour.toLong())
                setMinute(minute.toLong())
                setSecond(0L)
            }

            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents = dateComponents,
                repeats = reminder.repeatDaily
            )

            val identifier = "${reminder.id}_$index"
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = identifier,
                content = content,
                trigger = trigger
            )

            center.addNotificationRequest(request) { _ -> }
        }

        showNotification(
            title = "🔔 تم ضبط تنبيه دواء: ${reminder.drugName}",
            body = "سوف نذكرك بمواعيد الجرعات (${timesToSchedule.joinToString("، ")}) بانتظام ❤️"
        )
    }

    actual fun cancelReminder(reminderId: String) {
        val identifiers = (0..10).map { "${reminderId}_$it" }
        center.removePendingNotificationRequestsWithIdentifiers(identifiers)
        center.removeDeliveredNotificationsWithIdentifiers(identifiers)
    }

    actual fun showNotification(title: String, body: String) {
        requestPermission()
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound())
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = "instant_${reminderCounter++}",
            content = content,
            trigger = null
        )

        center.addNotificationRequest(request) { _ -> }
    }

    private var reminderCounter = 0L

    private fun parseHourAndMinute(timeStr: String): Pair<Int, Int> {
        val clean = timeStr.trim()
        val isPm = clean.contains("مساءً") || clean.contains("م") || clean.contains("PM", ignoreCase = true) || clean.contains("ظهراً")
        val isAm = clean.contains("صباحاً") || clean.contains("ص") || clean.contains("AM", ignoreCase = true) || clean.contains("فجراً")

        val numbers = Regex("(\\d{1,2}):(\\d{2})").find(clean)
        if (numbers != null) {
            var hour = numbers.groupValues[1].toIntOrNull() ?: 8
            val minute = numbers.groupValues[2].toIntOrNull() ?: 0

            if (isPm && hour < 12) {
                hour += 12
            } else if (isAm && hour == 12) {
                hour = 0
            }
            return Pair(hour, minute)
        }

        val singleNum = Regex("(\\d{1,2})").find(clean)?.groupValues?.get(1)?.toIntOrNull() ?: 8
        val finalHour = if (isPm && singleNum < 12) singleNum + 12 else singleNum
        return Pair(finalHour, 0)
    }
}
