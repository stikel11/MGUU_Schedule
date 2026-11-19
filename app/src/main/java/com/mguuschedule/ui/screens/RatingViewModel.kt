package com.mguuschedule.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mguuschedule.model.AcademicPeriod
import com.mguuschedule.model.ControlPoint
import com.mguuschedule.model.StudentRating
import com.mguuschedule.model.SubjectScore
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.repository.RatingEntity
import com.mguuschedule.repository.RatingRepository
import com.mguuschedule.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed class RatingUiState {
    object Loading : RatingUiState()
    data class Success(
        val student: StudentRating?,
        val subjects: List<SubjectScore>,
        val studentsList: List<StudentRating>,
        val availableYears: List<AcademicPeriod> = emptyList(),
        val availableSemesters: List<AcademicPeriod> = emptyList()
    ) : RatingUiState()
    data class Error(val message: String) : RatingUiState()
}

class RatingViewModel(
    private val ratingRepository: RatingRepository,
    application: Application
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        private val gson = Gson()
        private val STUDENT_TYPE = object : TypeToken<StudentRating>() {}.type
        private val SUBJECTS_TYPE = object : TypeToken<List<SubjectScore>>() {}.type
        private val STUDENTS_LIST_TYPE = object : TypeToken<List<StudentRating>>() {}.type
        private val PERIODS_TYPE = object : TypeToken<List<AcademicPeriod>>() {}.type
        private val POINTS_MAP_TYPE = object : TypeToken<Map<String, List<ControlPoint>>>() {}.type
    }

    private data class ParsedRatingCache(
        val student: StudentRating?,
        val subjects: List<SubjectScore>,
        val studentsList: List<StudentRating>,
        val years: List<AcademicPeriod>,
        val semesters: List<AcademicPeriod>,
        val pointsMap: Map<String, List<ControlPoint>>,
        val resolvedYearId: String?,
        val resolvedSemId: String?
    )

    private fun parseCachedEntity(cachedEntity: RatingEntity, savedZachetka: String): ParsedRatingCache? {
        return runCatching {
            val cachedStudentsList: List<StudentRating> =
                gson.fromJson(cachedEntity.studentsListJson, STUDENTS_LIST_TYPE) ?: emptyList()
            val cachedStudent = if (savedZachetka.isNotBlank() && cachedStudentsList.isNotEmpty()) {
                cachedStudentsList.find { it.zachetka.equals(savedZachetka, ignoreCase = true) }
            } else {
                gson.fromJson(cachedEntity.studentJson, STUDENT_TYPE)
            }
            val cachedSubjects: List<SubjectScore> =
                gson.fromJson(cachedEntity.subjectsJson, SUBJECTS_TYPE) ?: emptyList()
            if (cachedStudent == null && cachedSubjects.isEmpty()) return@runCatching null

            val cachedPointsMap: Map<String, List<ControlPoint>> = runCatching {
                gson.fromJson<Map<String, List<ControlPoint>>>(cachedEntity.controlPointsJson, POINTS_MAP_TYPE)
            }.getOrDefault(emptyMap())
            val cachedYears: List<AcademicPeriod> = runCatching {
                gson.fromJson<List<AcademicPeriod>>(cachedEntity.availableYearsJson, PERIODS_TYPE)
            }.getOrDefault(emptyList())
            val cachedSemesters: List<AcademicPeriod> = runCatching {
                gson.fromJson<List<AcademicPeriod>>(cachedEntity.availableSemestersJson, PERIODS_TYPE)
            }.getOrDefault(emptyList())

            val resolvedYearId = cachedEntity.yearId.ifBlank { null }
            val resolvedSemId = cachedEntity.semId.ifBlank { null }
            val updatedYears = cachedYears.map { it.copy(isSelected = it.id == resolvedYearId) }
            val updatedSemesters = cachedSemesters.map { it.copy(isSelected = it.id == resolvedSemId) }

            ParsedRatingCache(
                student = cachedStudent,
                subjects = cachedSubjects,
                studentsList = cachedStudentsList,
                years = updatedYears,
                semesters = updatedSemesters,
                pointsMap = cachedPointsMap,
                resolvedYearId = resolvedYearId,
                resolvedSemId = resolvedSemId
            )
        }.getOrNull()
    }

    private fun applyParsedCache(parsed: ParsedRatingCache, savedZachetka: String) {
        _subjectControlPoints.value = parsed.pointsMap
        selectedYearId = parsed.resolvedYearId
        selectedSemId = parsed.resolvedSemId
        if (savedZachetka.isBlank() && parsed.student != null) {
            prefs.edit().putString("selected_zachetka", parsed.student.zachetka).apply()
        }
        uiState = RatingUiState.Success(
            student = parsed.student,
            subjects = parsed.subjects,
            studentsList = parsed.studentsList,
            availableYears = parsed.years,
            availableSemesters = parsed.semesters
        )
    }

    private val _scrollToTopTrigger = MutableStateFlow(0)
    val scrollToTopTrigger = _scrollToTopTrigger.asStateFlow()

    fun scrollToTop() {
        _scrollToTopTrigger.value += 1
    }

    var uiState by mutableStateOf<RatingUiState>(RatingUiState.Loading)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var refreshStatusText by mutableStateOf("Данные обновлены")
        private set

    var refreshIsError by mutableStateOf(false)
        private set

    var selectedYearId by mutableStateOf<String?>(null)
        private set

    var selectedSemId by mutableStateOf<String?>(null)
        private set

    private val _subjectControlPoints = MutableStateFlow<Map<String, List<ControlPoint>>>(emptyMap())
    val subjectControlPoints: StateFlow<Map<String, List<ControlPoint>>> = _subjectControlPoints.asStateFlow()

    private val _loadingControlPoints = MutableStateFlow<Set<String>>(emptySet())
    val loadingControlPoints: StateFlow<Set<String>> = _loadingControlPoints.asStateFlow()

    init {
        restoreCacheAsync()
    }

    private fun restoreCacheAsync() {
        viewModelScope.launch {
            val groupId = prefs.getString("selected_group_id", "000000283") ?: "000000283"
            val savedZachetka = prefs.getString("selected_zachetka", "") ?: ""

            val parsed = withContext(Dispatchers.Default) {
                val instantCacheJson = prefs.getString("rating_latest_instant_cache_$groupId", null)
                    ?: return@withContext null
                val cachedEntity = runCatching {
                    gson.fromJson(instantCacheJson, RatingEntity::class.java)
                }.getOrNull() ?: return@withContext null
                parseCachedEntity(cachedEntity, savedZachetka)
            }

            var needsLegacyMigrationRefresh = false
            if (parsed != null) {
                applyParsedCache(parsed, savedZachetka)
                AppLogger.d("RATING", "Отображен кэш БРС для ${parsed.student?.zachetka}")
                val cachedUrl = parsed.student?.detailUrl.orEmpty()
                if (cachedUrl.isNotBlank() && !cachedUrl.startsWith(RatingRepository.RATING_API_BASE)) {
                    needsLegacyMigrationRefresh = true
                }
            }

            // Asynchronously check Room for newer/other period cache or refresh legacy cache
            loadRating(forceRefresh = needsLegacyMigrationRefresh)
        }
    }

    fun loadRating(
        forceRefresh: Boolean = false,
        yearId: String? = selectedYearId,
        semId: String? = selectedSemId
    ) {
        val groupId = prefs.getString("selected_group_id", "000000283") ?: "000000283"
        val groupName = prefs.getString("selected_group_name", "25М-УГКП21") ?: "25М-УГКП21"
        val savedZachetka = prefs.getString("selected_zachetka", "") ?: ""

        viewModelScope.launch {
            if (!forceRefresh) {
                // Строгий Cache-First (парсинг JSON выполняется в Dispatchers.Default)
                val cachedEntity = ratingRepository.getCachedOrLatestRating(groupId, savedZachetka, yearId, semId)
                if (cachedEntity != null) {
                    val parsed = withContext(Dispatchers.Default) {
                        parseCachedEntity(cachedEntity, savedZachetka)
                    }
                    if (parsed != null) {
                        applyParsedCache(parsed, savedZachetka)
                        isRefreshing = false
                        AppLogger.d("RATING", "Отображен строгий кэш БРС для ${parsed.student?.zachetka}")
                        return@launch
                    }
                }
            }

            isRefreshing = true
            refreshStatusText = "Данные обновлены"
            refreshIsError = false

            try {
                val groupUrl = ratingRepository.buildGroupUrl(groupId, groupName, yearId, semId)
                AppLogger.d("RATING", "Запрос рейтинга группы: $groupUrl")

                val ratingPage = withTimeoutOrNull(10000L) {
                    ratingRepository.parseGroupList(groupUrl)
                }

                if (ratingPage == null) {
                    AppLogger.e("RATING", "ТАЙМАУТ СЕТЕВОГО ЗАПРОСА РЕЙТИНГА (10с)")
                    refreshStatusText = "Ошибка обновления, показаны кэшированные данные"
                    refreshIsError = true
                    isRefreshing = false
                    if (uiState !is RatingUiState.Success) {
                        uiState = RatingUiState.Error("Превышено время ожидания сети (10с)")
                    }
                    return@launch
                }

                val students = ratingPage.students

                val activeYearId = yearId ?: ratingPage.availableYears.find { it.isSelected }?.id ?: selectedYearId
                val activeSemId = semId ?: ratingPage.availableSemesters.find { it.isSelected }?.id ?: selectedSemId

                selectedYearId = activeYearId
                selectedSemId = activeSemId

                val previousSuccess = uiState as? RatingUiState.Success

                val years = if (ratingPage.availableYears.isNotEmpty()) {
                    ratingPage.availableYears.map { it.copy(isSelected = it.id == activeYearId) }
                } else {
                    previousSuccess?.availableYears?.map { it.copy(isSelected = it.id == activeYearId) } ?: emptyList()
                }

                val semesters = if (ratingPage.availableSemesters.isNotEmpty()) {
                    ratingPage.availableSemesters.map { it.copy(isSelected = it.id == activeSemId) }
                } else {
                    previousSuccess?.availableSemesters?.map { it.copy(isSelected = it.id == activeSemId) } ?: emptyList()
                }

                if (students.isEmpty()) {
                    if (forceRefresh && previousSuccess?.student != null && yearId == selectedYearId && semId == selectedSemId) {
                        isRefreshing = false
                        return@launch
                    }
                    if (forceRefresh && (yearId != null || semId != null)) {
                        // Fallback: try without parameters
                        val fallbackUrl = ratingRepository.buildGroupUrl(groupId, groupName, null, null)
                        val fallbackPage = ratingRepository.parseGroupList(fallbackUrl)
                        if (fallbackPage.students.isNotEmpty()) {
                            val activeFallbackYearId = fallbackPage.availableYears.find { it.isSelected }?.id
                            val activeFallbackSemId = fallbackPage.availableSemesters.find { it.isSelected }?.id
                            selectedYearId = activeFallbackYearId
                            selectedSemId = activeFallbackSemId

                            val fallbackSelectedStudent = if (savedZachetka.isNotBlank()) {
                                fallbackPage.students.find { it.zachetka.equals(savedZachetka, ignoreCase = true) } ?: fallbackPage.students.first()
                            } else {
                                fallbackPage.students.first()
                            }

                            prefs.edit().putString("selected_zachetka", fallbackSelectedStudent.zachetka).apply()

                            val fallbackSubjects = ratingRepository.parseStudentSubjects(fallbackSelectedStudent.detailUrl)
                            val fallbackCache = ratingRepository.getCachedOrLatestRating(groupId, fallbackSelectedStudent.zachetka, activeFallbackYearId ?: "", activeFallbackSemId ?: "")

                            val fallbackPointsMap = if (fallbackCache != null) {
                                withContext(Dispatchers.Default) {
                                    runCatching {
                                        gson.fromJson<Map<String, List<ControlPoint>>>(fallbackCache.controlPointsJson, POINTS_MAP_TYPE)
                                    }.getOrDefault(emptyMap())
                                }
                            } else emptyMap()

                            _subjectControlPoints.value = fallbackPointsMap

                            val cacheEntity = withContext(Dispatchers.Default) {
                                RatingEntity(
                                    groupId = groupId,
                                    zachetka = fallbackSelectedStudent.zachetka,
                                    yearId = activeFallbackYearId ?: "",
                                    semId = activeFallbackSemId ?: "",
                                    studentJson = gson.toJson(fallbackSelectedStudent),
                                    subjectsJson = gson.toJson(fallbackSubjects),
                                    studentsListJson = gson.toJson(fallbackPage.students),
                                    availableYearsJson = gson.toJson(fallbackPage.availableYears),
                                    availableSemestersJson = gson.toJson(fallbackPage.availableSemesters),
                                    controlPointsJson = gson.toJson(fallbackPointsMap),
                                    updatedAt = System.currentTimeMillis()
                                )
                            }
                            ratingRepository.saveCachedRating(cacheEntity)

                            uiState = RatingUiState.Success(
                                student = fallbackSelectedStudent,
                                subjects = fallbackSubjects,
                                studentsList = fallbackPage.students,
                                availableYears = fallbackPage.availableYears,
                                availableSemesters = fallbackPage.availableSemesters
                            )
                            isRefreshing = false
                            return@launch
                        }
                    }

                    uiState = RatingUiState.Success(
                        student = null,
                        subjects = emptyList(),
                        studentsList = emptyList(),
                        availableYears = years,
                        availableSemesters = semesters
                    )
                    isRefreshing = false
                    return@launch
                }

                val selectedStudent = if (savedZachetka.isNotBlank()) {
                    students.find { it.zachetka.equals(savedZachetka, ignoreCase = true) } ?: students.first()
                } else {
                    students.first()
                }

                prefs.edit().putString("selected_zachetka", selectedStudent.zachetka).apply()

                AppLogger.d("RATING", "Запрос рейтинга студента: ${selectedStudent.zachetka} -> ${selectedStudent.detailUrl}")
                val subjects = ratingRepository.parseStudentSubjects(selectedStudent.detailUrl)

                val previousCache = ratingRepository.getCachedOrLatestRating(groupId, selectedStudent.zachetka, activeYearId ?: "", activeSemId ?: "")
                val mergedPointsMap = if (previousCache != null) {
                    withContext(Dispatchers.Default) {
                        runCatching {
                            gson.fromJson<Map<String, List<ControlPoint>>>(previousCache.controlPointsJson, POINTS_MAP_TYPE)
                        }.getOrDefault(emptyMap())
                    }
                } else emptyMap()

                // Параллельная загрузка контрольных точек для всех предметов
                val fetchedPointsPairs = coroutineScope {
                    subjects.filter { it.detailUrl.isNotBlank() }.map { subj ->
                        async {
                            val pts = runCatching { ratingRepository.parseControlPoints(subj.detailUrl) }.getOrDefault(emptyList())
                            subj.detailUrl to pts
                        }
                    }.awaitAll()
                }
                
                val finalPointsMap = mergedPointsMap.toMutableMap()
                fetchedPointsPairs.forEach { (url, points) ->
                    if (points.isNotEmpty()) {
                        finalPointsMap[url] = points
                    }
                }

                _subjectControlPoints.value = finalPointsMap

                // Сериализация кэша выполняется вне Main-потока
                val (cacheEntity, instantCacheJson) = withContext(Dispatchers.Default) {
                    val entity = RatingEntity(
                        groupId = groupId,
                        zachetka = selectedStudent.zachetka,
                        yearId = activeYearId ?: "",
                        semId = activeSemId ?: "",
                        studentJson = gson.toJson(selectedStudent),
                        subjectsJson = gson.toJson(subjects),
                        studentsListJson = gson.toJson(students),
                        availableYearsJson = gson.toJson(years),
                        availableSemestersJson = gson.toJson(semesters),
                        controlPointsJson = gson.toJson(finalPointsMap),
                        updatedAt = System.currentTimeMillis()
                    )
                    entity to gson.toJson(entity)
                }
                ratingRepository.saveCachedRating(cacheEntity)
                
                // Сохраняем кэш в prefs асинхронно
                prefs.edit()
                    .putString("rating_latest_instant_cache_$groupId", instantCacheJson)
                    .apply()

                uiState = RatingUiState.Success(
                    student = selectedStudent,
                    subjects = subjects,
                    studentsList = students,
                    availableYears = years,
                    availableSemesters = semesters
                )
            } catch (e: Exception) {
                AppLogger.e("RATING", "Ошибка загрузки рейтинга: ${e.message}", e)
                val previousSuccess = uiState as? RatingUiState.Success
                if (previousSuccess?.student == null) {
                    uiState = RatingUiState.Error(e.message ?: "Ошибка подключения к порталу БРС")
                }
            }

            isRefreshing = false
        }
    }

    fun selectStudent(student: StudentRating) {
        prefs.edit().putString("selected_zachetka", student.zachetka).apply()
        loadRating(forceRefresh = false)
    }

    fun selectPeriod(yearId: String, semId: String) {
        selectedYearId = yearId
        selectedSemId = semId
        loadRating(forceRefresh = false, yearId = yearId, semId = semId)
    }

    fun fetchControlPointsForSubject(subject: SubjectScore) {
        val detailUrl = subject.detailUrl
        if (detailUrl.isBlank()) return
        if (_subjectControlPoints.value.containsKey(detailUrl)) return

        viewModelScope.launch {
            _loadingControlPoints.value = _loadingControlPoints.value + detailUrl
            try {
                val points = ratingRepository.parseControlPoints(detailUrl)
                val newMap = _subjectControlPoints.value + (detailUrl to points)
                _subjectControlPoints.value = newMap
                
                val groupId = prefs.getString("selected_group_id", "000000283") ?: "000000283"
                val zachetka = prefs.getString("selected_zachetka", "") ?: ""
                val cachedEntity = ratingRepository.getCachedOrLatestRating(groupId, zachetka, selectedYearId, selectedSemId)
                if (cachedEntity != null) {
                    val serializedPoints = withContext(Dispatchers.Default) { gson.toJson(newMap) }
                    val updatedEntity = cachedEntity.copy(
                        controlPointsJson = serializedPoints,
                        updatedAt = System.currentTimeMillis()
                    )
                    ratingRepository.saveCachedRating(updatedEntity)
                }
            } catch(e: Exception) {
                AppLogger.e("RATING", "Ошибка загрузки КТ: ${e.message}")
            } finally {
                _loadingControlPoints.value = _loadingControlPoints.value - detailUrl
            }
        }
    }
}

class RatingViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.getDatabase(application)
        val repo = RatingRepository(database.ratingDao())
        return RatingViewModel(repo, application) as T
    }
}
