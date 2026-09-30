package com.mguuschedule.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mguuschedule.model.CampusTeacher
import com.mguuschedule.model.ControlPoint
import com.mguuschedule.model.Lesson
import com.mguuschedule.model.SubjectScore
import com.mguuschedule.repository.RatingEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

sealed class SearchResultItem {
    data class TeacherCard(
        val teacherName: String,
        val campusProfile: CampusTeacher? = null
    ) : SearchResultItem()

    data class RoomCard(
        val room: String,
        val floorNumber: String,
        val floorImageResId: Int?
    ) : SearchResultItem()

    data class DateHeader(
        val date: LocalDate,
        val dateText: String,
        val dayOfWeekText: String
    ) : SearchResultItem()

    data class ControlPointHeader(
        val title: String = "Ближайшие контрольные точки"
    ) : SearchResultItem()

    data class ControlPointCard(
        val uniqueId: String,
        val subjectName: String,
        val pointName: String,
        val dateText: String,
        val scoreText: String
    ) : SearchResultItem()

    data class SectionHeader(
        val title: String
    ) : SearchResultItem()

    data class LessonCard(
        val lesson: Lesson
    ) : SearchResultItem()
}

object SearchEngine {

    private val localeRu = Locale("ru")

    fun normalize(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace("ё", "е")
            .replace(Regex("[^a-zа-я0-9\\s./\\-]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun normalizeSubjectName(title: String): String {
        return title.lowercase(Locale.ROOT)
            .replace("ё", "е")
            .replace(Regex(",\\s*(лекция|семинар|практика|лаб|лабораторная|экзамен|зачет).*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^a-zа-я0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun parseControlPointDate(dateStr: String, fallbackYear: Int = LocalDate.now().year): LocalDate? {
        if (dateStr.isBlank() || dateStr == "—") return null
        val clean = dateStr.trim().replace('/', '.').replace('-', '.')
        val parts = clean.split('.')
        if (parts.size >= 2) {
            val day = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val year = if (parts.size >= 3) {
                val y = parts[2].toIntOrNull() ?: fallbackYear
                if (y < 100) 2000 + y else y
            } else {
                fallbackYear
            }
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        return null
    }

    fun computeLessonControlPointsMap(
        lessons: List<Lesson>,
        ratingEntity: RatingEntity
    ): Map<String, List<ControlPoint>> {
        val gson = Gson()
        val subjectsType = object : TypeToken<List<SubjectScore>>() {}.type
        val subjects: List<SubjectScore> = runCatching {
            gson.fromJson<List<SubjectScore>>(ratingEntity.subjectsJson, subjectsType)
        }.getOrDefault(emptyList())

        val pointsMapType = object : TypeToken<Map<String, List<ControlPoint>>>() {}.type
        val controlPointsMap: Map<String, List<ControlPoint>> = runCatching {
            gson.fromJson<Map<String, List<ControlPoint>>>(ratingEntity.controlPointsJson, pointsMapType)
        }.getOrDefault(emptyMap())

        if (subjects.isEmpty() || controlPointsMap.isEmpty()) return emptyMap()

        data class ParsedCP(
            val subjectTitle: String,
            val normalizedSubject: String,
            val point: ControlPoint,
            val date: LocalDate
        )

        val allParsedCPs = mutableListOf<ParsedCP>()
        
        // Определяем базовый год из yearId (например "2026_2027")
        val baseYear = Regex("^(\\d{4})").find(ratingEntity.yearId)?.groupValues?.get(1)?.toIntOrNull() ?: LocalDate.now().year

        subjects.forEach { subject ->
            val points = controlPointsMap[subject.detailUrl] ?: emptyList()
            val normSubject = normalizeSubjectName(subject.title)
            points.forEach { cp ->
                // Если дата КТ имеет месяц от 1 до 8 (весенний семестр), год скорее всего baseYear + 1
                // Для осеннего семестра (9-12) год = baseYear. Это эвристика для fallbackYear.
                val monthStr = cp.date.split('.', '/').getOrNull(1)
                val isSpringMonth = (monthStr?.toIntOrNull() ?: 9) < 9
                val fallbackYear = if (isSpringMonth && ratingEntity.yearId.contains("_")) baseYear + 1 else baseYear
                
                val parsedDate = parseControlPointDate(cp.date, fallbackYear)
                if (parsedDate != null) {
                    allParsedCPs.add(ParsedCP(subject.title, normSubject, cp, parsedDate))
                }
            }
        }

        if (allParsedCPs.isEmpty()) return emptyMap()

        val lessonsGrouped = lessons.groupBy { lesson ->
            Pair(normalizeSubjectName(lesson.title), lesson.date)
        }

        val resultMap = mutableMapOf<String, MutableList<ControlPoint>>()

        allParsedCPs.forEach { pcp ->
            val matchingLessons = lessonsGrouped[Pair(pcp.normalizedSubject, pcp.date)] ?: emptyList()
            if (matchingLessons.isEmpty()) return@forEach

            val cpNameLower = pcp.point.pointName.lowercase(Locale.ROOT)
            
            val typeMatches = matchingLessons.filter { lesson ->
                val typeLower = lesson.type.lowercase(Locale.ROOT)
                when {
                    typeLower.contains("лекц") && cpNameLower.contains("лекц") -> true
                    typeLower.contains("семин") && cpNameLower.contains("семин") -> true
                    typeLower.contains("практ") && cpNameLower.contains("практ") -> true
                    typeLower.contains("лаб") && cpNameLower.contains("лаб") -> true
                    else -> false
                }
            }

            if (typeMatches.size == 1) {
                val lesson = typeMatches[0]
                val lessonKey = "${lesson.date}_${lesson.number}_${lesson.startTime}"
                resultMap.getOrPut(lessonKey) { mutableListOf() }.add(pcp.point)
            } else if (typeMatches.isEmpty() && matchingLessons.size == 1) {
                val lesson = matchingLessons[0]
                val typeLower = lesson.type.lowercase(Locale.ROOT)
                if (!typeLower.contains("лекц")) {
                    val lessonKey = "${lesson.date}_${lesson.number}_${lesson.startTime}"
                    resultMap.getOrPut(lessonKey) { mutableListOf() }.add(pcp.point)
                }
            }
        }

        return resultMap
    }

    fun deduplicateLessons(lessons: List<Lesson>): List<Lesson> {
        return lessons.distinctBy {
            it.title.trim() + "_" + it.teacher.trim() + "_" + it.room.trim() + "_" + it.date.toString() + "_" + it.startTime.toString()
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = temp
            }
        }
        return dp[s2.length]
    }

    fun isFuzzyMatch(token: String, targetWord: String): Boolean {
        if (targetWord.contains(token)) return true
        if (token.length < 4) return false
        val maxDist = if (token.length <= 5) 1 else 2
        if (targetWord.length >= token.length) {
            for (i in 0..(targetWord.length - token.length)) {
                val sub = targetWord.substring(i, minOf(i + token.length, targetWord.length))
                if (levenshteinDistance(token, sub) <= maxDist) return true
            }
        } else {
            if (levenshteinDistance(token, targetWord) <= maxDist) return true
        }
        return false
    }

    fun parseDateFromQuery(tokens: List<String>): Pair<LocalDate?, List<String>> {
        val today = LocalDate.now()
        val remaining = tokens.toMutableList()

        for (token in tokens) {
            when (token) {
                "сегодня" -> {
                    remaining.remove(token)
                    return Pair(today, remaining)
                }
                "завтра" -> {
                    remaining.remove(token)
                    return Pair(today.plusDays(1), remaining)
                }
                "вчера" -> {
                    remaining.remove(token)
                    return Pair(today.minusDays(1), remaining)
                }
            }
        }

        val dateRegex = Regex("^(\\d{1,2})[./\\-](\\d{1,2})(?:[./\\-](\\d{2,4}))?$")
        for (token in tokens) {
            val match = dateRegex.find(token)
            if (match != null) {
                val day = match.groupValues[1].toIntOrNull() ?: continue
                val month = match.groupValues[2].toIntOrNull() ?: continue
                val yearStr = match.groupValues[3]
                val year = if (yearStr.isNotBlank()) {
                    val y = yearStr.toIntOrNull() ?: today.year
                    if (y < 100) 2000 + y else y
                } else {
                    today.year
                }
                runCatching {
                    val date = LocalDate.of(year, month, day)
                    remaining.remove(token)
                    return Pair(date, remaining)
                }
            }
        }

        val monthMap = mapOf(
            "января" to 1, "январь" to 1,
            "февраля" to 2, "февраль" to 2,
            "марта" to 3, "март" to 3,
            "апреля" to 4, "апрель" to 4,
            "мая" to 5, "май" to 5,
            "июня" to 6, "июнь" to 6,
            "июля" to 7, "июль" to 7,
            "августа" to 8, "август" to 8,
            "сентября" to 9, "сентябрь" to 9,
            "октября" to 10, "октябрь" to 10,
            "ноября" to 11, "ноябрь" to 11,
            "декабря" to 12, "декабрь" to 12
        )

        for (i in 0 until tokens.size - 1) {
            val day = tokens[i].toIntOrNull()
            val month = monthMap[tokens[i + 1]]
            if (day != null && month != null && day in 1..31) {
                var year = today.year
                var tokensToRemove = listOf(tokens[i], tokens[i + 1])
                if (i + 2 < tokens.size) {
                    val possibleYear = tokens[i + 2].toIntOrNull()
                    if (possibleYear != null && possibleYear in 2024..2030) {
                        year = possibleYear
                        tokensToRemove = listOf(tokens[i], tokens[i + 1], tokens[i + 2])
                    }
                }
                runCatching {
                    val date = LocalDate.of(year, month, day)
                    remaining.removeAll(tokensToRemove)
                    return Pair(date, remaining)
                }
            }
        }

        return Pair(null, tokens)
    }

    fun parseRoomFromQuery(tokens: List<String>): Pair<String?, List<String>> {
        val roomRegex = Regex("^(?:ауд|аудитория)?\\.?\\s*([1-5]\\d{2}[а-я]?)$", RegexOption.IGNORE_CASE)
        val simpleNumRegex = Regex("^([1-5]\\d{2}[а-я]?)$")

        val remaining = tokens.toMutableList()
        for (i in tokens.indices) {
            val token = tokens[i]
            if (token in listOf("ауд", "аудитория", "кабинет") && i + 1 < tokens.size) {
                val nextToken = tokens[i + 1]
                val match = simpleNumRegex.find(nextToken)
                if (match != null) {
                    remaining.removeAt(i + 1)
                    remaining.removeAt(i)
                    return Pair(match.groupValues[1], remaining)
                }
            }
            val match = roomRegex.find(token)
            if (match != null) {
                remaining.remove(token)
                return Pair(match.groupValues[1], remaining)
            }
        }
        return Pair(null, tokens)
    }

    fun parseControlPointIntent(tokens: List<String>): Pair<Boolean, List<String>> {
        val cpKeywords = setOf("кт", "контрольная", "точка", "контрольные", "модуль")
        val remaining = tokens.toMutableList()
        var isCp = false
        for (token in tokens) {
            if (token in cpKeywords) {
                isCp = true
                remaining.remove(token)
            }
        }
        return Pair(isCp, remaining)
    }

    fun findTeacherMatch(tokens: List<String>, allLessons: List<Lesson>): Pair<String?, List<String>> {
        if (tokens.isEmpty()) return Pair(null, tokens)
        val uniqueTeachers = allLessons.map { it.teacher }.filter { it.isNotBlank() && it != "—" }.distinct()
        val remaining = tokens.toMutableList()

        for (teacher in uniqueTeachers) {
            val normTeacher = normalize(teacher)
            val teacherWords = normTeacher.split(" ").filter { it.isNotBlank() }
            val surname = teacherWords.firstOrNull() ?: continue

            for (token in tokens) {
                if (token.length >= 3 && (surname.contains(token) || isFuzzyMatch(token, surname))) {
                    remaining.remove(token)
                    for (t in tokens) {
                        if (t != token && teacherWords.any { it.startsWith(t) }) {
                            remaining.remove(t)
                        }
                    }
                    return Pair(teacher, remaining)
                }
            }
        }
        return Pair(null, tokens)
    }

    fun sortLessonsByRelevanceAndDate(
        lessons: List<Lesson>,
        queryTokens: List<String>
    ): List<Lesson> {
        val today = LocalDate.now()
        val currentTime = LocalTime.now()

        return lessons.sortedWith(
            compareByDescending<Lesson> { lesson ->
                var score = 0
                val normTitle = normalize(lesson.title)
                val normTeacher = normalize(lesson.teacher)
                val normRoom = normalize(lesson.room)
                val normType = normalize(lesson.type)

                for (token in queryTokens) {
                    when {
                        normTitle == token -> score += 100
                        normTitle.startsWith(token) -> score += 80
                        normTitle.contains(token) -> score += 60
                        normTeacher.contains(token) -> score += 40
                        normRoom.contains(token) -> score += 30
                        normType.contains(token) -> score += 20
                        isFuzzyMatch(token, normTitle) -> score += 20
                    }
                }
                score
            }.thenBy { lesson ->
                when {
                    lesson.date.isEqual(today) && lesson.endTime.isAfter(currentTime) -> 0L
                    lesson.date.isAfter(today) -> ChronoUnit.DAYS.between(today, lesson.date)
                    else -> 10000L + ChronoUnit.DAYS.between(lesson.date, today)
                }
            }.thenBy { it.startTime }
        )
    }

    fun formatDateHeader(date: LocalDate): Pair<String, String> {
        val dateText = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL_STANDALONE, localeRu)}"
        val dayOfWeekText = date.dayOfWeek.getDisplayName(TextStyle.FULL, localeRu)
            .replaceFirstChar { it.uppercase() }
        return Pair(dateText, dayOfWeekText)
    }
}
