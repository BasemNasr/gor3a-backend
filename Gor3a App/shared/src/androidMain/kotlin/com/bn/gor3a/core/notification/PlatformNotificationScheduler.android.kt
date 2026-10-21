package com.bn.gor3a.core.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.bn.gor3a.domain.model.DosageReminder
import java.util.Calendar

actual object PlatformNotificationScheduler {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        Gor3aReminderReceiver.createNotificationChannel(context.applicationContext)
    }

    actual fun scheduleReminder(reminder: DosageReminder) {
        val context = appContext ?: return
        if (!reminder.isActive) return

        Gor3aReminderReceiver.createNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val timesToSchedule = if (reminder.times.isNotEmpty()) reminder.times else listOf(reminder.timeOfDay)

        timesToSchedule.forEachIndexed { index, timeStr ->
            val (hour, minute) = parseHourAndMinute(timeStr)
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // If this time has already passed today, schedule for tomorrow
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(context, Gor3aReminderReceiver::class.java).apply {
                putExtra(Gor3aReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
                putExtra(Gor3aReminderReceiver.EXTRA_DRUG_NAME, reminder.drugName)
                putExtra(Gor3aReminderReceiver.EXTRA_DOSE, reminder.dose)
                putExtra(Gor3aReminderReceiver.EXTRA_TIME, timeStr)
                putExtra(Gor3aReminderReceiver.EXTRA_FORM, reminder.form)
                putExtra(Gor3aReminderReceiver.EXTRA_INSTRUCTIONS, reminder.instructions ?: "")
                putExtra(Gor3aReminderReceiver.EXTRA_REPEAT, reminder.repeatDaily)
                putExtra(Gor3aReminderReceiver.EXTRA_DOSE_INDEX, index)
            }

            val requestCode = (reminder.id + "_" + index).hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (_: Exception) {
                // Fallback for devices restricting exact alarms
                try {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } catch (_: Exception) {}
            }
        }

        // Send a immediate confirmation notification to assure the user the reminder was scheduled
        showNotification(
            title = "🔔 تم ضبط تنبيه دواء: ${reminder.drugName}",
            body = "سوف نذكرك بمواعيد الجرعات (${timesToSchedule.joinToString("، ")}) بانتظام ❤️"
        )
    }

    actual fun cancelReminder(reminderId: String) {
        val context = appContext ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Cancel up to 10 possible dose indexes for this reminder
        for (i in 0..10) {
            val intent = Intent(context, Gor3aReminderReceiver::class.java)
            val requestCode = (reminderId + "_" + i).hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        for (i in 0..10) {
            val notificationId = (reminderId.hashCode() and 0x7FFFFFFF) + i
            notificationManager?.cancel(notificationId)
        }
    }

    actual fun showNotification(title: String, body: String) {
        val context = appContext ?: return
        Gor3aReminderReceiver.createNotificationChannel(context)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                (title + body).hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(context, Gor3aReminderReceiver.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val id = (System.currentTimeMillis() % 100000).toInt()
        notificationManager?.notify(id, notification)
    }

    private fun parseHourAndMinute(timeStr: String): Pair<Int, Int> {
        val clean = timeStr.trim()
        val isPm = clean.contains("مساءً") || clean.contains("م") || clean.contains("PM", ignoreCase = true) || clean.contains("ظهراً")
        val isAm = clean.contains("صباحاً") || clean.contains("ص") || clean.contains("AM", ignoreCase = true) || clean.contains("فجراً")

        // Extract numbers with regex
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

        // Fallback: single number
        val singleNum = Regex("(\\d{1,2})").find(clean)?.groupValues?.get(1)?.toIntOrNull() ?: 8
        val finalHour = if (isPm && singleNum < 12) singleNum + 12 else singleNum
        return Pair(finalHour, 0)
    }
}
