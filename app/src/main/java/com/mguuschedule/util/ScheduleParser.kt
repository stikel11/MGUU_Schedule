package com.mguuschedule.util

import com.mguuschedule.model.EducationLevel
import com.mguuschedule.model.Group
import com.mguuschedule.model.LessonEntity
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ScheduleParser {
    private val siteDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd
    private val emptyRoomRegex = Regex("^(?i)ауд\\.?\\s*$")

    /**
     * Парсинг JSON-ответа GET https://api.lk-upm.ru/api/schedule-by-id/{groupId}
     */
    fun parse(rawResponse: String): List<LessonEntity> {
        val trimmed = rawResponse.trim()
        if (!trimmed.startsWith("{")) return emptyList()

        val root = JSONObject(trimmed)
        if (!root.optBoolean("ok", false)) {
            val errorMsg = root.optString("error", "Unknown API error")
            throw IllegalStateException("API вернул ok=false: $errorMsg")
        }

        val count = root.optInt("count", 0)
        val version = root.optString("version", "—")
        val updatedAt = root.optString("updatedAt", "—")
        val stale = if (root.has("stale")) root.optBoolean("stale") else null
        
        AppLogger.d("API_PARSER", "Расписание JSON: ok=true, count=$count, stale=$stale, version=$version, updatedAt=$updatedAt")
        
        AppLogger.updateDiagnostics {
            it.copy(
                lastScheduleSource = "СЕТЬ (api.lk-upm.ru)",
                apiScheduleVersion = version,
                apiScheduleUpdatedAt = updatedAt,
                apiScheduleStale = stale,
                lastScheduleCount = count
            )
        }

        val lessonsArray = root.optJSONArray("lessons") ?: return emptyList()
        val parsedLessons = ArrayList<LessonEntity>(lessonsArray.length())

        for (i in 0 until lessonsArray.length()) {
            val item = lessonsArray.optJSONObject(i) ?: continue
            try {
                val rawDate = item.optString("date", "").trim()
                if (rawDate.isEmpty()) continue

                val normalizedDate = try {
                    LocalDate.parse(rawDate, siteDateFormatter).format(isoFormatter)
                } catch (e: Exception) {
                    rawDate
                }

                val subjectBase = item.optString("subject", "").trim()
                if (subjectBase.isEmpty()) continue

                val subgroup = item.optString("subgroup", "").trim()
                val subject = if (subgroup.isNotEmpty()) "$subjectBase ($subgroup)" else subjectBase

                val numStr = item.optString("number", "1").replace(Regex("[^0-9]"), "")
                val num = numStr.toIntOrNull() ?: 1

                val timeRaw = item.optString("time", "").trim()
                val (startT, endT) = parseAndNormalizeTimeRange(timeRaw)

                val teacher = item.optString("teacher", "").trim()
                val rawRoom = item.optString("room", "").trim()
                val classroom = if (emptyRoomRegex.matches(rawRoom)) "" else rawRoom
                val type = item.optString("type", "").trim()

                parsedLessons.add(
                    LessonEntity(
                        date = normalizedDate,
                        lessonNumber = num,
                        startTime = startT,
                        endTime = endT,
                        subject = subject,
                        teacher = teacher,
                        classroom = classroom,
                        type = type
                    )
                )
            } catch (e: Exception) {
                AppLogger.e("API_PARSER", "Ошибка разбора занятия #$i: ${e.message}", e)
            }
        }
        
        AppLogger.d("API_PARSER", "Успешно создано LessonEntity: ${parsedLessons.size} шт.")
        return parsedLessons
    }

    /**
     * Парсинг JSON-ответа GET https://api.lk-upm.ru/api/groups
     */
    fun parseGroupsJson(rawJson: String): List<Group> {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("{")) return emptyList()

        val root = JSONObject(trimmed)
        if (!root.optBoolean("ok", false)) return emptyList()

        val groupsArray = root.optJSONArray("groups") ?: return emptyList()
        val result = ArrayList<Group>(groupsArray.length())

        for (i in 0 until groupsArray.length()) {
            val obj = groupsArray.optJSONObject(i) ?: continue
            val id = obj.optString("id", "").trim()
            val name = obj.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty()) continue

            val courseInt = obj.optInt("course", 1)
            val courseName = "$courseInt курс"
            val levelStr = obj.optString("level", "").lowercase().trim()
            val level = when (levelStr) {
                "master", "postgrad" -> EducationLevel.MASTER
                "bachelor" -> EducationLevel.BACHELOR
                else -> if (name.contains("М-", ignoreCase = true) || name.contains("М_", ignoreCase = true)) {
                    EducationLevel.MASTER
                } else {
                    EducationLevel.BACHELOR
                }
            }

            result.add(
                Group(
                    id = id,
                    name = name,
                    course = courseName,
                    level = level
                )
            )
        }
        return result
    }

    // Оставляем старую сигнатуру для совместимости, перенаправляя на JSON-парсер
    fun parseGroups(htmlOrJson: String, level: EducationLevel = EducationLevel.BACHELOR): List<Group> {
        val all = parseGroupsJson(htmlOrJson)
        return if (all.isNotEmpty()) all.filter { it.level == level } else emptyList()
    }

    /**
     * Нормализует "8:30 - 10:00" -> ("08:30", "10:00"), чтобы LocalTime.parse никогда не падал
     */
    private fun parseAndNormalizeTimeRange(timeRaw: String): Pair<String, String> {
        if (timeRaw.isEmpty()) return "00:00" to "00:00"
        return if ("-" in timeRaw) {
            val parts = timeRaw.split("-").map { normalizeTimeToken(it.trim()) }
            (parts.getOrNull(0) ?: "00:00") to (parts.getOrNull(1) ?: "00:00")
        } else {
            normalizeTimeToken(timeRaw) to ""
        }
    }

    private fun normalizeTimeToken(token: String): String {
        val clean = token.trim()
        if (clean.isEmpty()) return "00:00"
        val colonIdx = clean.indexOf(':')
        if (colonIdx == 1 && clean.length == 4) {
            return "0$clean" // "8:30" -> "08:30"
        }
        return clean
    }
}
