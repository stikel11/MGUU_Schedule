package com.mguuschedule.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mguuschedule.model.Lesson
import com.mguuschedule.receiver.LiveUpdateReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object LiveUpdateManager {
    const val LIVE_UPDATE_NOTIFICATION_ID = 9992

    const val ACTION_START_FIRST_LESSON_BEFORE = "com.mguuschedule.action.START_FIRST_LESSON_BEFORE"
    const val ACTION_START_LESSON_END_BEFORE = "com.mguuschedule.action.START_LESSON_END_BEFORE"
    const val ACTION_DISMISS_LIVE_UPDATE = "com.mguuschedule.action.DISMISS_LIVE_UPDATE"

    fun scheduleLiveUpdatesForDay(context: Context, lessons: List<Lesson>) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("live_updates_enabled", true)
        if (!enabled) {
            cancelLiveUpdate(context)
            return
        }

        val today = LocalDate.now()
        val todayLessons = lessons.filter { it.date.isEqual(today) }.sortedBy { it.startTime }

        if (todayLessons.isEmpty()) {
            cancelLiveUpdate(context)
            return
        }

        val nowTime = LocalTime.now()
        val firstLesson = todayLessons.first()

        var isWindowActiveNow = false

        // 1. Проверка окна за 15 минут до НАЧАЛА ПЕРВОЙ пары дня
        val firstLessonBeforeStart = firstLesson.startTime.minusMinutes(15)
        if (nowTime.isAfter(firstLessonBeforeStart) && nowTime.isBefore(firstLesson.startTime)) {
            showFirstLessonUpcomingNotification(context, firstLesson)
            scheduleAlarm(context, firstLesson, ACTION_DISMISS_LIVE_UPDATE, firstLesson.startTime)
            isWindowActiveNow = true
        } else if (nowTime.isBefore(firstLessonBeforeStart)) {
            scheduleAlarm(context, firstLesson, ACTION_START_FIRST_LESSON_BEFORE, firstLessonBeforeStart)
            scheduleAlarm(context, firstLesson, ACTION_DISMISS_LIVE_UPDATE, firstLesson.startTime)
        }

        // 2. Проверка окон за 15 минут до КОНЦА КАЖДОЙ пары
        todayLessons.forEach { lesson ->
            val lessonBeforeEnd = lesson.endTime.minusMinutes(15)

            if (nowTime.isAfter(lessonBeforeEnd) && nowTime.isBefore(lesson.endTime)) {
                showLessonEndingNotification(context, lesson)
                scheduleAlarm(context, lesson, ACTION_DISMISS_LIVE_UPDATE, lesson.endTime)
                isWindowActiveNow = true
            } else if (nowTime.isBefore(lessonBeforeEnd)) {
                scheduleAlarm(context, lesson, ACTION_START_LESSON_END_BEFORE, lessonBeforeEnd)
                scheduleAlarm(context, lesson, ACTION_DISMISS_LIVE_UPDATE, lesson.endTime)
            }
        }

        // Если прямо сейчас не идет ни одно из 15-минутных окон — снимаем активное уведомление
        if (!isWindowActiveNow) {
            cancelLiveUpdate(context)
        }
    }

    private fun scheduleAlarm(context: Context, lesson: Lesson, action: String, time: LocalTime) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, LiveUpdateReceiver::class.java).apply {
            this.action = action
            putExtra("title", lesson.title)
            putExtra("room", lesson.room)
            putExtra("teacher", lesson.teacher)
            putExtra("number", lesson.number)
            putExtra("startTime", lesson.startTime.toString())
            putExtra("endTime", lesson.endTime.toString())
            putExtra("lessonId", lesson.id)
        }

        val requestCode = (lesson.id + action + time.toString()).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerDateTime = LocalDateTime.of(LocalDate.now(), time)
        val triggerMillis = triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (triggerMillis > System.currentTimeMillis()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
                AppLogger.d("LIVE_UPDATE", "Запланирован аларм LiveUpdate $action на $time для '${lesson.title}'")
            } catch (e: Exception) {
                AppLogger.e("LIVE_UPDATE", "Ошибка планирования аларма: ${e.message}", e)
            }
        }
    }

    /**
     * Показывает Live Update за 15 минут до начала ПЕРВОЙ пары дня
     */
    fun showFirstLessonUpcomingNotification(context: Context, lesson: Lesson) {
        val startTimeMillis = LocalDateTime.of(LocalDate.now(), lesson.startTime)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val roomFormatted = formatClassroom(lesson.room)
        val title = "Скоро первая пара: ${lesson.title}"
        val message = "$roomFormatted • ${lesson.teacher}"
        val shortText = "${lesson.room} • 15м"

        NotificationHelper.showLiveUpdateNotification(
            context = context,
            title = title,
            message = message,
            subText = "${lesson.number} пара • Начало в ${lesson.startTime}",
            shortText = shortText,
            targetTimeMillis = startTimeMillis,
            notificationId = LIVE_UPDATE_NOTIFICATION_ID,
            lessonId = lesson.id
        )
    }

    /**
     * Показывает Live Update за 15 минут до КОНЦА каждой из пар
     */
    fun showLessonEndingNotification(context: Context, lesson: Lesson) {
        val endTimeMillis = LocalDateTime.of(LocalDate.now(), lesson.endTime)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val roomFormatted = formatClassroom(lesson.room)
        val title = "До конца пары 15 мин: ${lesson.title}"
        val message = "$roomFormatted • ${lesson.teacher}"
        val shortText = "${lesson.room} • Конец"

        NotificationHelper.showLiveUpdateNotification(
            context = context,
            title = title,
            message = message,
            subText = "${lesson.number} пара • Завершение в ${lesson.endTime}",
            shortText = shortText,
            targetTimeMillis = endTimeMillis,
            notificationId = LIVE_UPDATE_NOTIFICATION_ID,
            lessonId = lesson.id
        )
    }

    fun cancelLiveUpdate(context: Context) {
        NotificationHelper.cancelNotification(context, LIVE_UPDATE_NOTIFICATION_ID)
    }
}
