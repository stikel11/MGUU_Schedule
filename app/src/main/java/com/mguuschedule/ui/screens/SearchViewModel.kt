package com.mguuschedule.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mguuschedule.model.CampusTeacher
import com.mguuschedule.model.ControlPoint
import com.mguuschedule.model.Lesson
import com.mguuschedule.model.toLesson
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.repository.ScheduleRepository
import com.mguuschedule.repository.TeacherRepository
import com.mguuschedule.ui.screens.getFloorImageResId
import com.mguuschedule.util.SearchEngine
import com.mguuschedule.util.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class SearchUiState(
    val query: String = "",
    val selectedFilter: String = "Все",
    val isSearching: Boolean = false,
    val results: List<SearchResultItem> = emptyList()
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    application: Application,
    private val scheduleRepository: ScheduleRepository = ScheduleRepository(application, AppDatabase.getDatabase(application)),
    private val teacherRepository: TeacherRepository = TeacherRepository(application)
) : AndroidViewModel(application) {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _selectedFilter = MutableStateFlow("Все")
    val selectedFilter = _selectedFilter.asStateFlow()

    companion object {
        private val gson = Gson()
        private val FLOOR_DIGIT_REGEX = Regex("([1-5])\\d{2}")
        private val CONTROL_POINTS_MAP_TYPE = object : TypeToken<Map<String, List<ControlPoint>>>() {}.type
    }

    // Преобразование и дедупликация уроков выполняются один раз при изменении БД, а не на каждый символ поиска
    private val deduplicatedLessonsFlow = scheduleRepository.getAllLessonsFlow()
        .map { entities -> SearchEngine.deduplicateLessons(entities.mapNotNull { it.toLesson() }) }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<SearchUiState> = combine(
        _query.debounce(250).distinctUntilChanged(),
        _selectedFilter,
        deduplicatedLessonsFlow
    ) { rawQuery, filter, lessons ->

        if (rawQuery.isBlank()) {
            return@combine SearchUiState(query = rawQuery, selectedFilter = filter, results = emptyList())
        }

        val normQuery = SearchEngine.normalize(rawQuery)
        val initialTokens = normQuery.split(" ").filter { it.isNotBlank() }

        if (initialTokens.isEmpty()) {
            return@combine SearchUiState(query = rawQuery, selectedFilter = filter, results = emptyList())
        }

        val results = mutableListOf<SearchResultItem>()

        // 1. Попытка детекта даты
        val (parsedDate, tokensAfterDate) = SearchEngine.parseDateFromQuery(initialTokens)

        // 2. Попытка детекта аудитории
        val (parsedRoom, tokensAfterRoom) = SearchEngine.parseRoomFromQuery(tokensAfterDate)

        // 3. Попытка детекта контрольной точки
        val (isCpIntent, tokensAfterCp) = SearchEngine.parseControlPointIntent(tokensAfterRoom)

        // 4. Попытка детекта преподавателя
        val (matchedTeacherName, remainingTokens) = SearchEngine.findTeacherMatch(tokensAfterCp, lessons)

        // ФИЛЬТРЫ ("Все", "Предмет", "Преподаватель", "Аудитория")
        when (filter) {
            "Предмет" -> {
                val subjectLessons = lessons.filter { lesson ->
                    val normTitle = SearchEngine.normalize(lesson.title)
                    initialTokens.all { token ->
                        normTitle.contains(token) || SearchEngine.isFuzzyMatch(token, normTitle)
                    }
                }
                val sorted = SearchEngine.sortLessonsByRelevanceAndDate(subjectLessons, initialTokens)
                results.addAll(sorted.map { SearchResultItem.LessonCard(it) })
            }

            "Преподаватель" -> {
                val teacherName = matchedTeacherName ?: lessons.firstOrNull { lesson ->
                    val normT = SearchEngine.normalize(lesson.teacher)
                    initialTokens.all { token -> normT.contains(token) || SearchEngine.isFuzzyMatch(token, normT) }
                }?.teacher

                if (teacherName != null) {
                    val cachedProfile = teacherRepository.getCachedTeacherProfile(teacherName)
                    results.add(SearchResultItem.TeacherCard(teacherName, cachedProfile))

                    val normTargetTeacher = SearchEngine.normalize(teacherName)
                    val teacherLessons = lessons.filter { SearchEngine.normalize(it.teacher) == normTargetTeacher }
                    val sorted = SearchEngine.sortLessonsByRelevanceAndDate(teacherLessons, initialTokens)
                    if (sorted.isNotEmpty()) {
                        results.add(SearchResultItem.SectionHeader("Занятия преподавателя"))
                        results.addAll(sorted.map { SearchResultItem.LessonCard(it) })
                    }
                }
            }

            "Аудитория" -> {
                val room = parsedRoom ?: lessons.firstOrNull { lesson ->
                    val normR = SearchEngine.normalize(lesson.room)
                    initialTokens.all { token -> normR.contains(token) }
                }?.room

                if (room != null) {
                    val floorDigitStr = FLOOR_DIGIT_REGEX.find(room)?.groupValues?.get(1) ?: ""
                    val floorResId = getFloorImageResId(room)
                    results.add(SearchResultItem.RoomCard(room, floorDigitStr, floorResId))

                    val normTargetRoom = SearchEngine.normalize(room)
                    val roomLessons = lessons.filter { SearchEngine.normalize(it.room).contains(normTargetRoom) }
                    val sorted = SearchEngine.sortLessonsByRelevanceAndDate(roomLessons, initialTokens)
                    if (sorted.isNotEmpty()) {
                        results.add(SearchResultItem.SectionHeader("Занятия в аудитории $room"))
                        results.addAll(sorted.map { SearchResultItem.LessonCard(it) })
                    }
                }
            }

            else -> { // "Все" (Автоматическое определение смысловых сущностей)
                
                // А. Преподаватель
                if (matchedTeacherName != null && !isCpIntent && parsedRoom == null) {
                    val cachedProfile = teacherRepository.getCachedTeacherProfile(matchedTeacherName)
                    results.add(SearchResultItem.TeacherCard(matchedTeacherName, cachedProfile))

                    val normMatchedTeacher = SearchEngine.normalize(matchedTeacherName)
                    var teacherLessons = lessons.filter { SearchEngine.normalize(it.teacher) == normMatchedTeacher }
                    if (parsedDate != null) {
                        teacherLessons = teacherLessons.filter { it.date.isEqual(parsedDate) }
                    }
                    val sorted = SearchEngine.sortLessonsByRelevanceAndDate(teacherLessons, remainingTokens)
                    if (sorted.isNotEmpty()) {
                        results.add(SearchResultItem.SectionHeader(if (parsedDate != null) "Занятия на $parsedDate" else "Ближайшие занятия"))
                        results.addAll(sorted.map { SearchResultItem.LessonCard(it) })
                    }
                }
                // Б. Аудитория
                else if (parsedRoom != null && !isCpIntent) {
                    val floorDigitStr = FLOOR_DIGIT_REGEX.find(parsedRoom)?.groupValues?.get(1) ?: ""
                    val floorResId = getFloorImageResId(parsedRoom)
                    results.add(SearchResultItem.RoomCard(parsedRoom, floorDigitStr, floorResId))

                    val normParsedRoom = SearchEngine.normalize(parsedRoom)
                    var roomLessons = lessons.filter { SearchEngine.normalize(it.room).contains(normParsedRoom) }
                    if (parsedDate != null) {
                        roomLessons = roomLessons.filter { it.date.isEqual(parsedDate) }
                    }
                    val sorted = SearchEngine.sortLessonsByRelevanceAndDate(roomLessons, remainingTokens)
                    if (sorted.isNotEmpty()) {
                        results.add(SearchResultItem.SectionHeader(if (parsedDate != null) "Занятия на $parsedDate" else "Занятия в аудитории"))
                        results.addAll(sorted.map { SearchResultItem.LessonCard(it) })
                    }
                }
                // В. Контрольная точка
                else if (isCpIntent) {
                    results.add(SearchResultItem.ControlPointHeader())
                    val database = AppDatabase.getDatabase(getApplication())
                    val cacheEntity = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        database.ratingDao().getLatestRatingCache()
                    }
                    
                    if (cacheEntity != null) {
                        val pointsMap: Map<String, List<ControlPoint>> = runCatching {
                            gson.fromJson<Map<String, List<ControlPoint>>>(cacheEntity.controlPointsJson, CONTROL_POINTS_MAP_TYPE)
                        }.getOrDefault(emptyMap())

                        val matchedCps = mutableListOf<SearchResultItem.ControlPointCard>()
                        pointsMap.forEach { (subjectUrl, points) ->
                            points.forEachIndexed { index, point ->
                                val normSubject = SearchEngine.normalize(subjectUrl)
                                val normPoint = SearchEngine.normalize(point.pointName)
                                
                                val matches = tokensAfterCp.isEmpty() || tokensAfterCp.all { token ->
                                    normSubject.contains(token) || normPoint.contains(token) || SearchEngine.isFuzzyMatch(token, normPoint)
                                }
                                if (matches) {
                                    matchedCps.add(
                                        SearchResultItem.ControlPointCard(
                                            uniqueId = "${subjectUrl}_${point.pointName}_${point.date}_$index",
                                            subjectName = subjectUrl.substringAfter("subject=").substringBefore("&").replace("_", " "),
                                            pointName = point.pointName,
                                            dateText = point.date,
                                            scoreText = if (point.score != null) "${point.score} б." else "—"
                                        )
                                    )
                                }
                            }
                        }
                        results.addAll(matchedCps)
                    }
                }
                // Г. Дата (Без комнаты/преподавателя/КТ)
                else if (parsedDate != null) {
                    val (dateText, dayOfWeekText) = SearchEngine.formatDateHeader(parsedDate)
                    results.add(SearchResultItem.DateHeader(parsedDate, dateText, dayOfWeekText))

                    var dateLessons = lessons.filter { it.date.isEqual(parsedDate) }
                    if (remainingTokens.isNotEmpty()) {
                        dateLessons = dateLessons.filter { lesson ->
                            val normTitle = SearchEngine.normalize(lesson.title)
                            val normTeacher = SearchEngine.normalize(lesson.teacher)
                            remainingTokens.all { token -> normTitle.contains(token) || normTeacher.contains(token) }
                        }
                    }
                    val sorted = dateLessons.sortedBy { it.startTime }
                    results.addAll(sorted.map { SearchResultItem.LessonCard(it) })
                }
                // Д. Предмет / Общий текстовый поиск
                else {
                    val matchingLessons = lessons.filter { lesson ->
                        val normTitle = SearchEngine.normalize(lesson.title)
                        val normTeacher = SearchEngine.normalize(lesson.teacher)
                        val normRoom = SearchEngine.normalize(lesson.room)
                        val normType = SearchEngine.normalize(lesson.type)

                        initialTokens.all { token ->
                            normTitle.contains(token) ||
                            normTeacher.contains(token) ||
                            normRoom.contains(token) ||
                            normType.contains(token) ||
                            SearchEngine.isFuzzyMatch(token, normTitle) ||
                            SearchEngine.isFuzzyMatch(token, normTeacher)
                        }
                    }

                    val sorted = SearchEngine.sortLessonsByRelevanceAndDate(matchingLessons, initialTokens)
                    results.addAll(sorted.map { SearchResultItem.LessonCard(it) })
                }
            }
        }

        SearchUiState(query = rawQuery, selectedFilter = filter, results = results)
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState()
    )

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun onFilterSelected(newFilter: String) {
        _selectedFilter.value = newFilter
    }
}

class SearchViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SearchViewModel(application) as T
    }
}
