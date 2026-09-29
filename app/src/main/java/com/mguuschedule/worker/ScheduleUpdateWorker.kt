package com.mguuschedule.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
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
import java.time.format.TextStyle
import java.util.Locale

class ScheduleUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val cachePrefs = context.getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
    
    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { src, _, _ ->
            JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE))
        })
        .registerTypeAdapter(LocalDate::class.java, JsonDeserializer<LocalDate> { json, _, _ ->
            LocalDate.parse(json.asString, DateTimeFormatter.ISO_LOCAL_DATE)
        })
        .registerTypeAdapter(LocalTime::class.java, JsonSerializer<LocalTime> { src, _, _ ->
            JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_TIME))
        })
        .registerTypeAdapter(LocalTime::class.java, JsonDeserializer<LocalTime> { json, _, _ ->
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

            // Защита от неполных/некорректных ответов сервера
            if (newLessons.isEmpty()) {
                AppLogger.w("WORKER", "Получен пустой ответ от сервера, пропускаем обновление и уведомления")
                return@withContext Result.success()
            }

            val oldLessonsMapped = getCachedLessons(groupId).mapNotNull { it.toLesson() }
            val newLessonsMapped = newLessons.mapNotNull { it.toLesson() }

            // Защита от резко усеченного ответа сервера (например, сбой парсинга половины страницы)
            if (oldLessonsMapped.size >= 10 && newLessonsMapped.size < oldLessonsMapped.size / 3) {
                AppLogger.w("WORKER", "Подозрительно малое количество пар (${newLessonsMapped.size} vs ${oldLessonsMapped.size}), отменяем сравнение")
                return@withContext Result.success()
            }

            // Отправка уведомлений об изменениях (только если был сохранен ранее непустой кэш)
            if (changesEnabled && oldLessonsMapped.isNotEmpty()) {
                val diffsByDate = findDetailedChanges(oldLessonsMapped, newLessonsMapped)
                
                val today = LocalDate.now()
                val tomorrow = today.plusDays(1)

                fun formatDate(date: LocalDate): String {
                    return when (date) {
                        today -> "Сегодня"
                        tomorrow -> "Завтра"
                        else -> {
                            @Suppress("DEPRECATION")
                            val locale = Locale("ru")
                            val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                                .replaceFirstChar { it.uppercase() }
                            val monthName = date.month.getDisplayName(TextStyle.SHORT, locale)
                            "$dayName, ${date.dayOfMonth} $monthName"
                        }
                    }
                }

                var index = 0
                diffsByDate.forEach { (date, messages) ->
                    val dateStr = formatDate(date)
                    val title = "Изменения в расписании: $dateStr"
                    val combinedMessage = messages.joinToString("\n")

                    NotificationHelper.showNotification(
                        applicationContext,
                        NotificationHelper.CHANNEL_SCHEDULE_CHANGES,
                        title,
                        combinedMessage,
                        2000 + index
                    )
                    index++
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

            // Обновляем кэш только после успешного завершения всех операций
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

    private fun findDetailedChanges(old: List<Lesson>, new: List<Lesson>): Map<LocalDate, List<String>> {
        val changes = mutableMapOf<LocalDate, MutableList<String>>()
        
        fun String.normalize(): String = this.replace("\u00A0", " ").replace(Regex("\\s+"), " ").trim()

        fun lessonKey(l: Lesson): String = "${l.date}_${l.number}_${l.startTime}"

        val oldMap = old.associateBy { lessonKey(it) }
        val newMap = new.associateBy { lessonKey(it) }
        
        val today = LocalDate.now()

        // Множество дат, присутствующих в новом расписании (для проверки отмены пар только в те дни, данные по которым загружены)
        val newDates = new.map { it.date }.toSet()

        // 1. Проверяем изменения и отмены пар
        old.forEach { oldLesson ->
            if (oldLesson.date >= today) {
                val key = lessonKey(oldLesson)
                val newLesson = newMap[key]
                
                if (newLesson == null) {
                    // Если день есть в загруженных данных, но конкретная пара исчезла — она отменена
                    if (newDates.contains(oldLesson.date)) {
                        changes.getOrPut(oldLesson.date) { mutableListOf() }.add("Отменена: ${oldLesson.title.normalize()}")
                    }
                } else {
                    // Пара существует, проверяем реальные изменения аудитории или преподавателя
                    val oldRoom = oldLesson.room.normalize()
                    val newRoom = newLesson.room.normalize()
                    if (oldRoom != newRoom && oldRoom.isNotEmpty() && newRoom.isNotEmpty()) {
                        changes.getOrPut(oldLesson.date) { mutableListOf() }.add("${oldLesson.title.normalize()} перенесен в Ауд. $newRoom (была $oldRoom)")
                    }

                    val oldTeacher = oldLesson.teacher.normalize()
                    val newTeacher = newLesson.teacher.normalize()
                    if (oldTeacher != newTeacher && oldTeacher.isNotEmpty() && newTeacher.isNotEmpty()) {
                        changes.getOrPut(oldLesson.date) { mutableListOf() }.add("${oldLesson.title.normalize()}: замена преподавателя на $newTeacher")
                    }
                }
            }
        }

        // 2. Проверяем новые добавленные пары
        new.forEach { newLesson ->
            if (newLesson.date >= today) {
                val key = lessonKey(newLesson)
                if (!oldMap.containsKey(key)) {
                    changes.getOrPut(newLesson.date) { mutableListOf() }.add("Новая пара ${newLesson.title.normalize()} в ${newLesson.startTime}")
                }
            }
        }
        
        return changes
    }
}
