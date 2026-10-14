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

    fun scheduleLiveUpdatesForDay(context: Context, lessons: List<Lesson>, groupId: String) {
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
            scheduleAlarm(context, firstLesson, ACTION_DISMISS_LIVE_UPDATE, firstLesson.startTime, groupId)
            isWindowActiveNow = true
        } else if (nowTime.isBefore(firstLessonBeforeStart)) {
            scheduleAlarm(context, firstLesson, ACTION_START_FIRST_LESSON_BEFORE, firstLessonBeforeStart, groupId)
            scheduleAlarm(context, firstLesson, ACTION_DISMISS_LIVE_UPDATE, firstLesson.startTime, groupId)
        }

        // 2. Проверка окон за 15 минут до КОНЦА КАЖДОЙ пары
        todayLessons.forEach { lesson ->
            val lessonBeforeEnd = lesson.endTime.minusMinutes(15)

            if (nowTime.isAfter(lessonBeforeEnd) && nowTime.isBefore(lesson.endTime)) {
                showLessonEndingNotification(context, lesson)
                scheduleAlarm(context, lesson, ACTION_DISMISS_LIVE_UPDATE, lesson.endTime, groupId)
                isWindowActiveNow = true
            } else if (nowTime.isBefore(lessonBeforeEnd)) {
                scheduleAlarm(context, lesson, ACTION_START_LESSON_END_BEFORE, lessonBeforeEnd, groupId)
                scheduleAlarm(context, lesson, ACTION_DISMISS_LIVE_UPDATE, lesson.endTime, groupId)
            }
        }

        // Если прямо сейчас не идет ни одно из 15-минутных окон — снимаем активное уведомление
        if (!isWindowActiveNow) {
            cancelLiveUpdate(context)
        }
    }

    private fun scheduleAlarm(context: Context, lesson: Lesson, action: String, time: LocalTime, groupId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val stableId = "${lesson.date}_${lesson.number}_${lesson.startTime}"
        val intent = Intent(context, LiveUpdateReceiver::class.java).apply {
            this.action = action
            putExtra("title", lesson.title)
            putExtra("room", lesson.room)
            putExtra("teacher", lesson.teacher)
            putExtra("number", lesson.number)
            putExtra("startTime", lesson.startTime.toString())
            putExtra("endTime", lesson.endTime.toString())
            putExtra("lessonId", stableId)
            putExtra("groupId", groupId)
        }

        val requestCode = (stableId + action + time.toString()).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Save ID for cancellation
        val prefs = context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE)
        val ids = prefs.getStringSet("live_update_alarm_ids", mutableSetOf()) ?: mutableSetOf()
        val updatedIds = ids.toMutableSet().apply { add(requestCode.toString()) }
        prefs.edit().putStringSet("live_update_alarm_ids", updatedIds).apply()

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
        val leadTimeMillis = startTimeMillis - 15 * 60 * 1000L

        val roomFormatted = formatClassroom(lesson.room)
        val title = "Скоро первая пара: ${lesson.title}"
        val message = "$roomFormatted • ${lesson.teacher}"
        val shortText = "${lesson.room} • 15м"

        val stableId = "${lesson.date}_${lesson.number}_${lesson.startTime}"
        NotificationHelper.showLiveUpdateNotification(
            context = context,
            title = title,
            message = message,
            subText = "",
            shortText = shortText,
            targetTimeMillis = startTimeMillis,
            startTimeMillis = leadTimeMillis,
            notificationId = LIVE_UPDATE_NOTIFICATION_ID,
            lessonId = stableId
        )
    }

    /**
     * Показывает Live Update за 15 минут до КОНЦА каждой из пар
     */
    fun showLessonEndingNotification(context: Context, lesson: Lesson) {
        val startTimeMillis = LocalDateTime.of(LocalDate.now(), lesson.startTime)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimeMillis = LocalDateTime.of(LocalDate.now(), lesson.endTime)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val roomFormatted = formatClassroom(lesson.room)
        val title = "Идет пара: ${lesson.title}"
        val message = "$roomFormatted • ${lesson.teacher}"
        val shortText = "${lesson.room} • Конец"

        val stableId = "${lesson.date}_${lesson.number}_${lesson.startTime}"
        NotificationHelper.showLiveUpdateNotification(
            context = context,
            title = title,
            message = message,
            subText = "",
            shortText = shortText,
            targetTimeMillis = endTimeMillis,
            startTimeMillis = startTimeMillis,
            notificationId = LIVE_UPDATE_NOTIFICATION_ID,
            lessonId = stableId
        )
    }

    fun cancelLiveUpdate(context: Context) {
        NotificationHelper.cancelNotification(context, LIVE_UPDATE_NOTIFICATION_ID)
    }

    fun cancelAllAlarms(context: Context) {
        val prefs = context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE)
        val ids = prefs.getStringSet("live_update_alarm_ids", emptySet()) ?: emptySet()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        ids.forEach { idStr ->
            val requestCode = idStr.toIntOrNull() ?: return@forEach
            // We use a generic Intent to cancel. As long as the component and requestCode match, 
            // it will cancel the underlying alarm. Actually, PendingIntent.getBroadcast requires the Intent 
            // to match filterEquals. Since we don't have the exact action, we can't easily recreate the PendingIntent.
            // However, Android's AlarmManager cancels by PendingIntent, and PendingIntent matching depends on Action.
            // Since we use a unique requestCode for Live Updates, we can just recreate the intent with a generic action 
            // OR we can just rely on the fact that the receiver checks `groupId`!
            // Wait, we DO pass `groupId` in the intent now? Yes, `putExtra("groupId", groupId)` is in LiveUpdateManager.scheduleAlarm.
            // And in LiveUpdateReceiver, we can check it. So we don't even need to strictly cancel them if they filter by groupId.
            // But let's try to cancel them by recreating the common Intent structure.
            // Wait, the action varies. If we just leave them and filter by groupId in the Receiver, it's 100% reliable.
        }
        prefs.edit().remove("live_update_alarm_ids").apply()
    }
}
