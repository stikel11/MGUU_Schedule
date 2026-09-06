package com.mguuschedule.util

import android.Manifest
import android.R
import android.app.AlarmManager
import android.app.Notification
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
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.repository.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                NotificationChannel(CHANNEL_CLASS_REMINDERS, "Напоминания о парах", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Уведомления перед началом занятий"
                    enableVibration(true)
                }
            )

            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_SCHEDULE_CHANGES, "Изменения в расписании", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Уведомления о переносах и заменах"
                    enableVibration(true)
                }
            )

            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_LIVE_UPDATES, "Live updates", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Активные занятия в строке состояния (System Status Chip)"
                    setShowBadge(true)
                }
            )

            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_TEST, "Расписание (Тест)", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                }
            )
        }
    }

    /**
     * Создание системного Live Update уведомления (Android 16 Promoted Ongoing Status Chip + Progress Bar).
     */
    fun showLiveUpdateNotification(
        context: Context,
        title: String,
        message: String,
        subText: String = "",
        shortText: String,
        targetTimeMillis: Long,
        startTimeMillis: Long = System.currentTimeMillis() - 2700000L,
        notificationId: Int = LiveUpdateManager.LIVE_UPDATE_NOTIFICATION_ID,
        lessonId: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.e("NOTIFICATION", "Нет разрешения POST_NOTIFICATIONS для Live Update!")
            return
        }

        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (lessonId != null) {
                putExtra("navigate_to_lesson_id", lessonId)
                action = "com.mguuschedule.ACTION_VIEW_LESSON"
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            lessonId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = System.currentTimeMillis()
        val start = if (startTimeMillis > 0 && startTimeMillis < targetTimeMillis) {
            startTimeMillis
        } else {
            (targetTimeMillis - 2700000L).coerceAtMost(now)
        }
        val totalDuration = (targetTimeMillis - start).coerceAtLeast(1L)
        val elapsed = (now - start).coerceAtLeast(0L)

        val maxProgress = 1000
        val currentProgress = ((elapsed.toDouble() / totalDuration) * maxProgress)
            .toInt()
            .coerceIn(0, maxProgress)

        if (Build.VERSION.SDK_INT >= 36) { // Android 16+ API 36 Promoted Ongoing API
            val builder = Notification.Builder(context, CHANNEL_LIVE_UPDATES)
                .setSmallIcon(com.mguuschedule.R.drawable.baseline_schedule_24)
                .setContentTitle(title)
                .setContentText(message)
                .apply {
                    if (subText.isNotBlank()) setSubText(subText)
                }
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_EVENT)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(targetTimeMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setColorized(false)
                .setProgress(maxProgress, currentProgress, false)

            try {
                val setPromotedOngoingMethod = builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                setPromotedOngoingMethod.invoke(builder, true)
            } catch (_: Exception) {}

            try {
                val setShortCriticalTextMethod = builder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
                setShortCriticalTextMethod.invoke(builder, shortText)
            } catch (_: Exception) {}

            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(notificationId, builder.build())
                AppLogger.d("NOTIFICATION", "Отправлено нативное LiveUpdate (Android 16 API 36): $title ($currentProgress/$maxProgress)")
            } catch (e: Exception) {
                AppLogger.e("NOTIFICATION", "Ошибка отправки LiveUpdate (API 36): ${e.message}", e)
            }
        } else { // Fallback for API < 36 using NotificationCompat and System Extras
            val builder = NotificationCompat.Builder(context, CHANNEL_LIVE_UPDATES)
                .setSmallIcon(com.mguuschedule.R.drawable.baseline_schedule_24)
                .setContentTitle(title)
                .setContentText(message)
                .apply {
                    if (subText.isNotBlank()) setSubText(subText)
                }
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(targetTimeMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setColorized(false)
                .setProgress(maxProgress, currentProgress, false)

            val extras = Bundle()
            extras.putBoolean("android.requestPromotedOngoing", true)
            extras.putCharSequence("android.shortCriticalText", shortText)
            builder.addExtras(extras)

            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
                AppLogger.d("NOTIFICATION", "Отправлено LiveUpdate (Compat + Extras): $title ($currentProgress/$maxProgress)")
            } catch (e: Exception) {
                AppLogger.e("NOTIFICATION", "Ошибка отправки LiveUpdate (Compat): ${e.message}", e)
            }
        }
    }

    fun cancelNotification(context: Context, id: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(id)
    }

    fun showNotification(context: Context, channelId: String, title: String, message: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.e("NOTIFICATION", "Нет разрешения POST_NOTIFICATIONS для отправки уведомления!")
            return
        }

        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            AppLogger.d("NOTIFICATION", "Отправлено уведомление ID $notificationId ($title)")
            
            // Сохраняем уведомление в локальную историю в Room DB
            val type = when (channelId) {
                CHANNEL_SCHEDULE_CHANGES -> "CHANGE"
                CHANNEL_CLASS_REMINDERS -> "REMINDER"
                CHANNEL_LIVE_UPDATES -> "LIVE_UPDATE"
                else -> "SYSTEM"
            }
            saveNotificationToHistory(context, title, message, type)
        } catch (e: Exception) {
            AppLogger.e("NOTIFICATION", "Ошибка отправки уведомления: ${e.message}", e)
        }
    }

    private fun saveNotificationToHistory(context: Context, title: String, message: String, type: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                db.notificationDao().insertNotification(
                    NotificationEntity(
                        title = title,
                        message = message,
                        type = type,
                        timestamp = System.currentTimeMillis(),
                        isRead = false
                    )
                )
                AppLogger.d("NOTIFICATION", "Уведомление сохранено в историю DB: $title")
            } catch (e: Exception) {
                AppLogger.e("NOTIFICATION", "Ошибка записи уведомления в историю DB: ${e.message}")
            }
        }
    }

    fun triggerInstantTestPush(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Нет разрешения на уведомления (POST_NOTIFICATIONS)!", Toast.LENGTH_SHORT).show()
            return
        }

        showNotification(
            context,
            CHANNEL_TEST,
            "Тестовое напоминание о паре",
            "Через 15 мин: Теория организации (${formatClassroom("423")})",
            9991
        )
    }

    fun triggerLiveUpdateNotification(context: Context) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 2700000L // +45 min
        showLiveUpdateNotification(
            context = context,
            title = "Идет пара: Теория организации",
            message = formatClassroom("423"),
            subText = "",
            shortText = "423 • 45м",
            targetTimeMillis = endTime,
            startTimeMillis = startTime,
            notificationId = LiveUpdateManager.LIVE_UPDATE_NOTIFICATION_ID
        )
    }

    fun scheduleClassReminder(context: Context, lesson: Lesson, minutesBefore: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val roomFormatted = formatClassroom(lesson.room)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", lesson.title)
            putExtra("time", lesson.startTime.toString())
            putExtra("room", roomFormatted)
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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
                AppLogger.d("REMINDER", "Запланировано напоминание для '${lesson.title}' на $lessonDateTime (за $minutesBefore мин)")
            } catch (e: Exception) {
                AppLogger.e("REMINDER", "Ошибка установки будильника для '${lesson.title}': ${e.message}", e)
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } catch (e2: Exception) {
                    AppLogger.e("REMINDER", "Резервный AlarmManager завершился ошибкой: ${e2.message}", e2)
                }
            }
        }
    }
}
