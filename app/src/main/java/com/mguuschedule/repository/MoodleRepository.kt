package com.mguuschedule.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mguuschedule.model.MoodleCourse
import com.mguuschedule.model.MoodleCourseSection
import com.mguuschedule.model.MoodleDeadline
import com.mguuschedule.model.MoodleFileContent
import com.mguuschedule.model.MoodleGradeItem
import com.mguuschedule.model.MoodleModule
import com.mguuschedule.model.MoodleSession
import com.mguuschedule.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.net.URLEncoder

class MoodleRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val courseCachePrefs = context.getSharedPreferences(COURSE_CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    private val addonsDao: LessonAddonsDao by lazy {
        AppDatabase.getDatabase(context).lessonAddonsDao()
    }

    companion object {
        const val MOODLE_BASE_URL = "https://online.mguu.ru"
        const val MOODLE_MY_URL = "$MOODLE_BASE_URL/my/"
        const val MOODLE_COURSES_URL = "$MOODLE_BASE_URL/my/courses.php"
        const val MOODLE_CALENDAR_URL = "$MOODLE_BASE_URL/calendar/view.php"
        const val MOODLE_MESSAGES_URL = "$MOODLE_BASE_URL/message/index.php"
        const val MOODLE_SERVICE = "moodle_mobile_app"
        const val SSO_URL_SCHEME = "moodlemobile"

        const val PREFS_NAME = "moodle_prefs"
        private const val COURSE_CACHE_PREFS_NAME = "moodle_course_cache"
        private const val KEY_SESSION = "moodle_session_json"
        private const val KEY_COURSES_CACHE = "moodle_courses_cache"
        private const val KEY_DEADLINES_CACHE = "moodle_deadlines_cache"
        private const val KEY_LAST_AUTOLOGIN_TS = "moodle_last_autologin_ts"

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        private const val TIMEOUT_MS = 15_000
        private const val AUTOLOGIN_COOLDOWN_MS = 6 * 60 * 1000L

        private val gson = Gson()
        private val COURSES_TYPE = object : TypeToken<List<MoodleCourse>>() {}.type
        private val DEADLINES_TYPE = object : TypeToken<List<MoodleDeadline>>() {}.type
        private val SECTIONS_TYPE = object : TypeToken<List<MoodleCourseSection>>() {}.type
        private val GRADES_TYPE = object : TypeToken<List<MoodleGradeItem>>() {}.type

        fun hasSavedSession(context: Context): Boolean {
            val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return !p.getString(KEY_SESSION, null).isNullOrBlank()
        }
    }

    fun loadSavedSession(): MoodleSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { gson.fromJson(raw, MoodleSession::class.java) }.getOrNull()
    }

    fun saveSession(session: MoodleSession) {
        prefs.edit().putString(KEY_SESSION, gson.toJson(session)).apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_SESSION)
            .remove(KEY_COURSES_CACHE)
            .remove(KEY_DEADLINES_CACHE)
            .remove(KEY_LAST_AUTOLOGIN_TS)
            .apply()
        courseCachePrefs.edit().clear().apply()
    }

    fun loadCachedCourses(): List<MoodleCourse> {
        val raw = prefs.getString(KEY_COURSES_CACHE, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<MoodleCourse>>(raw, COURSES_TYPE) }
            .getOrDefault(emptyList())
    }

    fun saveCachedCourses(courses: List<MoodleCourse>) {
        prefs.edit().putString(KEY_COURSES_CACHE, gson.toJson(courses)).apply()
    }

    fun loadCachedDeadlines(): List<MoodleDeadline> {
        val raw = prefs.getString(KEY_DEADLINES_CACHE, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<MoodleDeadline>>(raw, DEADLINES_TYPE) }
            .getOrDefault(emptyList())
    }

    fun saveCachedDeadlines(deadlines: List<MoodleDeadline>) {
        prefs.edit().putString(KEY_DEADLINES_CACHE, gson.toJson(deadlines)).apply()
    }

    fun loadCachedCourseSections(courseId: Long): List<MoodleCourseSection> {
        val raw = courseCachePrefs.getString("sections_$courseId", null) ?: return emptyList()
        return runCatching { gson.fromJson<List<MoodleCourseSection>>(raw, SECTIONS_TYPE) }
            .getOrDefault(emptyList())
    }

    fun saveCachedCourseSections(courseId: Long, sections: List<MoodleCourseSection>) {
        courseCachePrefs.edit()
            .putString("sections_$courseId", gson.toJson(sections))
            .putLong("sections_ts_$courseId", System.currentTimeMillis())
            .apply()
    }

    fun loadCachedCourseGrades(courseId: Long): List<MoodleGradeItem> {
        val raw = courseCachePrefs.getString("grades_$courseId", null) ?: return emptyList()
        return runCatching { gson.fromJson<List<MoodleGradeItem>>(raw, GRADES_TYPE) }
            .getOrDefault(emptyList())
    }

    fun saveCachedCourseGrades(courseId: Long, grades: List<MoodleGradeItem>) {
        courseCachePrefs.edit()
            .putString("grades_$courseId", gson.toJson(grades))
            .putLong("grades_ts_$courseId", System.currentTimeMillis())
            .apply()
    }

    /**
     * Формирует URL для входа в СДО через Портал МГУУ (OAuth2 / SSO) с возвратом токена мобильного API.
     */
    fun buildSsoLaunchUrl(passport: String): String {
        val encodedPassport = URLEncoder.encode(passport, "UTF-8")
        return "$MOODLE_BASE_URL/admin/tool/mobile/launch.php?service=$MOODLE_SERVICE&passport=$encodedPassport&urlscheme=$SSO_URL_SCHEME"
    }

    /**
     * Обрабатывает callback URI вида `moodlemobile://token=<base64(signature:::token:::privatetoken)>`
     */
    suspend fun completeSsoLogin(callbackUrl: String): MoodleSession = withContext(Dispatchers.IO) {
        val tokenParam = callbackUrl.substringAfter("token=", "").substringBefore("&").trim()
        if (tokenParam.isBlank()) {
            throw IllegalStateException("Не удалось получить токен авторизации от СДО")
        }

        val decodedUrl = Uri.decode(tokenParam)
        val decodedString = runCatching {
            String(Base64.decode(decodedUrl, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrElse {
            String(Base64.decode(decodedUrl, Base64.URL_SAFE), Charsets.UTF_8)
        }

        val parts = decodedString.split(":::")
        val token = parts.getOrNull(1)?.trim().orEmpty()
        val privateToken = parts.getOrNull(2)?.trim().orEmpty()
        if (token.isBlank()) {
            throw IllegalStateException("Пустой токен в ответе СДО")
        }

        val session = fetchSiteInfo(token = token, privateToken = privateToken)
        saveSession(session)
        session
    }

    /**
     * Вход по логину/email и паролю СДО через официальный `/login/token.php`.
     */
    suspend fun loginWithCredentials(username: String, password: String): MoodleSession = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        if (cleanUser.isEmpty() || password.isEmpty()) {
            throw IllegalArgumentException("Введите логин и пароль")
        }

        val url = "$MOODLE_BASE_URL/login/token.php"
        AppLogger.d("MOODLE_API", "POST $url (user=$cleanUser)")

        val responseBody = Jsoup.connect(url)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .userAgent(USER_AGENT)
            .method(Connection.Method.POST)
            .data("username", cleanUser)
            .data("password", password)
            .data("service", MOODLE_SERVICE)
            .timeout(TIMEOUT_MS)
            .execute()
            .body()

        val json = JSONObject(responseBody)
        val token = json.optCleanString("token")
        if (token.isBlank()) {
            val errorMsg = json.optCleanString("error").ifBlank {
                "Неверный логин или пароль. Попробуйте вход через Портал МГУУ (SSO)."
            }
            AppLogger.e("MOODLE_API", "Ошибка входа: $errorMsg")
            throw IllegalStateException(errorMsg)
        }

        val privateToken = json.optCleanString("privatetoken")
        val session = fetchSiteInfo(token = token, privateToken = privateToken)
        saveSession(session)
        session
    }

    /**
     * Загружает профиль пользователя (`core_webservice_get_site_info`).
     */
    suspend fun fetchSiteInfo(token: String, privateToken: String = ""): MoodleSession = withContext(Dispatchers.IO) {
        val raw = callRestApi(
            token = token,
            wsFunction = "core_webservice_get_site_info"
        )
        val root = JSONObject(raw)
        throwIfMoodleError(root)

        val userId = root.optLong("userid", 0L)
        if (userId <= 0L) {
            throw IllegalStateException("Не удалось определить профиль пользователя в СДО")
        }

        MoodleSession(
            token = token,
            privateToken = privateToken,
            userId = userId,
            fullName = root.optCleanString("fullname").ifBlank { root.optCleanString("username") },
            username = root.optCleanString("username"),
            siteName = root.optCleanString("sitename").ifBlank { "СДО МГУУ" },
            avatarUrl = root.optCleanString("userpictureurl"),
            baseUrl = root.optCleanString("siteurl").ifBlank { MOODLE_BASE_URL }
        )
    }

    /**
     * Загружает список курсов студента (`core_enrol_get_users_courses`).
     */
    suspend fun fetchCourses(session: MoodleSession): List<MoodleCourse> = withContext(Dispatchers.IO) {
        val raw = callRestApi(
            token = session.token,
            wsFunction = "core_enrol_get_users_courses",
            params = mapOf("userid" to session.userId.toString())
        )

        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            throwIfMoodleError(JSONObject(trimmed))
            return@withContext emptyList()
        }

        val arr = JSONArray(trimmed)
        val courses = ArrayList<MoodleCourse>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optLong("id", 0L)
            val fullName = stripHtml(obj.optCleanString("fullname"))
            if (id <= 0L || fullName.isBlank()) continue

            val progress = if (obj.has("progress") && !obj.isNull("progress")) {
                obj.optDouble("progress", -1.0).takeIf { !it.isNaN() && it >= 0.0 }?.toFloat()
            } else null

            courses.add(
                MoodleCourse(
                    id = id,
                    fullName = fullName,
                    shortName = stripHtml(obj.optCleanString("shortname")),
                    summary = stripHtml(obj.optCleanString("summary")),
                    progress = progress,
                    completed = obj.optBoolean("completed", false),
                    startDate = obj.optLong("startdate", 0L),
                    endDate = obj.optLong("enddate", 0L),
                    isFavourite = obj.optBoolean("isfavourite", false)
                )
            )
        }

        val sorted = courses.sortedWith(
            compareByDescending<MoodleCourse> { it.isFavourite }
                .thenBy { it.completed }
                .thenByDescending { it.startDate }
                .thenBy { it.fullName.lowercase() }
        )
        saveCachedCourses(sorted)
        sorted
    }

    /**
     * Загружает ближайшие и недавно просроченные дедлайны (календарь + задания).
     */
    suspend fun fetchUpcomingDeadlines(
        session: MoodleSession,
        courses: List<MoodleCourse>
    ): List<MoodleDeadline> = withContext(Dispatchers.IO) {
        val nowSec = System.currentTimeMillis() / 1000L
        val fromSec = nowSec - 7L * 24L * 3600L
        val deadlines = mutableListOf<MoodleDeadline>()
        val seenKeys = HashSet<String>()

        // 1. События календаря (тесты, задания, вебинары, дедлайны)
        runCatching {
            val raw = callRestApi(
                token = session.token,
                wsFunction = "core_calendar_get_action_events_by_timesort",
                params = mapOf(
                    "timesortfrom" to fromSec.toString(),
                    "limitnum" to "50"
                )
            )
            val root = JSONObject(raw)
            if (!root.has("exception")) {
                val events = root.optJSONArray("events")
                if (events != null) {
                    for (i in 0 until events.length()) {
                        val ev = events.optJSONObject(i) ?: continue
                        val id = ev.optLong("id", 0L)
                        val name = stripHtml(ev.optCleanString("name"))
                        val timeSort = ev.optLong("timesort", ev.optLong("timestart", 0L))
                        if (name.isBlank() || timeSort <= 0L) continue

                        val courseObj = ev.optJSONObject("course")
                        val courseId = courseObj?.optLong("id", 0L) ?: 0L
                        val courseName = stripHtml(courseObj?.optCleanString("fullname").orEmpty())
                        val url = ev.optCleanString("url")
                        val modName = ev.optCleanString("modulename").ifBlank { "assign" }
                        val desc = stripHtml(ev.optCleanString("description"))
                        val overdue = ev.optBoolean("overdue", timeSort < nowSec)

                        val dedupKey = "$courseId:${name.lowercase()}:$timeSort"
                        if (seenKeys.add(dedupKey)) {
                            deadlines.add(
                                MoodleDeadline(
                                    id = id,
                                    name = name,
                                    courseName = courseName,
                                    courseId = courseId,
                                    dueDateEpochSec = timeSort,
                                    url = url,
                                    moduleName = modName,
                                    description = desc,
                                    overdue = overdue
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Дополнительно подтягиваем задания из mod_assign_get_assignments (если календарь вернул мало данных)
        if (deadlines.size < 10 && courses.isNotEmpty()) {
            runCatching {
                val params = mutableMapOf<String, String>()
                courses.take(20).forEachIndexed { idx, c ->
                    params["courseids[$idx]"] = c.id.toString()
                }
                val raw = callRestApi(
                    token = session.token,
                    wsFunction = "mod_assign_get_assignments",
                    params = params
                )
                val root = JSONObject(raw)
                if (!root.has("exception")) {
                    val coursesArr = root.optJSONArray("courses")
                    if (coursesArr != null) {
                        for (i in 0 until coursesArr.length()) {
                            val cObj = coursesArr.optJSONObject(i) ?: continue
                            val courseId = cObj.optLong("id", 0L)
                            val courseName = stripHtml(cObj.optCleanString("fullname"))
                            val assigns = cObj.optJSONArray("assignments") ?: continue
                            for (j in 0 until assigns.length()) {
                                val a = assigns.optJSONObject(j) ?: continue
                                val dueDate = a.optLong("duedate", 0L)
                                if (dueDate < fromSec) continue
                                val cmid = a.optLong("cmid", a.optLong("id", 0L))
                                val name = stripHtml(a.optCleanString("name"))
                                if (name.isBlank()) continue
                                val dedupKey = "$courseId:${name.lowercase()}:$dueDate"
                                if (seenKeys.add(dedupKey)) {
                                    deadlines.add(
                                        MoodleDeadline(
                                            id = cmid,
                                            name = name,
                                            courseName = courseName,
                                            courseId = courseId,
                                            dueDateEpochSec = dueDate,
                                            url = "$MOODLE_BASE_URL/mod/assign/view.php?id=$cmid",
                                            moduleName = "assign",
                                            description = stripHtml(a.optCleanString("intro")),
                                            overdue = dueDate < nowSec
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val sorted = deadlines.sortedBy { it.dueDateEpochSec }
        saveCachedDeadlines(sorted)
        sorted
    }

    /**
     * Загружает разделы и материалы курса (`core_course_get_contents`).
     */
    suspend fun fetchCourseContents(
        session: MoodleSession,
        courseId: Long
    ): List<MoodleCourseSection> = withContext(Dispatchers.IO) {
        val raw = callRestApi(
            token = session.token,
            wsFunction = "core_course_get_contents",
            params = mapOf("courseid" to courseId.toString())
        )
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            throwIfMoodleError(JSONObject(trimmed))
            return@withContext emptyList()
        }

        val arr = JSONArray(trimmed)
        val sections = ArrayList<MoodleCourseSection>(arr.length())
        for (i in 0 until arr.length()) {
            val secObj = arr.optJSONObject(i) ?: continue
            val secId = secObj.optLong("id", i.toLong())
            val secName = stripHtml(secObj.optCleanString("name")).ifBlank { "Раздел ${i + 1}" }
            val secSummary = stripHtml(secObj.optCleanString("summary"))
            val modulesArr = secObj.optJSONArray("modules")
            val modules = mutableListOf<MoodleModule>()

            if (modulesArr != null) {
                for (m in 0 until modulesArr.length()) {
                    val modObj = modulesArr.optJSONObject(m) ?: continue
                    val modId = modObj.optLong("id", 0L)
                    val modName = modObj.optCleanString("modname")
                    val name = stripHtml(modObj.optCleanString("name"))
                    val description = stripHtml(modObj.optCleanString("description"))
                    if (name.isBlank() && description.isBlank()) continue

                    val completionObj = modObj.optJSONObject("completiondata")
                    val completed: Boolean? = if (completionObj != null && completionObj.has("state")) {
                        completionObj.optInt("state", 0) > 0
                    } else null

                    val contentsArr = modObj.optJSONArray("contents")
                    val files = mutableListOf<MoodleFileContent>()
                    if (contentsArr != null) {
                        for (f in 0 until contentsArr.length()) {
                            val fileObj = contentsArr.optJSONObject(f) ?: continue
                            val fileUrlRaw = fileObj.optCleanString("fileurl")
                            if (fileUrlRaw.isBlank()) continue
                            val authenticatedUrl = appendToken(fileUrlRaw, session.token)
                            files.add(
                                MoodleFileContent(
                                    fileName = fileObj.optCleanString("filename").ifBlank { name },
                                    fileUrl = authenticatedUrl,
                                    fileSize = fileObj.optLong("filesize", 0L),
                                    mimeType = fileObj.optCleanString("mimetype"),
                                    timeModified = fileObj.optLong("timemodified", 0L)
                                )
                            )
                        }
                    }

                    modules.add(
                        MoodleModule(
                            id = modId,
                            name = name.ifBlank { description.take(80) },
                            modName = modName,
                            modPlural = modObj.optCleanString("modplural"),
                            url = modObj.optCleanString("url"),
                            description = description,
                            contents = files,
                            completed = completed
                        )
                    )
                }
            }

            if (modules.isNotEmpty() || secSummary.isNotBlank()) {
                sections.add(
                    MoodleCourseSection(
                        id = secId,
                        name = secName,
                        summary = secSummary,
                        modules = modules
                    )
                )
            }
        }
        saveCachedCourseSections(courseId, sections)
        sections
    }

    /**
     * Загружает оценки студента по курсу (`gradereport_user_get_grade_items`).
     */
    suspend fun fetchCourseGrades(
        session: MoodleSession,
        courseId: Long
    ): List<MoodleGradeItem> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = callRestApi(
                token = session.token,
                wsFunction = "gradereport_user_get_grade_items",
                params = mapOf(
                    "courseid" to courseId.toString(),
                    "userid" to session.userId.toString()
                )
            )
            val root = JSONObject(raw)
            if (root.has("exception")) return@withContext emptyList()

            val userGrades = root.optJSONArray("usergrades") ?: return@withContext emptyList()
            val firstUser = userGrades.optJSONObject(0) ?: return@withContext emptyList()
            val itemsArr = firstUser.optJSONArray("gradeitems") ?: return@withContext emptyList()

            val grades = ArrayList<MoodleGradeItem>(itemsArr.length())
            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.optJSONObject(i) ?: continue
                val id = item.optLong("id", i.toLong())
                val itemType = item.optCleanString("itemtype")
                val rawName = stripHtml(item.optCleanString("itemname"))
                val itemName = when {
                    rawName.isNotBlank() -> rawName
                    itemType == "course" -> "Итого за курс"
                    else -> continue
                }
                val gradeFormatted = stripHtml(item.optCleanString("gradeformatted")).ifBlank { "—" }
                val pctFormatted = stripHtml(item.optCleanString("percentageformatted"))
                val minVal = item.optDouble("grademin", Double.NaN)
                val maxVal = item.optDouble("grademax", Double.NaN)
                val rangeStr = if (!minVal.isNaN() && !maxVal.isNaN() && maxVal > 0.0) {
                    "${minVal.toInt()}–${maxVal.toInt()}"
                } else ""
                val feedback = stripHtml(item.optCleanString("feedback"))

                grades.add(
                    MoodleGradeItem(
                        id = id,
                        itemName = itemName,
                        gradeFormatted = gradeFormatted,
                        percentageFormatted = pctFormatted,
                        rangeFormatted = rangeStr,
                        feedback = feedback
                    )
                )
            }
            saveCachedCourseGrades(courseId, grades)
            grades
        }.getOrDefault(emptyList())
    }

    /**
     * Запрашивает одноразовый ключ автовхода в веб-портал СДО (`tool_mobile_get_autologin_key`).
     * Если приватного токена нет или действует лимит Moodle (раз в 6 минут), возвращает [targetUrl].
     */
    suspend fun getAutoLoginUrl(session: MoodleSession, targetUrl: String): String = withContext(Dispatchers.IO) {
        val cleanTarget = targetUrl.ifBlank { MOODLE_MY_URL }
        if (session.privateToken.isBlank()) return@withContext cleanTarget

        val now = System.currentTimeMillis()
        val lastTs = prefs.getLong(KEY_LAST_AUTOLOGIN_TS, 0L)
        if (now - lastTs < AUTOLOGIN_COOLDOWN_MS) {
            return@withContext cleanTarget
        }

        runCatching {
            val raw = callRestApi(
                token = session.token,
                wsFunction = "tool_mobile_get_autologin_key",
                params = mapOf("privatetoken" to session.privateToken)
            )
            val root = JSONObject(raw)
            if (root.has("exception")) return@withContext cleanTarget

            val key = root.optCleanString("key")
            val autoLoginUrl = root.optCleanString("autologinurl")
            if (key.isNotBlank() && autoLoginUrl.isNotBlank()) {
                prefs.edit().putLong(KEY_LAST_AUTOLOGIN_TS, now).apply()
                "$autoLoginUrl?userid=${session.userId}&key=${URLEncoder.encode(key, "UTF-8")}&urltogo=${URLEncoder.encode(cleanTarget, "UTF-8")}"
            } else {
                cleanTarget
            }
        }.getOrDefault(cleanTarget)
    }

    /**
     * Прикрепляет файл или ссылку из СДО Moodle напрямую в «Материалы к парам».
     */
    suspend fun attachToLesson(
        lessonKey: String,
        title: String,
        url: String,
        fileName: String? = null,
        mimeType: String? = null,
        sizeBytes: Long? = null
    ) = withContext(Dispatchers.IO) {
        val type = when {
            mimeType?.contains("pdf", ignoreCase = true) == true ||
                fileName?.endsWith(".pdf", ignoreCase = true) == true -> "PDF"
            mimeType?.contains("presentation", ignoreCase = true) == true ||
                fileName?.endsWith(".pptx", ignoreCase = true) == true ||
                fileName?.endsWith(".ppt", ignoreCase = true) == true -> "PPTX"
            !fileName.isNullOrBlank() -> "FILE"
            else -> "URL"
        }

        addonsDao.insertMaterial(
            LessonMaterialEntity(
                lessonKey = lessonKey,
                title = title.trim().ifBlank { fileName ?: "Материал СДО" },
                type = type,
                uriOrUrl = url,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes?.takeIf { it > 0L }
            )
        )
    }

    private fun callRestApi(
        token: String,
        wsFunction: String,
        params: Map<String, String> = emptyMap()
    ): String {
        val url = "$MOODLE_BASE_URL/webservice/rest/server.php"
        val start = System.currentTimeMillis()
        AppLogger.d("MOODLE_API", "POST $wsFunction")

        val conn = Jsoup.connect(url)
            .ignoreContentType(true)
            .ignoreHttpErrors(true)
            .userAgent(USER_AGENT)
            .method(Connection.Method.POST)
            .data("wstoken", token)
            .data("wsfunction", wsFunction)
            .data("moodlewsrestformat", "json")
            .timeout(TIMEOUT_MS)

        params.forEach { (k, v) -> conn.data(k, v) }

        val body = conn.execute().body()
        val elapsed = System.currentTimeMillis() - start
        AppLogger.d("MOODLE_API", "RES $wsFunction -> ${elapsed}мс (${body.length} симв.)")
        return body
    }

    private fun throwIfMoodleError(json: JSONObject) {
        if (json.has("exception") || json.has("errorcode")) {
            val errorCode = json.optCleanString("errorcode")
            val message = json.optCleanString("message")
                .ifBlank { json.optCleanString("error") }
                .ifBlank { "Ошибка СДО Moodle ($errorCode)" }
            if (errorCode == "invalidtoken") {
                throw InvalidMoodleTokenException("Сессия СДО истекла. Выполните вход повторно.")
            }
            throw IllegalStateException(message)
        }
    }

    private fun appendToken(rawUrl: String, token: String): String {
        if (rawUrl.contains("token=")) return rawUrl
        val sep = if (rawUrl.contains("?")) "&" else "?"
        return "$rawUrl${sep}token=${URLEncoder.encode(token, "UTF-8")}"
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        return Jsoup.parse(html).text().replace("\u00A0", " ").trim()
    }

    private fun JSONObject.optCleanString(key: String): String {
        if (isNull(key)) return ""
        return optString(key, "").trim()
    }
}

class InvalidMoodleTokenException(message: String) : IllegalStateException(message)
