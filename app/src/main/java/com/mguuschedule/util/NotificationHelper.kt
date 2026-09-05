package com.mguuschedule.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mguuschedule.MainActivity
import com.mguuschedule.model.Lesson
import com.mguuschedule.receiver.NotificationDismissReceiver
import com.mguuschedule.receiver.ReminderReceiver
import java.time.LocalDateTime
import java.time.ZoneId

object NotificationHelper {
    const val CHANNEL_CLASS_REMINDERS = "channel_class_reminders"
    const val CHANNEL_SCHEDULE_CHANGES = "channel_schedule_changes"
    const val CHANNEL_LIVE_UPDATES = "channel_live_updates"
    const val CHANNEL_TEST = "schedule_channel"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_CLASS_REMINDERS, "Напоминания о парах", NotificationManager.IMPORTANCE_DEFAULT)
            )

            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_SCHEDULE_CHANGES, "Изменения в расписании", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                }
            )

            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_LIVE_UPDATES, "Live updates", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Активные занятия в строке состояния"
                    setShowBadge(true)
                }
            )

            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_TEST, "Расписание (Тест)", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    /**
     * Оптимизированное формирование Live Update для Android 16 (Status Bar Chip).
     */
    fun showLiveUpdateNotification(
        context: Context,
        title: String,
        message: String,
        shortText: String,
        progressPercent: Int,
        endTimeMillis: Long,
        notificationId: Int
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_LIVE_UPDATES)
            // Используем системную иконку расписания (монохромная)
            .setSmallIcon(android.R.drawable.ic_menu_today) 
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(endTimeMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setProgress(100, progressPercent, false)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .extend(NotificationCompat.WearableExtender())

        // 1. Нативный метод для Android 16 (API 36+)
        if (Build.VERSION.SDK_INT >= 36) {
            // builder.setRequestPromotedOngoing(true) 
            // Примечание: Пока используем extras, так как NotificationCompat может не иметь метода в старой версии библиотеки
        }

        // 2. Обратная совместимость и поддержка Pixel Status Bar Chip
        val extras = Bundle()
        extras.putBoolean("android.requestPromotedOngoing", true)
        extras.putCharSequence("android.shortCriticalText", shortText)
        builder.addExtras(extras)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    fun cancelNotification(context: Context, id: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(id)
    }

    fun showNotification(context: Context, channelId: String, title: String, message: String, notificationId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(if (channelId == CHANNEL_SCHEDULE_CHANGES || channelId == CHANNEL_TEST) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    fun sendTestNotification(context: Context) {
        showNotification(
            context,
            CHANNEL_TEST,
            "Тест уведомления",
            "Через 15 мин пара в Ауд. 423",
            777
        )
    }

    fun triggerInstantTestPush(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Нет разрешения на уведомления (POST_NOTIFICATIONS)!", Toast.LENGTH_SHORT).show()
            return
        }

        val channelId = "debug_notifications_channel"
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Тестовые уведомления", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Канал для проверки пушей и живых обновлений"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Тестовое напоминание о паре")
            .setContentText("Через 15 мин: Теория организации (Ауд. 423)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(9991, notification)
    }

    fun triggerLiveUpdateNotification(context: Context) {
        val channelId = "live_updates_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Живые обновления", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Таймер пары в шторке и на экране блокировки"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val liveNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Идет пара: Теория организации")
            .setContentText("Ауд. 423 • До конца 45 мин")
            .setSubText("Пара 7")
            .setOngoing(true) // Закрепленный статус
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Завершить",
                // PendingIntent на отмену уведомления 9992
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, NotificationDismissReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        NotificationManagerCompat.from(context).notify(9992, liveNotification)
    }

    fun scheduleClassReminder(context: Context, lesson: Lesson, minutesBefore: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", lesson.title)
            putExtra("time", lesson.startTime.toString())
            putExtra("room", lesson.room)
            putExtra("id", lesson.id.hashCode())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lesson.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lessonDateTime = LocalDateTime.of(lesson.date, lesson.startTime)
        val triggerTime = lessonDateTime.minusMinutes(minutesBefore.toLong())
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (triggerTime > System.currentTimeMillis()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }
}
