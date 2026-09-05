package com.mguuschedule.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mguuschedule.model.Lesson
import com.mguuschedule.model.toLesson
import com.mguuschedule.repository.AppDatabase
import com.mguuschedule.repository.ScheduleRepository
import com.mguuschedule.repository.WeatherData
import com.mguuschedule.repository.WeatherRepository
import com.mguuschedule.util.AppLogger
import com.mguuschedule.util.LiveUpdateManager
import com.mguuschedule.util.NetworkMonitor
import com.mguuschedule.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class ScheduleUiState {
    object Loading : ScheduleUiState()
    data class Success(val lessons: List<Lesson>) : ScheduleUiState()
    data class Error(val message: String) : ScheduleUiState()
}

class ScheduleViewModel(
    private val scheduleRepository: ScheduleRepository,
    application: Application
) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
    private val appPrefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val lessonsForSelectedDay: StateFlow<List<Lesson>> = _selectedDate
        .flatMapLatest { date ->
            scheduleRepository.getLessonsByDateFlow(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        }
        .map { entities -> entities.map { it.toLesson() }.sortedBy { it.startTime } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    var uiState: ScheduleUiState by mutableStateOf(ScheduleUiState.Loading)
        private set

    var lastUpdateTime by mutableStateOf(prefs.getLong("last_update_time", 0L))
        private set

    var isForcedLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var hasChangesInLastRefresh by mutableStateOf(false)
        private set

    val lessons: List<Lesson> get() = lessonsForSelectedDay.value
    private var currentGroupId: String? = null

    private val networkMonitor = NetworkMonitor(application)
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val weatherRepository = WeatherRepository(application)
    var weatherData by mutableStateOf<WeatherData?>(null)
        private set

    init {
        fetchWeather()
        AppLogger.d("SCHEDULE_TRACE", ">>> ScheduleViewModel инициализирована")
        viewModelScope.launch(Dispatchers.IO) {
            val savedGroupId = appPrefs.getString("selected_group_id", null)
            if (savedGroupId != null) {
                AppLogger.d("SCHEDULE_TRACE", ">>> Найден сохраненный groupId: $savedGroupId, запуск loadSchedule()")
                withContext(Dispatchers.Main) {
                    loadSchedule(savedGroupId)
                }
            }
            scheduleUpcomingRemindersFromDb()
        }

        viewModelScope.launch {
            lessonsForSelectedDay.collect { lessons ->
                if (_selectedDate.value.isEqual(LocalDate.now())) {
                    LiveUpdateManager.scheduleLiveUpdatesForDay(getApplication(), lessons)
                }
            }
        }
    }

    private fun fetchWeather() {
        viewModelScope.launch {
            weatherData = weatherRepository.getWeatherData()
        }
    }

    val cacheMetadata by derivedStateOf {
        val currentLessons = lessonsForSelectedDay.value
        if (currentLessons.isEmpty()) {
            Triple("Локальный кэш пуст", "0 КБ", "Данные не загружены")
        } else {
            val range = "${currentLessons.first().date.dayOfMonth} - ${currentLessons.last().date.dayOfMonth}"
            val updateTime = appPrefs.getString("last_sync_time", "Данные не загружены") ?: "Данные не загружены"
            Triple("Синхронизировано (${currentLessons.size} пар)", range, updateTime)
        }
    }

    fun loadSchedule(groupId: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && currentGroupId == groupId) return
        
        currentGroupId = groupId
        refreshSchedule()
    }

    fun refreshSchedule() {
        val groupId = currentGroupId ?: return
        val groupName = appPrefs.getString("selected_group_name", "") ?: ""
        val cacheDaysCount = appPrefs.getInt("cache_days_count", 30)

        viewModelScope.launch {
            isRefreshing = true
            isForcedLoading = lessonsForSelectedDay.value.isEmpty()
            
            AppLogger.d("SCHEDULE_TRACE", ">>> 1. Старт корутины загрузки...")
            
            val result = scheduleRepository.fetchSchedule(groupId, groupName, cacheDaysCount)
            
            result.onSuccess { entities ->
                val newLessons = entities.map { it.toLesson() }
                hasChangesInLastRefresh = if (lessonsForSelectedDay.value.isEmpty()) false else lessonsForSelectedDay.value != newLessons
                
                uiState = ScheduleUiState.Success(newLessons)
                scheduleUpcomingRemindersFromDb()
                AppLogger.d("SCHEDULE_TRACE", ">>> 3. Успешно загружено пар: ${newLessons.size}")
            }.onFailure { e ->
                AppLogger.e("SCHEDULE_TRACE", "ОШИБКА ЗАГРУЗКИ: ${e.message}", e)
                if (lessonsForSelectedDay.value.isEmpty()) {
                    uiState = ScheduleUiState.Error(e.message ?: "Ошибка подключения")
                }
            }

            isForcedLoading = false
            isRefreshing = false
            AppLogger.d("SCHEDULE_TRACE", ">>> 4. isRefreshing сброшен в false")
        }
    }

    fun scheduleUpcomingRemindersFromDb() {
        val enabled = appPrefs.getBoolean("reminders_enabled", true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val todayStr = LocalDate.now().toString()
                val upcomingEntities = scheduleRepository.getUpcomingLessons(todayStr)
                val minutesBefore = appPrefs.getInt("reminder_time", 15)
                val upcomingLessons = upcomingEntities.map { it.toLesson() }
                
                if (enabled) {
                    upcomingLessons.forEach { lesson ->
                        NotificationHelper.scheduleClassReminder(getApplication(), lesson, minutesBefore)
                    }
                }
                
                val todayLessons = upcomingLessons.filter { it.date.isEqual(LocalDate.now()) }
                LiveUpdateManager.scheduleLiveUpdatesForDay(getApplication(), todayLessons)
                
                AppLogger.d("SCHEDULE_TRACE", "Запланированы напоминания и Live Updates для ${upcomingLessons.size} будущих пар из БД")
            } catch (e: Exception) {
                AppLogger.e("SCHEDULE_TRACE", "Ошибка автопланирования напоминаний: ${e.message}", e)
            }
        }
    }

    fun clearCache() {
        currentGroupId?.let { _ ->
            viewModelScope.launch(Dispatchers.IO) {
                scheduleRepository.clearDatabase()
                withContext(Dispatchers.Main) {
                    uiState = ScheduleUiState.Loading
                }
            }
        }
    }

    fun forceUpdate() {
        refreshSchedule()
    }

    fun reloadSchedule() {
        refreshSchedule()
    }

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
    }

    fun resetToToday() {
        _selectedDate.value = LocalDate.now()
    }

    fun getLessonById(id: String): Lesson? {
        return lessonsForSelectedDay.value.find { it.id == id }
    }
}

class ScheduleViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            val database = AppDatabase.getDatabase(application)
            val repository = ScheduleRepository(application, database.scheduleDao())
            return ScheduleViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
