package com.mguuschedule.repository

import android.content.Context
import android.util.Log
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

class ScheduleRepository(private val context: Context, private val dao: ScheduleDao) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    suspend fun fetchScheduleFromPortal(
        groupId: String = "000000311",
        groupName: String = "26М-УГКП21",
        startDate: String = "01.09.2026",
        endDate: String = "30.09.2026"
    ): List<LessonEntity> = withContext(Dispatchers.IO) {
        val encodedGroup = URLEncoder.encode(groupName, "UTF-8")
        val url = "https://portal.mguu.ru/student/scheduler2.php?groupid=$groupId&groupname=$encodedGroup&startDate=$startDate&endDate=$endDate#schedule"
        
        Log.d("PORTAL_PARSER", "Запрос к URL: $url")
        AppLogger.d("PORTAL_PARSER", "Запрос к URL: $url")
        
        try {
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
            val dayBlocks = doc.select("div.schedule")
            AppLogger.d("PORTAL_PARSER", "Найдено дней (div.schedule): ${dayBlocks.size}")

            val lessons = ScheduleParser.parse(doc.html())
            Log.d("PORTAL_PARSER", "Всего успешно спарсено пар: ${lessons.size}")
            AppLogger.d("PORTAL_PARSER", "Всего успешно спарсено пар: ${lessons.size}")
            
            // Сохраняем в базу
            if (lessons.isNotEmpty()) {
                dao.insertLessons(lessons)
                
                val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                prefs.edit().putString("last_sync_time", nowStr).apply()
                
                AppLogger.d("PORTAL_PARSER", "Обновлено расписание в БД (вставка/обновление)")
            }
            lessons
        } catch (e: Exception) {
            Log.e("PORTAL_PARSER", "Ошибка парсинга: ${e.message}", e)
            AppLogger.e("PORTAL_PARSER", "Ошибка парсинга: ${e.message}", e)
            emptyList()
        }
    }
    
    suspend fun fetchSchedule(
        groupId: String,
        groupName: String,
        daysCount: Int
    ): Result<List<LessonEntity>> = try {
        val today = LocalDate.now()
        
        // ВСЕГДА устанавливаем startDate на 1-е число месяца для корректного сравнения на сервере
        val effectiveStart = if (today.monthValue == 8) {
            LocalDate.of(2026, 9, 1) // Август переводим на 1 сентября 2026
        } else {
            today.withDayOfMonth(1)  // Всегда 1-е число текущего месяца
        }

        // Конец выборки — последний день следующего месяца (гарантированный охват семестра)
        val nextMonth = effectiveStart.plusMonths(1)
        val effectiveEnd = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth())

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
}
