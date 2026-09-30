package com.mguuschedule.repository

import android.content.Context
import android.util.Log
import com.mguuschedule.model.EducationLevel
import com.mguuschedule.model.LessonEntity
import com.mguuschedule.util.AppLogger
import com.mguuschedule.util.ScheduleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ScheduleRepository(private val context: Context, private val database: AppDatabase) {
    private val dao: ScheduleDao = database.scheduleDao()
    private val addonsDao: LessonAddonsDao = database.lessonAddonsDao()
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    private fun fetchAndParsePortalUrl(url: String): List<LessonEntity> {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL").apply {
                init(null, trustAllCerts, SecureRandom())
            }

            val doc = Jsoup.connect(url)
                .sslSocketFactory(sslContext.socketFactory)
                .userAgent("Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36")
                .referrer("https://portal.mguu.ru/")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .timeout(20_000)
                .get()

            AppLogger.d("PORTAL_PARSER", "Title страницы: ${doc.title()}")
            val lessons = ScheduleParser.parse(doc.html())
            Log.d("PORTAL_PARSER", "Успешно спарсено пар с $url: ${lessons.size}")
            AppLogger.d("PORTAL_PARSER", "Успешно спарсено пар с $url: ${lessons.size}")
            lessons
        } catch (e: Exception) {
            Log.e("PORTAL_PARSER", "Ошибка запроса к $url: ${e.message}", e)
            AppLogger.e("PORTAL_PARSER", "Ошибка запроса к $url: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchScheduleFromPortal(
        groupId: String = "000000311",
        groupName: String = "26М-УГКП21",
        startDate: String = "01.09.2026",
        endDate: String = "30.09.2026"
    ): List<LessonEntity> = withContext(Dispatchers.IO) {
        val encodedGroup = URLEncoder.encode(groupName, "UTF-8")
        val levelStr = prefs.getString("selected_group_level", "BACHELOR")
        val level = runCatching { EducationLevel.valueOf(levelStr ?: "BACHELOR") }.getOrDefault(
            EducationLevel.BACHELOR)

        val primaryPage = level.schedulerPage
        val primaryUrl = "https://portal.mguu.ru/student/$primaryPage?groupid=$groupId&groupname=$encodedGroup&startDate=$startDate&endDate=$endDate#schedule"

        Log.d("PORTAL_PARSER", "Запрос к первичному URL ($primaryPage): $primaryUrl")
        AppLogger.d("PORTAL_PARSER", "Запрос к первичному URL ($primaryPage): $primaryUrl")

        var lessons = fetchAndParsePortalUrl(primaryUrl)

        if (lessons.isEmpty()) {
            val fallbackPage = if (level == EducationLevel.BACHELOR) "scheduler2.php" else "scheduler1.php"
            val fallbackUrl = "https://portal.mguu.ru/student/$fallbackPage?groupid=$groupId&groupname=$encodedGroup&startDate=$startDate&endDate=$endDate#schedule"
            Log.d("PORTAL_PARSER", "Фолбэк запрос к URL ($fallbackPage): $fallbackUrl")
            AppLogger.d("PORTAL_PARSER", "Фолбэк запрос к URL ($fallbackPage): $fallbackUrl")
            lessons = fetchAndParsePortalUrl(fallbackUrl)
        }

        // Сохраняем в базу
        if (lessons.isNotEmpty()) {
            dao.clearSchedule()
            dao.insertLessons(lessons)

            val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
            prefs.edit().putString("last_sync_time", nowStr).apply()

            AppLogger.d("PORTAL_PARSER", "Обновлено расписание в БД (очистка + вставка)")
        }
        lessons
    }
    
    suspend fun fetchSchedule(
        groupId: String,
        groupName: String,
        daysCount: Int
    ): Result<List<LessonEntity>> = try {
        val today = LocalDate.now()
        
        // ВСЕГДА устанавливаем startDate на 1-е число месяца для корректного сравнения на сервере
        val targetStartDate = today.minusDays(daysCount.toLong())
        val effectiveStart = if (targetStartDate.monthValue == 8) {
            LocalDate.of(2026, 9, 1) // Август переводим на 1 сентября 2026
        } else {
            targetStartDate.withDayOfMonth(1)  // Всегда 1-е число текущего месяца
        }

        // Конец выборки — с учетом daysCount и до конца месяца
        val targetEndDate = today.plusDays(daysCount.toLong())
        val effectiveEnd = targetEndDate.withDayOfMonth(targetEndDate.lengthOfMonth())

        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val startDateStr = effectiveStart.format(formatter)
        val endDateStr = effectiveEnd.format(formatter)
        
        val lessons = fetchScheduleFromPortal(groupId, groupName, startDateStr, endDateStr)
        Result.success(lessons)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun clearDatabase() {
        dao.clearSchedule()
    }

    suspend fun getLessonsCount(): Int {
        return dao.getLessonsCount()
    }

    suspend fun getUpcomingLessons(startDate: String = LocalDate.now().toString()): List<LessonEntity> {
        return dao.getUpcomingLessons(startDate)
    }

    fun getTotalLessonsCountFlow() = dao.getTotalLessonsCountFlow()
    
    suspend fun getEarliestDate() = dao.getEarliestDate()
    
    suspend fun getLatestDate() = dao.getLatestDate()

    fun getLessonsByDateFlow(date: String) = dao.getLessonsForDate(date)

    fun getAllLessonsFlow() = dao.getAllLessonsFlow()

    // Lesson Notes
    fun getNoteFlow(lessonKey: String) = addonsDao.getNoteFlow(lessonKey)
    suspend fun saveNote(lessonKey: String, text: String) {
        if (text.isBlank()) {
            addonsDao.deleteNote(lessonKey)
        } else {
            addonsDao.upsertNote(
                LessonNoteEntity(
                    lessonKey = lessonKey,
                    text = text.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
    suspend fun deleteNote(lessonKey: String) = addonsDao.deleteNote(lessonKey)

    // Lesson Tasks
    fun getTasksFlow(lessonKey: String) = addonsDao.getTasksFlow(lessonKey)
    fun getAllTasksFlow() = addonsDao.getAllTasksFlow()
    suspend fun addTask(lessonKey: String, title: String, deadlineEpoch: Long? = null) {
        if (title.isNotBlank()) {
            addonsDao.insertTask(
                LessonTaskEntity(
                    lessonKey = lessonKey,
                    title = title.trim(),
                    deadlineEpoch = deadlineEpoch,
                    isCompleted = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }
    suspend fun toggleTaskCompleted(taskId: Long, isCompleted: Boolean) = addonsDao.setTaskCompleted(taskId, isCompleted)
    suspend fun deleteTask(taskId: Long) = addonsDao.deleteTask(taskId)

    // Lesson Materials
    fun getMaterialsFlow(lessonKey: String) = addonsDao.getMaterialsFlow(lessonKey)
    suspend fun addMaterial(
        lessonKey: String,
        title: String,
        type: String,
        uriOrUrl: String,
        fileName: String? = null,
        mimeType: String? = null,
        sizeBytes: Long? = null
    ) {
        addonsDao.insertMaterial(
            LessonMaterialEntity(
                lessonKey = lessonKey,
                title = title.trim(),
                type = type,
                uriOrUrl = uriOrUrl,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                createdAt = System.currentTimeMillis()
            )
        )
    }
    suspend fun deleteMaterial(materialId: Long) = addonsDao.deleteMaterial(materialId)

    // Active Addons Overview
    fun getActiveAddonLessonKeysFlow() = addonsDao.getActiveAddonLessonKeysFlow()
}
