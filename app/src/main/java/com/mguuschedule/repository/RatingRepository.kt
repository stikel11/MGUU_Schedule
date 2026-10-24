package com.mguuschedule.repository

import com.mguuschedule.model.AcademicPeriod
import com.mguuschedule.model.ControlPoint
import com.mguuschedule.model.EduGroup
import com.mguuschedule.model.GroupRatingPage
import com.mguuschedule.model.StudentRating
import com.mguuschedule.model.SubjectScore
import com.mguuschedule.util.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.LocalDate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.roundToInt

class RatingRepository(
    private val ratingDao: RatingDao
) {

    companion object {
        private const val API_BASE = "https://api.lk-upm.ru"
        private const val USER_AGENT = "Mozilla/5.0 (Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0"
        private const val TIMEOUT_MS = 12_000
        private const val DEFAULT_YEAR_ID = "000000033"
        private const val DEFAULT_SEM_ID = "0"
    }

    suspend fun getCachedRating(groupId: String, zachetka: String = "", yearId: String = "", semId: String = ""): RatingEntity? {
        return ratingDao.getRatingCache(groupId, zachetka, yearId, semId)
            ?: ratingDao.getLatestRatingCache()
    }

    suspend fun getCachedOrLatestRating(groupId: String, zachetka: String, yearId: String?, semId: String?): RatingEntity? {
        if (yearId.isNullOrBlank() || semId.isNullOrBlank()) {
            return ratingDao.getLatestRatingCacheForStudent(groupId, zachetka)
        }
        return ratingDao.getRatingCache(groupId, zachetka, yearId, semId)
    }

    suspend fun saveCachedRating(entity: RatingEntity) {
        ratingDao.saveRatingCache(entity)
    }

    /**
     * Формирует внутренний URI запроса группы с сохранением groupId, yearId и semId.
     */
    fun buildGroupUrl(groupId: String, groupName: String, yearId: String? = null, semId: String? = null): String {
        val cleanGroupId = groupId.trim()
        val yearParam = if (!yearId.isNullOrBlank()) "&year=${yearId.trim()}" else ""
        val semParam = if (!semId.isNullOrBlank()) "&sem=${semId.trim()}" else ""
        return "$API_BASE/api/rating/students-by-group-id/$cleanGroupId?groupname=${URLEncoder.encode(groupName, "UTF-8")}$yearParam$semParam"
    }

    /**
     * Загружает каталог групп из GET /api/groups
     */
    suspend fun parseMasterGroupsCatalogue(url: String = ""): List<EduGroup> = withContext(Dispatchers.IO) {
        runCatching {
            val rawJson = fetchJson("$API_BASE/api/groups")
            val root = JSONObject(rawJson)
            if (!root.optBoolean("ok", false)) return@withContext emptyList()

            val groupsArr = root.optJSONArray("groups") ?: return@withContext emptyList()
            val result = ArrayList<EduGroup>(groupsArr.length())
            for (i in 0 until groupsArr.length()) {
                val g = groupsArr.optJSONObject(i) ?: continue
                val id = g.optString("id", "").trim()
                val name = g.optString("name", "").trim()
                if (id.isNotEmpty() && name.isNotEmpty()) {
                    result.add(
                        EduGroup(
                            groupName = name,
                            url = buildGroupUrl(groupId = id, groupName = name)
                        )
                    )
                }
            }
            result
        }.getOrElse { emptyList() }
    }

    /**
     * Загружает список студентов (зачеток) группы через GET /api/rating/students-by-group-id/{groupId}
     */
    suspend fun parseGroupList(url: String): GroupRatingPage = withContext(Dispatchers.IO) {
        val groupId = extractGroupIdFromUrl(url)
        val requestedYear = extractQueryParam(url, "year")
        val requestedSem = extractQueryParam(url, "sem")

        val apiUrl = "$API_BASE/api/rating/students-by-group-id/$groupId"
        val rawJson = runCatching { fetchJson(apiUrl) }.getOrElse { e ->
            com.mguuschedule.util.AppLogger.e("API_RATING", "Сетевая ошибка API рейтинга ($apiUrl): ${e.message}", e)
            throw e
        }
        val root = JSONObject(rawJson)

        if (!root.optBoolean("ok", false)) {
            val err = root.optString("details").ifBlank { root.optString("error", "Ошибка загрузки списка группы") }
            com.mguuschedule.util.AppLogger.e("API_RATING", "Ошибка API рейтинга ($apiUrl): $err")
            throw IllegalStateException(err)
        }

        val count = root.optInt("count", 0)
        val version = root.optString("version", "—")
        val updatedAt = root.optString("updatedAt", "—")
        com.mguuschedule.util.AppLogger.d("API_RATING", "Список студентов: URL=$apiUrl, ok=true, count=$count, version=$version, updatedAt=$updatedAt")
        com.mguuschedule.util.AppLogger.updateDiagnostics {
            it.copy(
                lastRatingSource = "СЕТЬ (api.lk-upm.ru)",
                apiRatingVersion = version,
                lastRatingSyncTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
            )
        }

        val studentsArr = root.optJSONArray("students")
        val students = mutableListOf<StudentRating>()

        var detectedYearFromPortal: String? = null
        var detectedSemFromPortal: String? = null

        if (studentsArr != null) {
            for (i in 0 until studentsArr.length()) {
                val item = studentsArr.optJSONObject(i) ?: continue
                val studentId = item.optString("id", "").trim()
                val zachetka = normalizeText(item.optString("name", ""))
                val legacyUrl = item.optString("urlString", "").trim()

                if (zachetka.isBlank() || studentId.isBlank()) continue

                if (detectedYearFromPortal == null && legacyUrl.isNotEmpty()) {
                    detectedYearFromPortal = extractQueryParam(legacyUrl, "year")
                    detectedSemFromPortal = extractQueryParam(legacyUrl, "sem")
                }

                val effectiveYear = requestedYear?.takeIf { it.isNotBlank() }
                    ?: detectedYearFromPortal?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_YEAR_ID
                val effectiveSem = requestedSem?.takeIf { it.isNotBlank() }
                    ?: detectedSemFromPortal?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_SEM_ID

                val detailApiUrl = "$API_BASE/api/rating/detail/$studentId?year=$effectiveYear&sem=$effectiveSem"

                students.add(
                    StudentRating(
                        zachetka = zachetka,
                        module1 = null,
                        module2 = null,
                        totalScore = null,
                        detailUrl = detailApiUrl
                    )
                )
            }
        }

        val activeYear = requestedYear?.takeIf { it.isNotBlank() }
            ?: detectedYearFromPortal?.takeIf { it.isNotBlank() }
            ?: DEFAULT_YEAR_ID
        val activeSem = requestedSem?.takeIf { it.isNotBlank() }
            ?: detectedSemFromPortal?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SEM_ID

        val years = listOf(
            AcademicPeriod(id = "000000033", title = "2026/2027", isSelected = activeYear == "000000033"),
            AcademicPeriod(id = "000000032", title = "2025/2026", isSelected = activeYear == "000000032")
        )
        val semesters = listOf(
            AcademicPeriod(id = "0", title = "1 полугодие", isSelected = activeSem == "0"),
            AcademicPeriod(id = "1", title = "2 полугодие", isSelected = activeSem == "1")
        )

        GroupRatingPage(
            students = students,
            availableYears = years,
            availableSemesters = semesters
        )
    }

    /**
     * Загружает дисциплины студента через GET /api/rating/detail/{studentId}?year=...&sem=...
     * Поддерживает автоматическую конвертацию старых ссылок portal.mguu.ru из кэша!
     */
    suspend fun parseStudentSubjects(url: String): List<SubjectScore> = withContext(Dispatchers.IO) {
        val resolvedApiUrl = normalizeToDetailApiUrl(url)
        val rawJson = runCatching { fetchJson(resolvedApiUrl) }.getOrElse { e ->
            com.mguuschedule.util.AppLogger.e("API_RATING", "Сетевая ошибка API рейтинга ($resolvedApiUrl): ${e.message}", e)
            throw e
        }
        val root = JSONObject(rawJson)

        if (!root.optBoolean("ok", false)) {
            val err = root.optString("details").ifBlank { root.optString("error", "Не удалось загрузить ведомость студента") }
            com.mguuschedule.util.AppLogger.e("API_RATING", "Ошибка API рейтинга ($resolvedApiUrl): $err")
            throw IllegalStateException(err)
        }

        val count = root.optInt("count", 0)
        val version = root.optString("version", "—")
        val studentId = root.optString("studentID", "—")
        com.mguuschedule.util.AppLogger.d("API_RATING", "Дисциплины студента: URL=$resolvedApiUrl, ok=true, studentId=$studentId, count=$count, version=$version")

        val rowsArr = root.optJSONArray("rows") ?: return@withContext emptyList()
        val subjects = ArrayList<SubjectScore>(rowsArr.length())

        for (i in 0 until rowsArr.length()) {
            val row = rowsArr.optJSONObject(i) ?: continue
            val rawSubject = normalizeText(row.optString("subject", ""))
            if (rawSubject.isBlank() || isHeaderOrSummaryRow(rawSubject)) continue

            val explicitControlType = normalizeText(row.optString("controlType", ""))
            val (parsedTitle, fallbackControlType) = splitSubjectAndControlType(rawSubject)
            val finalTitle = parsedTitle.ifBlank { rawSubject }
            val finalControlType = explicitControlType.ifBlank { fallbackControlType }

            // ВАЖНО: пустая строка "" в JSON означает отсутствие оценки (null), а НЕ 0!
            val semesterPoints = parseNullableFloat(row.optString("semesterPoints", ""))
            val controlPoints = parseNullableFloat(row.optString("controlPoints", ""))
            val explicitTotal = parseNullableFloat(row.optString("total", ""))

            val subjectUrl = row.optString("subjectURL", "").trim()

            subjects.add(
                SubjectScore(
                    title = finalTitle,
                    controlType = finalControlType,
                    module1 = semesterPoints,
                    module2 = controlPoints,
                    totalScore = explicitTotal ?: semesterPoints,
                    detailUrl = subjectUrl
                )
            )
        }

        subjects.sortedWith(compareBy<SubjectScore> { it.title }.thenBy { it.controlType })
    }

    /**
     * Загружает контрольные точки дисциплины через GET /api/rating/subject?url={encodedSubjectURL}
     */
    suspend fun parseControlPoints(url: String): List<ControlPoint> = withContext(Dispatchers.IO) {
        runCatching {
            if (url.isBlank()) return@withContext emptyList()

            val apiUrl = if (url.startsWith("$API_BASE/api/rating/subject")) {
                url
            } else {
                val encodedTarget = URLEncoder.encode(url.trim(), "UTF-8")
                "$API_BASE/api/rating/subject?url=$encodedTarget"
            }

            val rawJson = fetchJson(apiUrl)
            val root = JSONObject(rawJson)
            if (!root.optBoolean("ok", false)) {
                com.mguuschedule.util.AppLogger.e("API_RATING", "Ошибка API рейтинга ($apiUrl): ok=false")
                return@withContext emptyList()
            }

            val pointsArr = root.optJSONArray("points") ?: return@withContext emptyList()
            com.mguuschedule.util.AppLogger.d("API_RATING", "КТ дисциплины: URL=$apiUrl, ok=true, count=${pointsArr.length()}")
            
            val points = ArrayList<ControlPoint>(pointsArr.length())

            for (i in 0 until pointsArr.length()) {
                val item = pointsArr.optJSONObject(i) ?: continue
                val pointNameText = normalizeText(item.optString("title", ""))
                val dateText = normalizeText(item.optString("date", ""))
                val scoreText = item.optString("points", "")

                if (dateText.isBlank() || dateText == "—") continue
                if (isHeaderOrSummaryRow(pointNameText) || isHeaderOrSummaryRow(dateText)) continue

                // Пустая строка "" -> null (оценка еще не выставлена)
                val score = parseNullableInt(scoreText)
                points.add(
                    ControlPoint(
                        pointName = pointNameText.ifBlank { "КТ ${points.size + 1}" },
                        date = dateText,
                        score = score
                    )
                )
            }

            val currentYear = LocalDate.now().year
            points.sortedWith(compareBy<ControlPoint> { cp ->
                SearchEngine.parseControlPointDate(cp.date, currentYear) ?: LocalDate.MAX
            }.thenBy { it.pointName })
        }.getOrElse { emptyList() }
    }

    private fun extractGroupIdFromUrl(url: String): String {
        if (url.contains("/students-by-group-id/")) {
            return url.substringAfter("/students-by-group-id/").substringBefore("?").substringBefore("#").trim()
        }
        return extractQueryParam(url, "groupid")
            ?: extractQueryParam(url, "group")
            ?: url.trim()
    }

    private fun normalizeToDetailApiUrl(url: String): String {
        val clean = url.trim()
        if (clean.startsWith("$API_BASE/api/rating/detail/")) return clean

        val userId = extractQueryParam(clean, "userid") ?: extractQueryParam(clean, "id") ?: clean
        val year = extractQueryParam(clean, "year") ?: DEFAULT_YEAR_ID
        val sem = extractQueryParam(clean, "sem") ?: DEFAULT_SEM_ID
        return "$API_BASE/api/rating/detail/$userId?year=$year&sem=$sem"
    }

    private fun extractQueryParam(url: String, param: String): String? {
        val query = url.substringAfter("?", "").substringBefore("#")
        if (query.isEmpty()) return null
        return query.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.getOrNull(0)?.equals(param, ignoreCase = true) == true }
            ?.getOrNull(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun splitSubjectAndControlType(rawText: String): Pair<String, String> {
        val normalized = normalizeText(rawText)
        val lastComma = normalized.lastIndexOf(',')
        return if (lastComma != -1 && lastComma < normalized.length - 1) {
            val title = normalized.substring(0, lastComma).trim()
            val controlType = normalized.substring(lastComma + 1).trim()
            title to controlType
        } else {
            normalized to ""
        }
    }

    private fun isHeaderOrSummaryRow(text: String): Boolean {
        val lower = normalizeText(text).lowercase()
        return lower.contains("сводный рейтинг") || lower == "итого" || lower.contains("модуль 1") ||
            lower.contains("модуль 2") || lower.contains("модуль") || lower == "всего" ||
            lower.contains("фио") || lower.contains("зачетка") || lower.contains("студент")
    }

    private fun normalizeText(text: String): String {
        return text.replace("\u00A0", " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun parseNullableFloat(text: String?): Float? {
        if (text.isNullOrBlank()) return null
        val clean = normalizeText(text).replace(",", ".")
        if (clean == "—" || clean == "-" || clean == "null") return null
        return clean.toFloatOrNull()
    }

    private fun parseNullableInt(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val clean = normalizeText(text).replace(",", ".")
        if (clean == "—" || clean == "-" || clean == "null") return null
        return clean.toIntOrNull() ?: clean.toFloatOrNull()?.roundToInt()
    }

    private fun fetchJson(url: String): String {
        val start = System.currentTimeMillis()
        com.mguuschedule.util.AppLogger.d("API_REQ", "GET $url")

        val connection = Jsoup.connect(url)
            .ignoreContentType(true)
            .userAgent(USER_AGENT)
            .referrer("https://lk-upm.ru/")
            .header("Origin", "https://lk-upm.ru")
            .header("Accept", "application/json")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .timeout(TIMEOUT_MS)
            .followRedirects(true)

        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL").apply { init(null, trustAllCerts, SecureRandom()) }
            connection.sslSocketFactory(sslContext.socketFactory)
        } catch (e: Exception) { }

        val body = connection.execute().body()
        val duration = System.currentTimeMillis() - start
        com.mguuschedule.util.AppLogger.d("API_RES", "GET $url -> ${duration}мс (${body.length} символов)")
        return body
    }
}