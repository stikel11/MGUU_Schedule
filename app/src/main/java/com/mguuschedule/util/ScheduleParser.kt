package com.mguuschedule.util

import com.mguuschedule.model.Group
import com.mguuschedule.model.LessonEntity
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ScheduleParser {
    private val siteDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd

    fun parse(html: String): List<LessonEntity> {
        val doc = Jsoup.parse(html)
        val parsedLessons = mutableListOf<LessonEntity>()
        
        val dayBlocks = doc.select("div.schedule")

        for (day in dayBlocks) {
            val rawDate = day.selectFirst("h3")?.text()?.trim() ?: continue // "01.09.2026"
            // Конвертируем дату в единый ISO формат базы
            val normalizedDate = try {
                LocalDate.parse(rawDate, siteDateFormatter).format(isoFormatter)
            } catch (e: Exception) {
                rawDate
            }

            val items = day.select("div.ct-action").filter { it.selectFirst(".schedule-number") != null }
            for (item in items) {
                try {
                    val num = item.selectFirst(".schedule-number")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 1
                    val timeRaw = item.selectFirst(".schedule-data")?.text()?.trim().orEmpty()
                    val (startT, endT) = if ("-" in timeRaw) {
                        val p = timeRaw.split("-").map { it.trim() }
                        p[0] to p[1]
                    } else if (timeRaw.isNotEmpty()) {
                        timeRaw to ""
                    } else "00:00" to "00:00"

                    val subject = item.selectFirst(".schedule-type")?.text()?.replace(Regex("^\\s*\\|\\s*"), "")?.trim().orEmpty()
                    val teacher = item.selectFirst(".schedule-teacher")?.text()?.replace(Regex("^\\s*\\|\\s*"), "")?.trim().orEmpty()
                    
                    val rooms = item.select(".schedule-room")
                    val classroom = rooms.getOrNull(0)?.text()?.replace(Regex("^\\s*\\|\\s*"), "")?.trim().orEmpty()
                    val type = rooms.getOrNull(1)?.text()?.replace(Regex("^\\s*\\|\\s*"), "")?.trim().orEmpty()

                    if (subject.isNotEmpty()) {
                        parsedLessons.add(
                            LessonEntity(
                                date = normalizedDate, // "2026-09-01"
                                lessonNumber = num,
                                startTime = startT,
                                endTime = endT,
                                subject = subject,
                                teacher = teacher,
                                classroom = classroom,
                                type = type
                            )
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e("PORTAL_PARSER", "Ошибка парсинга занятия: ${e.message}", e)
                }
            }
        }
        return parsedLessons
    }

    fun parseGroups(html: String): List<Group> {
        val doc = Jsoup.parse(html)
        val groups = mutableListOf<Group>()
        
        val courseNames = doc.select("ul#author h6").map { it.text().trim() }
        val items = doc.select("div#testimonial div.item")
        
        items.forEachIndexed { index, item ->
            val courseName = courseNames.getOrNull(index) ?: "${index + 1} курс"
            val groupLinks = item.select("a.btn-group")
            
            groupLinks.forEach { element ->
                try {
                    val href = element.attr("href")
                    val groupId = href.substringAfter("groupid=").substringBefore("&")
                    groups.add(
                        Group(
                            id = groupId,
                            name = element.text().trim(),
                            course = courseName
                        )
                    )
                } catch (e: Exception) {
                    // Игнорируем битую ссылку
                }
            }
        }
        return groups
    }
}
