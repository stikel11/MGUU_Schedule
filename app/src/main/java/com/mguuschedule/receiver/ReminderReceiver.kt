package com.mguuschedule.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.util.AppLogger
import com.mguuschedule.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Скоро пара"
        val time = intent.getStringExtra("time") ?: ""
        val room = intent.getStringExtra("room") ?: ""
        val id = intent.getIntExtra("id", 0)
        val date = intent.getStringExtra("date")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (date != null) {
                    val db = AppDatabase.getDatabase(context)
                    // ПРОВЕРКА 1: Проверяем, существует ли еще эта пара в расписании (не отменена/перенесена ли)
                    val lessonExists = db.scheduleDao().getUpcomingLessons(date).any {
                        it.date == date && it.startTime == time && it.subject == title
                    }

                    if (!lessonExists) {
                        AppLogger.d("REMINDER_TRIGGER", "Пара отменена или перенесена, пропускаем уведомление: $title ($time, $date)")
                        return@launch
                    }
                }

                val message = "Начало в $time, ауд. $room"
                
                AppLogger.d("REMINDER_TRIGGER", "Показ уведомления: $title ($time)")
                NotificationHelper.showNotification(
                    context,
                    NotificationHelper.CHANNEL_CLASS_REMINDERS,
                    title,
                    message,
                    id
                )
            } catch (e: Exception) {
                AppLogger.e("REMINDER_TRIGGER", "Ошибка проверки пары: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
