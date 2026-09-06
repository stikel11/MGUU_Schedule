package com.mguuschedule.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mguuschedule.model.toLesson
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.util.AppLogger
import com.mguuschedule.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AppLogger.d("BOOT_RECEIVER", "Получен системный ивент: ${intent.action}")
            NotificationHelper.createNotificationChannels(context)

            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val remindersEnabled = prefs.getBoolean("reminders_enabled", true)
            val reminderMinutes = prefs.getInt("reminder_time", 15)

            if (remindersEnabled) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val todayStr = LocalDate.now().toString()
                        val lessons = db.scheduleDao().getUpcomingLessons(todayStr)
                        lessons.forEach { entity ->
                            entity.toLesson()?.let { lesson ->
                                NotificationHelper.scheduleClassReminder(
                                    context,
                                    lesson,
                                    reminderMinutes
                                )
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e("BOOT_RECEIVER", "Ошибка восстановления будильников: ${e.message}", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
