package com.mguuschedule.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mguuschedule.model.EducationLevel
import com.mguuschedule.model.Group
import com.mguuschedule.model.LessonEntity
import com.mguuschedule.util.AppLogger
import com.mguuschedule.util.ScheduleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

data class ScheduleSyncResult(
    val lessons: List<LessonEntity>,
    val fromNetwork: Boolean,
    val errorMessage: String? = null
)

class ScheduleRepository(private val context: Context, private val database: AppDatabase) {
    private val dao: ScheduleDao = database.scheduleDao()
    private val addonsDao: LessonAddonsDao = database.lessonAddonsDao()
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val cachePrefs = context.getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)

    companion object {
        const val SCHEDULE_API_BASE = "https://schedule.mguu.ru/api/v1/schedule"
        private const val SCHEDULE_ORIGIN = "https://schedule.mguu.ru"
        private const val SCHEDULE_REFERER = "https://schedule.mguu.ru/schedule"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        private const val KEY_GROUPS_CACHE = "all_groups_cache_json"
        private const val KEY_GROUPS_CACHE_TS = "all_groups_cache_ts"
        private const val KEY_CACHED_GROUP_ID = "cached_schedule_group_id"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"

        private val gson = Gson()
        private val GROUPS_TYPE = object : TypeToken<List<Group>>() {}.type
        private val SYNC_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
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

    private fun executeGetJson(url: String, timeoutMs: Int = 12_000): String {
        val start = System.currentTimeMillis()
        AppLogger.d("API_REQ", "GET $url")

        val jsonBody = Jsoup.connect(url)
            .sslSocketFactory(TRUST_ALL_SSL_FACTORY)
            .ignoreContentType(true)
            .userAgent(USER_AGENT)
            .header("Accept", "application/json")
            .header("Origin", SCHEDULE_ORIGIN)
            .referrer(SCHEDULE_REFERER)
            .timeout(timeoutMs)
            .execute()
            .body()

        val duration = System.currentTimeMillis() - start
        AppLogger.d("API_RES", "GET $url -> ${duration}мс (${jsonBody.length} символов)")
        return jsonBody
    }

    /**
     * Возвращает кэшированный список всех групп (для мгновенного открытия и работы офлайн).
     */
    suspend fun getCachedGroups(): List<Group> = withContext(Dispatchers.IO) {
        val raw = cachePrefs.getString(KEY_GROUPS_CACHE, null) ?: return@withContext emptyList()
        runCatching { gson.fromJson<List<Group>>(raw, GROUPS_TYPE) }.getOrDefault(emptyList())
    }

    private fun saveCachedGroups(groups: List<Group>) {
        if (groups.isEmpty()) return
        cachePrefs.edit()
            .putString(KEY_GROUPS_CACHE, gson.toJson(groups))
            .putLong(KEY_GROUPS_CACHE_TS, System.currentTimeMillis())
            .apply()
    }

    fun getCachedGroupId(): String? = cachePrefs.getString(KEY_CACHED_GROUP_ID, null)

    suspend fun getAllCachedLessons(): List<LessonEntity> = withContext(Dispatchers.IO) {
        dao.getAllLessons()
    }

    /**
     * Загружает расписание напрямую с https://schedule.mguu.ru/api/v1/schedule без записи в Room.
     */
    suspend fun fetchRemoteLessons(
        groupId: String,
        startDate: String = "",
        endDate: String = ""
    ): List<LessonEntity> = withContext(Dispatchers.IO) {
        val (defaultStart, defaultEnd) = ScheduleParser.buildAcademicYearDateRange()
        val resolvedStart = startDate.ifBlank { defaultStart }
        val resolvedEnd = endDate.ifBlank { defaultEnd }
        val encodedGroup = URLEncoder.encode(groupId.trim(), "UTF-8")
        val apiUrl = "$SCHEDULE_API_BASE/groups/$encodedGroup/lessons/?start_date=$resolvedStart&end_date=$resolvedEnd"

        Log.d("API_PARSER", "Запрос к schedule.mguu.ru: $apiUrl")
        AppLogger.d("API_PARSER", "Запрос к schedule.mguu.ru: $apiUrl")

        val jsonBody = executeGetJson(apiUrl)
        ScheduleParser.parse(jsonBody)
    }

    /**
     * Загружает список всех групп (бакалавриат, магистратура, аспирантура) с https://schedule.mguu.ru
     * и сохраняет результат в локальный кэш. При сбое сети возвращает сохранённый кэш.
     */
    suspend fun fetchAllGroups(): List<Group> = withContext(Dispatchers.IO) {
        val levels = listOf(
            "bak" to EducationLevel.BACHELOR,
            "mag" to EducationLevel.MASTER,
            "asp" to EducationLevel.MASTER
        )
        val fresh = coroutineScope {
            levels.map { (code, level) ->
                async {
                    runCatching {
                        val url = "$SCHEDULE_API_BASE/levels/$code/groups/"
                        val json = executeGetJson(url, timeoutMs = 10_000)
                        ScheduleParser.parseGroupsForLevel(json, fallbackLevel = level)
                    }.onFailure { e ->
                        AppLogger.w("API_PARSER", "Не удалось загрузить группы уровня $code: ${e.message}")
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }

        if (fresh.isNotEmpty()) {
            saveCachedGroups(fresh)
            fresh
        } else {
            getCachedGroups()
        }
    }

    /**
     * Синхронизирует расписание с портала с честным флагом источника ([ScheduleSyncResult.fromNetwork]).
     * При ошибке сети сохраняет и возвращает локальный кэш Room, не затирая его пустым ответом.
     */
    suspend fun syncSchedule(
        groupId: String = "000000311",
        startDate: String = "",
        endDate: String = ""
    ): ScheduleSyncResult = withContext(Dispatchers.IO) {
        var fetchError: String? = null
        val remoteLessons = runCatching {
            fetchRemoteLessons(groupId = groupId, startDate = startDate, endDate = endDate)
        }.onFailure { e ->
            fetchError = e.message ?: "Ошибка сети"
            AppLogger.e("API_PARSER", "Ошибка загрузки расписания с schedule.mguu.ru: ${e.message}", e)
        }.getOrNull()

        if (!remoteLessons.isNullOrEmpty()) {
            dao.replaceAll(remoteLessons)

            val nowMs = System.currentTimeMillis()
            val nowStr = LocalDateTime.now().format(SYNC_TIME_FORMATTER)
            prefs.edit().putString("last_sync_time", nowStr).apply()
            // Синхронизируем снэпшот с ScheduleUpdateWorker и метаданными группы
            cachePrefs.edit()
                .putString("cache_$groupId", gson.toJson(remoteLessons))
                .putString(KEY_CACHED_GROUP_ID, groupId)
                .putLong(KEY_LAST_UPDATE_TIME, nowMs)
                .apply()

            AppLogger.d("API_PARSER", "Обновлено расписание в БД и кэше: ${remoteLessons.size} пар")
            ScheduleSyncResult(lessons = remoteLessons, fromNetwork = true)
        } else {
            val cachedLessons = dao.getAllLessons()
            val cachedForGroup = getCachedGroupId()
            val canUseCache = cachedLessons.isNotEmpty() && (cachedForGroup == null || cachedForGroup == groupId)
            if (canUseCache) {
                AppLogger.d("API_PARSER", "Сеть недоступна или пустой ответ, возвращаем локальный кэш: ${cachedLessons.size} пар")
                ScheduleSyncResult(
                    lessons = cachedLessons,
                    fromNetwork = false,
                    errorMessage = fetchError ?: "Пустой ответ сервера"
                )
            } else {
                ScheduleSyncResult(
                    lessons = emptyList(),
                    fromNetwork = remoteLessons != null && fetchError == null,
                    errorMessage = fetchError
                )
            }
        }
    }

    suspend fun fetchScheduleFromPortal(
        groupId: String = "000000311",
        groupName: String = "",
        startDate: String = "",
        endDate: String = ""
    ): List<LessonEntity> = syncSchedule(
        groupId = groupId,
        startDate = startDate,
        endDate = endDate
    ).lessons

    suspend fun fetchSchedule(
        groupId: String,
        groupName: String,
        daysCount: Int
    ): Result<List<LessonEntity>> = try {
        val sync = syncSchedule(groupId = groupId)
        if (sync.fromNetwork || sync.lessons.isNotEmpty()) {
            Result.success(sync.lessons)
        } else {
            Result.failure(Exception(sync.errorMessage ?: "Не удалось загрузить расписание"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun clearDatabase() = withContext(Dispatchers.IO) {
        AppLogger.w("CACHE", "Локальный кэш расписания Room очищен")
        dao.clearSchedule()
        cachePrefs.edit()
            .remove(KEY_CACHED_GROUP_ID)
            .remove(KEY_LAST_UPDATE_TIME)
            .apply()
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

    fun getLessonAddonSummariesFlow(): Flow<Map<String, LessonAddonSummary>> = combine(
        addonsDao.getAllNotesFlow(),
        addonsDao.getAllTasksFlow(),
        addonsDao.getAllMaterialsFlow()
    ) { notes, tasks, materials ->
        val notesMap = notes.associateBy { it.lessonKey }
        val tasksMap = tasks.groupBy { it.lessonKey }
        val materialsMap = materials.groupBy { it.lessonKey }

        val allKeys = notesMap.keys + tasksMap.keys + materialsMap.keys
        allKeys.associateWith { key ->
            LessonAddonSummary(
                lessonKey = key,
                noteText = notesMap[key]?.text,
                tasks = tasksMap[key] ?: emptyList(),
                materials = materialsMap[key] ?: emptyList()
            )
        }
    }
}
