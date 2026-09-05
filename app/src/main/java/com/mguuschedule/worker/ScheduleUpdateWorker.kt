package com.mguuschedule.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mguuschedule.model.Lesson
import com.mguuschedule.model.LessonEntity
import com.mguuschedule.model.toLesson
import com.mguuschedule.util.AppLogger
import com.mguuschedule.util.NotificationHelper
import com.mguuschedule.util.ScheduleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ScheduleUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val cachePrefs = context.getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
    
    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, com.google.gson.JsonSerializer<LocalDate> { src, _, _ ->
            com.google.gson.JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE))
        })
        .registerTypeAdapter(LocalDate::class.java, com.google.gson.JsonDeserializer<LocalDate> { json, _, _ ->
            LocalDate.parse(json.asString, DateTimeFormatter.ISO_LOCAL_DATE)
        })
        .registerTypeAdapter(LocalTime::class.java, com.google.gson.JsonSerializer<LocalTime> { src, _, _ ->
            com.google.gson.JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_TIME))
        })
        .registerTypeAdapter(LocalTime::class.java, com.google.gson.JsonDeserializer<LocalTime> { json, _, _ ->
            LocalTime.parse(json.asString, DateTimeFormatter.ISO_LOCAL_TIME)
        })
        .create()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val groupId = prefs.getString("selected_group_id", null) ?: return@withContext Result.success()
        val changesEnabled = prefs.getBoolean("changes_enabled", true)
        val remindersEnabled = prefs.getBoolean("reminders_enabled", true)
        val reminderMinutes = prefs.getInt("reminder_time", 15)

        AppLogger.d("WORKER", "Запуск фонового обновления для $groupId")

        try {
            val url = "https://portal.mguu.ru/student/scheduler2.php?groupid=$groupId&startDate=01.09.2026&endDate=30.09.2027"
            val response = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(20000)
                .get()
            
            val newLessons = ScheduleParser.parse(response.html())
            AppLogger.d("WORKER", "Спарсено ${newLessons.size} пар")
            val oldLessonsMapped = getCachedLessons(groupId).map { it.toLesson() }
            val newLessonsMapped = newLessons.map { it.toLesson() }

            if (changesEnabled && oldLessonsMapped.isNotEmpty()) {
                val diffs = findDetailedChanges(oldLessonsMapped, newLessonsMapped)
                diffs.forEachIndexed { index, message ->
                    NotificationHelper.showNotification(
                        applicationContext,
                        NotificationHelper.CHANNEL_SCHEDULE_CHANGES,
                        "Изменение в расписании",
                        message,
                        2000 + index
                    )
                }
            }

            // Автоматически обновляем напоминания на сегодня и завтра
            if (remindersEnabled) {
                val today = LocalDate.now()
                val tomorrow = today.plusDays(1)
                newLessonsMapped.filter { it.date == today || it.date == tomorrow }.forEach { lesson ->
                    NotificationHelper.scheduleClassReminder(applicationContext, lesson, reminderMinutes)
                }
            }

            saveToCache(groupId, newLessons)
            AppLogger.d("WORKER", "Расписание успешно обновлено в кэше")
            Result.success()
        } catch (e: Exception) {
            AppLogger.e("WORKER", "Ошибка в Worker: ${e.message}", e)
            Result.retry()
        }
    }

    private fun getCachedLessons(groupId: String): List<LessonEntity> {
        val json = cachePrefs.getString("cache_$groupId", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LessonEntity>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveToCache(groupId: String, lessons: List<LessonEntity>) {
        val json = gson.toJson(lessons)
        cachePrefs.edit().putString("cache_$groupId", json).putLong("last_update_time", System.currentTimeMillis()).apply()
    }

    private fun findDetailedChanges(old: List<Lesson>, new: List<Lesson>): List<String> {
        val changes = mutableListOf<String>()
        val oldMap = old.associateBy { it.id }
        val newMap = new.associateBy { it.id }
        
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        fun formatDate(date: LocalDate): String {
            return when (date) {
                today -> "Сегодня"
                tomorrow -> "Завтра"
                else -> {
                    val dayName = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("ru"))
                        .replaceFirstChar { it.uppercase() }
                    val monthName = date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale("ru"))
                    "$dayName, ${date.dayOfMonth} $monthName"
                }
            }
        }

        // 1. Проверяем изменения и удаления
        old.forEach { oldLesson ->
            val newLesson = newMap[oldLesson.id]
            val dateStr = formatDate(oldLesson.date)
            
            if (newLesson == null) {
                // Пара пропала из расписания (только для будущих дат)
                if (oldLesson.date >= today) {
                    changes.add("$dateStr: пара отменена: ${oldLesson.title}")
                }
            } else {
                // Пара есть, проверяем поля
                if (oldLesson.room != newLesson.room) {
                    changes.add("$dateStr: ${oldLesson.title} перенесен в Ауд. ${newLesson.room} (была ${oldLesson.room})")
                }
                if (oldLesson.teacher != newLesson.teacher) {
                    changes.add("$dateStr: ${oldLesson.title}: замена преподавателя на ${newLesson.teacher}")
                }
            }
        }

        // 2. Проверяем новые пары
        new.forEach { newLesson ->
            if (!oldMap.containsKey(newLesson.id) && newLesson.date >= today) {
                val dateStr = formatDate(newLesson.date)
                changes.add("$dateStr: новая пара ${newLesson.title} в ${newLesson.startTime}")
            }
        }
        
        return changes
    }
}
