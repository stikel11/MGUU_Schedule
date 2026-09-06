package com.mguuschedule.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mguuschedule.model.Lesson
import com.mguuschedule.util.AppLogger
import com.mguuschedule.util.LiveUpdateManager
import java.time.LocalDate
import java.time.LocalTime

class LiveUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        AppLogger.d("LIVE_UPDATE_RECEIVER", "Получен интент: $action")

        if (action == LiveUpdateManager.ACTION_DISMISS_LIVE_UPDATE) {
            LiveUpdateManager.cancelLiveUpdate(context)
            return
        }

        val title = intent.getStringExtra("title") ?: return
        val room = intent.getStringExtra("room") ?: ""
        val teacher = intent.getStringExtra("teacher") ?: ""
        val number = intent.getIntExtra("number", 1)
        val startTimeStr = intent.getStringExtra("startTime") ?: "00:00"
        val endTimeStr = intent.getStringExtra("endTime") ?: "00:00"
        val lessonId = intent.getStringExtra("lessonId") ?: "0"

        val lesson = Lesson(
            id = lessonId,
            title = title,
            type = "",
            startTime = LocalTime.parse(startTimeStr),
            endTime = LocalTime.parse(endTimeStr),
            teacher = teacher,
            room = room,
            date = LocalDate.now(),
            number = number
        )

        when (action) {
            LiveUpdateManager.ACTION_START_FIRST_LESSON_BEFORE -> {
                LiveUpdateManager.showFirstLessonUpcomingNotification(context, lesson)
            }
            LiveUpdateManager.ACTION_START_LESSON_END_BEFORE -> {
                LiveUpdateManager.showLessonEndingNotification(context, lesson)
            }
        }
    }
}
