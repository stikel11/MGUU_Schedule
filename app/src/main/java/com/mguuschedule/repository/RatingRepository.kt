package com.mguuschedule.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mguuschedule.model.AcademicPeriod
import com.mguuschedule.model.ControlPoint
import com.mguuschedule.model.EduGroup
import com.mguuschedule.model.GroupRatingPage
import com.mguuschedule.model.StudentRating
import com.mguuschedule.model.SubjectScore
import com.mguuschedule.util.AppLogger
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.roundToInt

class RatingRepository(
    private val ratingDao: RatingDao,
    private val context: Context? = null
) {

    private data class CachedYearsData(
        val years: List<AcademicPeriod>,
        val defaultYearId: String,
        val fetchedAtMs: Long
    )

    private val metaPrefs by lazy {
        context?.getSharedPreferences("rating_meta_cache", Context.MODE_PRIVATE)
    }

    companion object {
        const val RATING_API_BASE = "https://schedule.mguu.ru/api/v1/rating"
        private const val RATING_ORIGIN = "https://schedule.mguu.ru"
        private const val RATING_REFERER = "https://schedule.mguu.ru/rating"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        private const val TIMEOUT_MS = 12_000
        private const val DEFAULT_YEAR_ID = "000000033"
        private const val DEFAULT_SEM_ID = "0"
        private const val YEARS_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val KEY_YEARS_JSON = "years_meta_json"

        private val gson = Gson()
        private val STUDENTS_LIST_TYPE = object : TypeToken<List<StudentRating>>() {}.type
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val SYNC_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

        @Volatile
        private var cachedYearsData: CachedYearsData? = null

        private val TRUST_ALL_SSL_FACTORY by lazy {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            SSLContext.getInstance("SSL").apply {
                init(null, trustAllCerts, SecureRandom())
            }.socketFactory
        }
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

    suspend fun getCachedZachetkasForGroup(groupId: String): List<String> = withContext(Dispatchers.IO) {
        val entity = ratingDao.getLatestRatingCacheForStudent(groupId, "") ?: return@withContext emptyList()
        runCatching {
            val students: List<StudentRating> = gson.fromJson(entity.studentsListJson, STUDENTS_LIST_TYPE) ?: emptyList()
            students.map { it.zachetka }.filter { it.isNotBlank() }.distinct()
        }.getOrDefault(emptyList())
    }

    suspend fun saveCachedRating(entity: RatingEntity) {
        ratingDao.saveRatingCache(entity)
    }

    /**
     * Формирует URL запроса рейтинга группы на schedule.mguu.ru с опциональными year и sem.
     */
    @Suppress("UNUSED_PARAMETER")
    fun buildGroupUrl(groupId: String, groupName: String = "", yearId: String? = null, semId: String? = null): String {
        val cleanGroupId = URLEncoder.encode(groupId.trim(), "UTF-8")
        val params = mutableListOf<String>()
        if (!yearId.isNullOrBlank()) {
            params.add("year=${URLEncoder.encode(yearId.trim(), "UTF-8")}")
        }
        if (!semId.isNullOrBlank()) {
            params.add("sem=${URLEncoder.encode(semId.trim(), "UTF-8")}")
        }
        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return "$RATING_API_BASE/groups/$cleanGroupId/$query"
    }

    /**
     * Загружает доступные учебные годы из GET /api/v1/rating/years/ (с кэшированием в памяти и на диске).
     */
    private fun fetchYearsMetadata(): CachedYearsData {
        val now = System.currentTimeMillis()
        val cached = cachedYearsData ?: loadDiskYearsMetadata()
        if (cached != null && (now - cached.fetchedAtMs) < YEARS_CACHE_TTL_MS) {
            cachedYearsData = cached
            return cached
        }

        val fresh = runCatching {
            val rawJson = fetchJson("$RATING_API_BASE/years/")
            val root = JSONObject(rawJson)
            val defaultCode = root.optCleanString("default").ifBlank { DEFAULT_YEAR_ID }
            val yearsArr = root.optJSONArray("years")
            val parsedYears = mutableListOf<AcademicPeriod>()
            if (yearsArr != null) {
                for (i in 0 until yearsArr.length()) {
                    val item = yearsArr.optJSONObject(i) ?: continue
                    val code = item.optCleanString("code")
                    val title = normalizeText(item.optCleanString("title"))
                    if (code.isNotEmpty() && title.isNotEmpty()) {
                        parsedYears.add(
                            AcademicPeriod(
                                id = code,
                                title = title,
                                isSelected = code == defaultCode
                            )
                        )
                    }
                }
            }
            val sortedYears = parsedYears.sortedByDescending { it.title }
            if (sortedYears.isNotEmpty()) {
                CachedYearsData(sortedYears, defaultCode, now)
            } else null
        }.getOrNull()

        if (fresh != null) {
            cachedYearsData = fresh
            saveDiskYearsMetadata(fresh)
            return fresh
        }

        return cached ?: CachedYearsData(
            years = listOf(
                AcademicPeriod(id = "000000033", title = "2026/2027", isSelected = true),
                AcademicPeriod(id = "000000015", title = "2025/2026", isSelected = false)
            ),
            defaultYearId = DEFAULT_YEAR_ID,
            fetchedAtMs = now
        )
    }

    private fun loadDiskYearsMetadata(): CachedYearsData? {
        val raw = metaPrefs?.getString(KEY_YEARS_JSON, null) ?: return null
        return runCatching { gson.fromJson(raw, CachedYearsData::class.java) }.getOrNull()
    }

    private fun saveDiskYearsMetadata(data: CachedYearsData) {
        metaPrefs?.edit()?.putString(KEY_YEARS_JSON, gson.toJson(data))?.apply()
    }

    /**
     * Загружает каталог групп БРС из GET /api/v1/rating/groups/
     */
    suspend fun parseMasterGroupsCatalogue(url: String = ""): List<EduGroup> = withContext(Dispatchers.IO) {
        runCatching {
            val rawJson = fetchJson("$RATING_API_BASE/groups/")
            val root = JSONObject(rawJson)
            val coursesArr = root.optJSONArray("courses") ?: return@withContext emptyList()
            val result = mutableListOf<EduGroup>()

            for (c in 0 until coursesArr.length()) {
                val courseObj = coursesArr.optJSONObject(c) ?: continue
                val groupsArr = courseObj.optJSONArray("groups") ?: continue
                for (i in 0 until groupsArr.length()) {
                    val g = groupsArr.optJSONObject(i) ?: continue
                    val code = g.optCleanString("code")
                    val name = normalizeText(g.optCleanString("name"))
                    if (code.isNotEmpty() && name.isNotEmpty()) {
                        result.add(
                            EduGroup(
                                groupName = name,
                                url = buildGroupUrl(groupId = code, groupName = name)
                            )
                        )
                    }
                }
            }
            result
        }.getOrElse { emptyList() }
    }

    /**
     * Загружает список студентов (зачеток) и их сводные баллы группы через
     * GET /api/v1/rating/groups/{groupCode}/?year=...&sem=...
     */
    suspend fun parseGroupList(url: String): GroupRatingPage = withContext(Dispatchers.IO) {
        val groupId = extractGroupIdFromUrl(url)
        val yearsMeta = fetchYearsMetadata()

        val rawRequestedYear = extractQueryParam(url, "year")
        // Совместимость со старым кодом 2025/2026 ("000000032" в api.lk-upm.ru -> "000000015" в schedule.mguu.ru)
        val normalizedRequestedYear = when {
            rawRequestedYear.isNullOrBlank() -> null
            rawRequestedYear == "000000032" &&
                yearsMeta.years.none { it.id == "000000032" } &&
                yearsMeta.years.any { it.id == "000000015" } -> "000000015"
            else -> rawRequestedYear
        }
        val requestedSem = extractQueryParam(url, "sem")

        val apiUrl = buildGroupUrl(
            groupId = groupId,
            groupName = "",
            yearId = normalizedRequestedYear,
            semId = requestedSem
        )

        val rawJson = runCatching { fetchJson(apiUrl) }.getOrElse { e ->
            AppLogger.e("API_RATING", "Сетевая ошибка API рейтинга ($apiUrl): ${e.message}", e)
            throw e
        }
        val root = JSONObject(rawJson)

        val detailError = root.optCleanString("detail")
        if (detailError.isNotEmpty() && !root.has("students")) {
            AppLogger.e("API_RATING", "Ошибка API рейтинга ($apiUrl): $detailError")
            throw IllegalStateException(detailError)
        }

        val activeYear = root.optCleanString("year")
            .ifBlank { normalizedRequestedYear ?: yearsMeta.defaultYearId }
        val activeSem = if (root.has("sem") && !root.isNull("sem")) {
            root.optString("sem", DEFAULT_SEM_ID).trim().ifBlank { DEFAULT_SEM_ID }
        } else {
            requestedSem?.takeIf { it.isNotBlank() } ?: DEFAULT_SEM_ID
        }

        val studentsArr = root.optJSONArray("students")
        val students = ArrayList<StudentRating>(studentsArr?.length() ?: 0)

        if (studentsArr != null) {
            for (i in 0 until studentsArr.length()) {
                val item = studentsArr.optJSONObject(i) ?: continue
                val studentCode = item.optCleanString("student_code")
                val zachetka = normalizeText(item.optCleanString("student_number"))
                if (zachetka.isBlank() || studentCode.isBlank()) continue

                val module1 = parseNullableFloat(item.optCleanString("semester_points"))
                val module2 = parseNullableFloat(item.optCleanString("answer_points"))
                val totalScore = parseNullableFloat(item.optCleanString("rating"))

                val encodedStudentCode = URLEncoder.encode(studentCode, "UTF-8")
                val detailApiUrl = "$RATING_API_BASE/students/$encodedStudentCode/?year=$activeYear&sem=$activeSem"

                students.add(
                    StudentRating(
                        zachetka = zachetka,
                        module1 = module1,
                        module2 = module2,
                        totalScore = totalScore,
                        detailUrl = detailApiUrl
                    )
                )
            }
        }

        AppLogger.d(
            "API_RATING",
            "Список студентов: URL=$apiUrl, count=${students.size}, year=$activeYear, sem=$activeSem"
        )
        AppLogger.updateDiagnostics {
            it.copy(
                lastRatingSource = "СЕТЬ (schedule.mguu.ru)",
                apiRatingVersion = "v1 ($activeYear / сем.$activeSem)",
                lastRatingSyncTime = LocalDateTime.now().format(SYNC_TIME_FORMATTER)
            )
        }

        val yearsList = yearsMeta.years.map { it.copy(isSelected = it.id == activeYear) }.let { list ->
            if (list.any { it.id == activeYear }) list
            else list + AcademicPeriod(id = activeYear, title = activeYear, isSelected = true)
        }

        val semesters = listOf(
            AcademicPeriod(id = "0", title = "Осенний семестр", isSelected = activeSem == "0"),
            AcademicPeriod(id = "1", title = "Весенний семестр", isSelected = activeSem == "1")
        )

        GroupRatingPage(
            students = students,
            availableYears = yearsList,
            availableSemesters = semesters
        )
    }

    /**
     * Загружает дисциплины студента через GET /api/v1/rating/students/{studentCode}/?year=...&sem=...
     * Поддерживает автоматическую конвертацию старых ссылок api.lk-upm.ru и portal.mguu.ru из кэша.
     */
    suspend fun parseStudentSubjects(url: String): List<SubjectScore> = withContext(Dispatchers.IO) {
        val resolvedApiUrl = normalizeToDetailApiUrl(url)
        val studentCode = extractStudentCodeFromUrl(resolvedApiUrl)

        val rawJson = runCatching { fetchJson(resolvedApiUrl) }.getOrElse { e ->
            AppLogger.e("API_RATING", "Сетевая ошибка API рейтинга ($resolvedApiUrl): ${e.message}", e)
            throw e
        }
        val root = JSONObject(rawJson)

        val detailError = root.optCleanString("detail")
        if (detailError.isNotEmpty() && !root.has("disciplines")) {
            AppLogger.e("API_RATING", "Ошибка API рейтинга ($resolvedApiUrl): $detailError")
            throw IllegalStateException(detailError)
        }

        val effectiveYear = root.optCleanString("year_code")
            .ifBlank { extractQueryParam(resolvedApiUrl, "year") ?: DEFAULT_YEAR_ID }
        val effectiveSem = if (root.has("sem") && !root.isNull("sem")) {
            root.optString("sem", DEFAULT_SEM_ID).trim().ifBlank { DEFAULT_SEM_ID }
        } else {
            extractQueryParam(resolvedApiUrl, "sem") ?: DEFAULT_SEM_ID
        }

        val disciplinesArr = root.optJSONArray("disciplines") ?: return@withContext emptyList()
        AppLogger.d(
            "API_RATING",
            "Дисциплины студента: URL=$resolvedApiUrl, student=$studentCode, count=${disciplinesArr.length()}"
        )

        val subjects = ArrayList<SubjectScore>(disciplinesArr.length())

        for (i in 0 until disciplinesArr.length()) {
            val row = disciplinesArr.optJSONObject(i) ?: continue
            val rawDiscipline = normalizeText(row.optCleanString("discipline"))
            if (rawDiscipline.isBlank() || isHeaderOrSummaryRow(rawDiscipline)) continue

            val (parsedTitle, parsedControlType) = splitSubjectAndControlType(rawDiscipline)
            val finalTitle = parsedTitle.ifBlank { rawDiscipline }

            val disciplineCode = row.optCleanString("discipline_code")
            val controlCode = row.optCleanString("control_code")

            // Пустая строка "" в JSON означает отсутствие оценки (null), а НЕ 0
            val semesterPoints = parseNullableFloat(row.optCleanString("semester_points"))
            val answerPoints = parseNullableFloat(row.optCleanString("answer_points"))
            val explicitTotal = parseNullableFloat(row.optCleanString("total"))

            val checkpointUrl = if (studentCode.isNotBlank() && disciplineCode.isNotBlank()) {
                val encStudent = URLEncoder.encode(studentCode, "UTF-8")
                val encDisc = URLEncoder.encode(disciplineCode, "UTF-8")
                val controlParam = if (controlCode.isNotBlank()) {
                    "&control=${URLEncoder.encode(controlCode, "UTF-8")}"
                } else ""
                "$RATING_API_BASE/students/$encStudent/disciplines/$encDisc/?year=$effectiveYear&sem=$effectiveSem$controlParam"
            } else ""

            subjects.add(
                SubjectScore(
                    title = finalTitle,
                    controlType = parsedControlType,
                    module1 = semesterPoints,
                    module2 = answerPoints,
                    totalScore = explicitTotal ?: semesterPoints,
                    detailUrl = checkpointUrl
                )
            )
        }

        subjects.sortedWith(compareBy<SubjectScore> { it.title }.thenBy { it.controlType })
    }

    /**
     * Загружает контрольные точки дисциплины через
     * GET /api/v1/rating/students/{studentCode}/disciplines/{disciplineCode}/?year=...&sem=...&control=...
     */
    suspend fun parseControlPoints(url: String): List<ControlPoint> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = url.trim()
            if (cleanUrl.isBlank() || !cleanUrl.startsWith(RATING_API_BASE)) {
                return@withContext emptyList()
            }

            val rawJson = fetchJson(cleanUrl)
            val root = JSONObject(rawJson)
            val checkpointsArr = root.optJSONArray("checkpoints") ?: return@withContext emptyList()

            AppLogger.d("API_RATING", "КТ дисциплины: URL=$cleanUrl, count=${checkpointsArr.length()}")

            val points = ArrayList<ControlPoint>(checkpointsArr.length())

            for (i in 0 until checkpointsArr.length()) {
                val item = checkpointsArr.optJSONObject(i) ?: continue
                val number = item.optInt("number", points.size + 1)
                val dateText = normalizeText(item.optCleanString("date"))
                val scoreText = item.optCleanString("points")

                if (dateText.isBlank() || dateText == "—") continue
                if (isHeaderOrSummaryRow(dateText)) continue

                val score = parseNullableInt(scoreText)
                points.add(
                    ControlPoint(
                        pointName = "КТ $number",
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
        val clean = url.trim()
        if (clean.contains("/rating/groups/")) {
            return clean.substringAfter("/rating/groups/")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                .trim()
        }
        if (clean.contains("/students-by-group-id/")) {
            return clean.substringAfter("/students-by-group-id/")
                .substringBefore("?")
                .substringBefore("#")
                .trim()
        }
        return extractQueryParam(clean, "groupid")
            ?: extractQueryParam(clean, "group")
            ?: clean
    }

    private fun extractStudentCodeFromUrl(url: String): String {
        val clean = url.trim()
        if (clean.contains("/rating/students/")) {
            return clean.substringAfter("/rating/students/")
                .substringBefore("/")
                .substringBefore("?")
                .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                .trim()
        }
        if (clean.contains("/rating/detail/")) {
            return clean.substringAfter("/rating/detail/")
                .substringBefore("/")
                .substringBefore("?")
                .trim()
        }
        return extractQueryParam(clean, "userid") ?: extractQueryParam(clean, "id") ?: ""
    }

    private fun normalizeToDetailApiUrl(url: String): String {
        val clean = url.trim()
        if (clean.startsWith("$RATING_API_BASE/students/")) return clean

        val studentCode = extractStudentCodeFromUrl(clean).ifBlank { clean }
        val rawYear = extractQueryParam(clean, "year") ?: DEFAULT_YEAR_ID
        val year = if (rawYear == "000000032") "000000015" else rawYear
        val sem = extractQueryParam(clean, "sem") ?: DEFAULT_SEM_ID
        return "$RATING_API_BASE/students/${URLEncoder.encode(studentCode, "UTF-8")}/?year=$year&sem=$sem"
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
        return text.replace("\u00A0", " ").replace(WHITESPACE_REGEX, " ").trim()
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

    private fun JSONObject.optCleanString(key: String): String {
        if (isNull(key)) return ""
        return optString(key, "").trim()
    }

    private fun fetchJson(url: String): String {
        val start = System.currentTimeMillis()
        AppLogger.d("API_REQ", "GET $url")

        val connection = Jsoup.connect(url)
            .ignoreContentType(true)
            .userAgent(USER_AGENT)
            .referrer(RATING_REFERER)
            .header("Origin", RATING_ORIGIN)
            .header("Accept", "application/json")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .timeout(TIMEOUT_MS)
            .followRedirects(true)

        runCatching {
            connection.sslSocketFactory(TRUST_ALL_SSL_FACTORY)
        }

        val body = connection.execute().body()
        val duration = System.currentTimeMillis() - start
        AppLogger.d("API_RES", "GET $url -> ${duration}мс (${body.length} символов)")
        return body
    }
}
