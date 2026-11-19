package com.mguuschedule.util

import com.mguuschedule.model.EducationLevel
import com.mguuschedule.model.Group
import com.mguuschedule.model.LessonEntity
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ScheduleParser {
    private val siteDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd
    private val syncTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    private val emptyRoomRegex = Regex("^(?i)ауд\\.?\\s*$")
    private val nonDigitRegex = Regex("[^0-9]")
    private val timeRangeSplitRegex = Regex("\\s*[-–—]\\s*")

    /**
     * Формирует диапазон дат текущего учебного года в формате dd.MM.yyyy для API schedule.mguu.ru.
     * С августа по декабрь — с 01.08.YYYY по 31.08.(YYYY+1), с января по июль — с 01.09.(YYYY-1) по 31.08.YYYY.
     */
    fun buildAcademicYearDateRange(referenceDate: LocalDate = LocalDate.now()): Pair<String, String> {
        val year = referenceDate.year
        val (start, end) = if (referenceDate.monthValue >= 8) {
            LocalDate.of(year, 8, 1) to LocalDate.of(year + 1, 8, 31)
        } else {
            LocalDate.of(year - 1, 9, 1) to LocalDate.of(year, 8, 31)
        }
        return start.format(siteDateFormatter) to end.format(siteDateFormatter)
    }

    /**
     * Парсинг JSON-ответа с расписанием.
     * Основной формат: GET https://schedule.mguu.ru/api/v1/schedule/groups/{groupCode}/lessons/
     * (содержит массив "days" -> { "date", "lessons": [{ "couple", "time", "discipline", "teacher", "auditorium", "load", "subgroup" }] }).
     * Также сохранена обратная совместимость с плоским форматом { "ok": true, "lessons": [...] } (используется для расписания аудитории).
     */
    fun parse(rawResponse: String): List<LessonEntity> {
        val trimmed = rawResponse.trim()
        if (!trimmed.startsWith("{")) return emptyList()

        val root = JSONObject(trimmed)

        return when {
            root.has("days") -> parseScheduleMguuJson(root)
            root.has("lessons") -> parseLegacyFlatJson(root)
            else -> emptyList()
        }
    }

    private fun parseScheduleMguuJson(root: JSONObject): List<LessonEntity> {
        val startDate = root.optCleanString("start_date")
        val endDate = root.optCleanString("end_date")
        val daysArray = root.optJSONArray("days") ?: return emptyList()

        val parsedLessons = ArrayList<LessonEntity>(daysArray.length() * 4)

        for (dIdx in 0 until daysArray.length()) {
            val dayObj = daysArray.optJSONObject(dIdx) ?: continue
            val rawDate = dayObj.optCleanString("date")
            if (rawDate.isEmpty()) continue

            val normalizedDate = normalizeDate(rawDate)
            val lessonsArray = dayObj.optJSONArray("lessons") ?: continue

            for (lIdx in 0 until lessonsArray.length()) {
                val item = lessonsArray.optJSONObject(lIdx) ?: continue
                try {
                    val discipline = item.optCleanString("discipline")
                    if (discipline.isEmpty()) continue

                    val subgroup = item.optCleanString("subgroup")
                    val subject = if (subgroup.isNotEmpty()) "$discipline ($subgroup)" else discipline

                    val coupleStr = item.optCleanString("couple").replace(nonDigitRegex, "")
                    val lessonNumber = coupleStr.toIntOrNull() ?: (lIdx + 1)

                    val timeRaw = item.optCleanString("time")
                    val (startT, endT) = parseAndNormalizeTimeRange(timeRaw)

                    val teacher = item.optCleanString("teacher")
                    val rawRoom = item.optCleanString("auditorium")
                    val classroom = if (emptyRoomRegex.matches(rawRoom)) "" else rawRoom
                    val type = item.optCleanString("load")

                    parsedLessons.add(
                        LessonEntity(
                            date = normalizedDate,
                            lessonNumber = lessonNumber,
                            startTime = startT,
                            endTime = endT,
                            subject = subject,
                            teacher = teacher,
                            classroom = classroom,
                            type = type
                        )
                    )
                } catch (e: Exception) {
                    AppLogger.e("API_PARSER", "Ошибка разбора занятия ($rawDate #$lIdx): ${e.message}", e)
                }
            }
        }

        val periodLabel = if (startDate.isNotEmpty() && endDate.isNotEmpty()) "$startDate–$endDate" else "schedule.mguu.ru"
        val nowStr = LocalDateTime.now().format(syncTimeFormatter)

        AppLogger.d("API_PARSER", "Расписание schedule.mguu.ru: дней=${daysArray.length()}, пар=${parsedLessons.size}, период=$periodLabel")
        AppLogger.updateDiagnostics {
            it.copy(
                lastScheduleSource = "СЕТЬ (schedule.mguu.ru)",
                lastScheduleSyncTime = nowStr,
                apiScheduleVersion = periodLabel,
                apiScheduleUpdatedAt = nowStr,
                apiScheduleStale = false,
                lastScheduleCount = parsedLessons.size
            )
        }

        return parsedLessons
    }

    private fun parseLegacyFlatJson(root: JSONObject): List<LessonEntity> {
        if (root.has("ok") && !root.optBoolean("ok", false)) {
            val errorMsg = root.optString("error", "Unknown API error")
            throw IllegalStateException("API вернул ok=false: $errorMsg")
        }

        val lessonsArray = root.optJSONArray("lessons") ?: return emptyList()
        val parsedLessons = ArrayList<LessonEntity>(lessonsArray.length())

        for (i in 0 until lessonsArray.length()) {
            val item = lessonsArray.optJSONObject(i) ?: continue
            try {
                val rawDate = item.optCleanString("date")
                if (rawDate.isEmpty()) continue

                val normalizedDate = normalizeDate(rawDate)

                val subjectBase = item.optCleanString("subject")
                if (subjectBase.isEmpty()) continue

                val subgroup = item.optCleanString("subgroup")
                val subject = if (subgroup.isNotEmpty()) "$subjectBase ($subgroup)" else subjectBase

                val numStr = item.optCleanString("number").replace(nonDigitRegex, "")
                val num = numStr.toIntOrNull() ?: 1

                val timeRaw = item.optCleanString("time")
                val (startT, endT) = parseAndNormalizeTimeRange(timeRaw)

                val teacher = item.optCleanString("teacher")
                val rawRoom = item.optCleanString("room")
                val classroom = if (emptyRoomRegex.matches(rawRoom)) "" else rawRoom
                val type = item.optCleanString("type")

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

        AppLogger.d("API_PARSER", "Успешно создано LessonEntity (legacy): ${parsedLessons.size} шт.")
        return parsedLessons
    }

    /**
     * Парсинг JSON-ответа GET https://schedule.mguu.ru/api/v1/schedule/levels/{level}/groups/
     */
    fun parseGroupsForLevel(
        rawJson: String,
        fallbackLevel: EducationLevel = EducationLevel.BACHELOR
    ): List<Group> {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("{")) return emptyList()

        val root = JSONObject(trimmed)
        val levelCode = root.optJSONObject("level")?.optCleanString("code")?.lowercase().orEmpty()
        val resolvedLevel = when (levelCode) {
            "bak", "bachelor" -> EducationLevel.BACHELOR
            "mag", "asp", "master", "postgrad" -> EducationLevel.MASTER
            else -> fallbackLevel
        }

        val coursesArray = root.optJSONArray("courses") ?: return emptyList()
        val result = ArrayList<Group>()

        for (cIdx in 0 until coursesArray.length()) {
            val courseObj = coursesArray.optJSONObject(cIdx) ?: continue
            val courseInt = courseObj.optInt("course", 1)
            val courseName = "$courseInt курс"
            val groupsArray = courseObj.optJSONArray("groups") ?: continue

            for (gIdx in 0 until groupsArray.length()) {
                val groupObj = groupsArray.optJSONObject(gIdx) ?: continue
                val id = groupObj.optCleanString("code")
                val name = groupObj.optCleanString("name")
                if (id.isEmpty() || name.isEmpty()) continue

                result.add(
                    Group(
                        id = id,
                        name = name,
                        course = courseName,
                        level = resolvedLevel
                    )
                )
            }
        }

        return result
    }

    /**
     * Универсальный парсинг JSON со списком групп (поддерживает новый формат schedule.mguu.ru и legacy).
     */
    fun parseGroupsJson(rawJson: String): List<Group> {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("{")) return emptyList()

        val root = JSONObject(trimmed)
        if (root.has("courses")) {
            return parseGroupsForLevel(trimmed)
        }

        if (root.has("ok") && !root.optBoolean("ok", false)) return emptyList()
        val groupsArray = root.optJSONArray("groups") ?: return emptyList()
        val result = ArrayList<Group>(groupsArray.length())

        for (i in 0 until groupsArray.length()) {
            val obj = groupsArray.optJSONObject(i) ?: continue
            val id = obj.optCleanString("id").ifEmpty { obj.optCleanString("code") }
            val name = obj.optCleanString("name")
            if (id.isEmpty() || name.isEmpty()) continue

            val courseInt = obj.optInt("course", 1)
            val courseName = "$courseInt курс"
            val levelStr = obj.optCleanString("level").lowercase()
            val level = when (levelStr) {
                "mag", "asp", "master", "postgrad" -> EducationLevel.MASTER
                "bak", "bachelor" -> EducationLevel.BACHELOR
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

    fun parseGroups(htmlOrJson: String, level: EducationLevel = EducationLevel.BACHELOR): List<Group> {
        val all = parseGroupsJson(htmlOrJson)
        return if (all.isNotEmpty()) all.filter { it.level == level } else emptyList()
    }

    private fun normalizeDate(rawDate: String): String = try {
        LocalDate.parse(rawDate, siteDateFormatter).format(isoFormatter)
    } catch (_: Exception) {
        rawDate
    }

    private fun JSONObject.optCleanString(key: String): String {
        if (isNull(key)) return ""
        return optString(key, "").trim()
    }

    /**
     * Нормализует "8:30 - 10:00" -> ("08:30", "10:00"), чтобы LocalTime.parse никогда не падал.
     */
    private fun parseAndNormalizeTimeRange(timeRaw: String): Pair<String, String> {
        if (timeRaw.isEmpty()) return "00:00" to "00:00"
        val parts = timeRaw.split(timeRangeSplitRegex)
        return if (parts.size >= 2) {
            normalizeTimeToken(parts[0]) to normalizeTimeToken(parts[1])
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
