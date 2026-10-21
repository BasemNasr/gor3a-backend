package com.bn.gor3a.core.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class Gor3aReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "gor3a_medication_reminders"
        const val CHANNEL_NAME = "تنبيهات مواعيد الأدوية"
        const val CHANNEL_DESC = "تنبيهات فورية لتناول جرعات الأدوية في مواعيدها المحددة"

        const val EXTRA_REMINDER_ID = "EXTRA_REMINDER_ID"
        const val EXTRA_DRUG_NAME = "EXTRA_DRUG_NAME"
        const val EXTRA_DOSE = "EXTRA_DOSE"
        const val EXTRA_TIME = "EXTRA_TIME"
        const val EXTRA_FORM = "EXTRA_FORM"
        const val EXTRA_INSTRUCTIONS = "EXTRA_INSTRUCTIONS"
        const val EXTRA_REPEAT = "EXTRA_REPEAT"
        const val EXTRA_DOSE_INDEX = "EXTRA_DOSE_INDEX"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                    setSound(soundUri, null)
                }
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val drugName = intent.getStringExtra(EXTRA_DRUG_NAME) ?: "الدواء"
        val dose = intent.getStringExtra(EXTRA_DOSE) ?: "جرعة واحدة"
        val time = intent.getStringExtra(EXTRA_TIME) ?: ""
        val form = intent.getStringExtra(EXTRA_FORM) ?: "أقراص"
        val instructions = intent.getStringExtra(EXTRA_INSTRUCTIONS) ?: ""
        val repeatDaily = intent.getBooleanExtra(EXTRA_REPEAT, true)
        val doseIndex = intent.getIntExtra(EXTRA_DOSE_INDEX, 0)

        createNotificationChannel(context)

        // Launch app intent when notification clicked
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                reminderId.hashCode() + doseIndex,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val notificationTitle = "⏰ حان موعد دواء: $drugName"
        val notificationText = buildString {
            append("الجرعة: $dose ($form)")
            if (time.isNotBlank()) append(" • $time")
            if (instructions.isNotBlank()) append("\nالتعليمات: $instructions")
            append("\nصحتك في أمان مع جرعة ❤️")
        }

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(notificationTitle)
            .setContentText("الجرعة: $dose ($form) - $time")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setSound(defaultSound)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notificationId = (reminderId.hashCode() and 0x7FFFFFFF) + doseIndex
        notificationManager?.notify(notificationId, notification)

        // Reschedule for next day if repeating
        if (repeatDaily) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val nextIntent = Intent(context, Gor3aReminderReceiver::class.java).apply {
                putExtras(intent)
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                (reminderId + "_" + doseIndex).hashCode(),
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextTrigger = System.currentTimeMillis() + AlarmManager.INTERVAL_DAY
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager?.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTrigger,
                        nextPendingIntent
                    )
                } else {
                    alarmManager?.set(
                        AlarmManager.RTC_WAKEUP,
                        nextTrigger,
                        nextPendingIntent
                    )
                }
            } catch (_: Exception) {}
        }
    }
}
