package com.mguuschedule.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mguuschedule.repository.ScheduleRepository
import com.mguuschedule.util.NotificationHelper
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.mguuschedule.model.EducationLevel
import com.mguuschedule.model.Group
import com.mguuschedule.model.Lesson
import com.mguuschedule.repository.RatingRepository
import com.mguuschedule.util.LiveUpdateManager
import com.mguuschedule.util.ScheduleParser
import com.mguuschedule.worker.RatingUpdateWorker
import com.mguuschedule.worker.ScheduleUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

sealed class GroupsUiState {
    object Loading : GroupsUiState()
    data class Success(val groups: List<Group>) : GroupsUiState()
    data class Error(val message: String) : GroupsUiState()
}

sealed class ZachetkasUiState {
    object Loading : ZachetkasUiState()
    data class Success(val zachetkas: List<String>) : ZachetkasUiState()
    data class Error(val message: String) : ZachetkasUiState()
}

data class StorageUiState(
    val statusText: String = "Локальный кэш пуст",
    val lessonsCount: Int = 0,
    val sizeBytes: Long = 0L,
    val lastUpdated: String = "Данные не загружены",
    val periodDays: Int = 14
)

class ProfileViewModel(
    private val repository: ScheduleRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _scrollToTopTrigger = MutableStateFlow(0)
    val scrollToTopTrigger = _scrollToTopTrigger.asStateFlow()

    fun scrollToTop() {
        _scrollToTopTrigger.value += 1
    }
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val ratingRepository = RatingRepository()

    var groupsUiState: GroupsUiState by mutableStateOf(GroupsUiState.Loading)
        private set

    var zachetkasUiState: ZachetkasUiState by mutableStateOf(ZachetkasUiState.Loading)
        private set

    private val _periodState = MutableStateFlow(prefs.getInt("cache_days_count", 14))

    val storageState: StateFlow<StorageUiState> = combine(
        repository.getTotalLessonsCountFlow(),
        _periodState
    ) { count, period ->
        val lastUpdateText = prefs.getString("last_sync_time", "Данные не загружены") ?: "Данные не загружены"
        
        if (count == 0) {
            StorageUiState(
                statusText = "Локальный кэш пуст",
                lessonsCount = 0,
                sizeBytes = 0L,
                lastUpdated = lastUpdateText,
                periodDays = period
            )
        } else {
            StorageUiState(
                statusText = "Сохранено: $count пар",
                lessonsCount = count,
                sizeBytes = getDatabaseSizeBytes(application),
                lastUpdated = lastUpdateText,
                periodDays = period
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StorageUiState(periodDays = prefs.getInt("cache_days_count", 14))
    )
    
    var selectedGroup by mutableStateOf<Group?>(loadSavedGroup())
        private set

    var selectedZachetka by mutableStateOf(prefs.getString("selected_zachetka", "") ?: "")
        private set

    // Notification Settings
    var remindersEnabled by mutableStateOf(prefs.getBoolean("reminders_enabled", true))
        private set
    
    var reminderTimeMinutes by mutableIntStateOf(prefs.getInt("reminder_time", 15))
        private set
    
    var changesEnabled by mutableStateOf(prefs.getBoolean("changes_enabled", true))
        private set

    var liveUpdatesEnabled by mutableStateOf(prefs.getBoolean("live_updates_enabled", true))
        private set

    // Appearance Settings
    var themeMode by mutableIntStateOf(prefs.getInt("theme_mode", 0)) // 0: System, 1: Light, 2: Dark
        private set
    
    var dynamicColorEnabled by mutableStateOf(prefs.getBoolean("dynamic_color", true))
        private set

    var shareCardStyle by mutableIntStateOf(prefs.getInt("share_card_style", 0))
        private set

    fun updateShareCardStyle(style: Int) {
        shareCardStyle = style
        prefs.edit().putInt("share_card_style", style).apply()
    }

    var cacheDaysCount by mutableIntStateOf(prefs.getInt("cache_days_count", 30))
        private set

    init {
        loadGroups()
        refreshStorageStatus()
        if (changesEnabled) {
            startScheduleWorker()
        }
    }

    private fun getDatabaseSizeBytes(context: Context): Long {
        val dbFile = context.getDatabasePath("schedule_database")
        val journalFile = File(dbFile.path + "-wal")
        return (if (dbFile.exists()) dbFile.length() else 0L) +
               (if (journalFile.exists()) journalFile.length() else 0L)
    }

    private fun refreshStorageStatus() {
        // Обновляется автоматически через Flow
    }

    private fun loadSavedGroup(): Group? {
        val id = prefs.getString("selected_group_id", null)
        val name = prefs.getString("selected_group_name", null)
        val course = prefs.getString("selected_group_course", "")
        val levelStr = prefs.getString("selected_group_level", EducationLevel.BACHELOR.name)
        val level = runCatching { EducationLevel.valueOf(levelStr ?: "BACHELOR") }.getOrDefault(EducationLevel.BACHELOR)
        return if (id != null && name != null) {
            Group(id, name, course ?: "", level)
        } else null
    }

    fun loadGroups() {
        viewModelScope.launch {
            groupsUiState = GroupsUiState.Loading
            try {
                val allGroups = withContext(Dispatchers.IO) {
                    val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    
                    val urlBak = "https://portal.mguu.ru/student/scheduler1.php#bak"
                    val urlMag = "https://portal.mguu.ru/student/scheduler2.php#mag"

                    val docBak = runCatching { Jsoup.connect(urlBak).userAgent(userAgent).timeout(10000).get() }.getOrNull()
                    val docMag = runCatching { Jsoup.connect(urlMag).userAgent(userAgent).timeout(10000).get() }.getOrNull()

                    val bakList = docBak?.let { ScheduleParser.parseGroups(it.html(), EducationLevel.BACHELOR) } ?: emptyList()
                    val magList = docMag?.let { ScheduleParser.parseGroups(it.html(), EducationLevel.MASTER) } ?: emptyList()

                    bakList + magList
                }

                if (allGroups.isNotEmpty()) {
                    groupsUiState = GroupsUiState.Success(allGroups)
                } else {
                    groupsUiState = GroupsUiState.Error("Не удалось загрузить список групп")
                }
            } catch (e: Exception) {
                groupsUiState = GroupsUiState.Error(e.message ?: "Ошибка загрузки групп")
            }
        }
    }

    fun loadZachetkasForSelectedGroup() {
        val group = selectedGroup ?: return
        viewModelScope.launch {
            zachetkasUiState = ZachetkasUiState.Loading
            try {
                val groupUrl = ratingRepository.buildGroupUrl(group.id, group.name)
                val ratingPage = withContext(Dispatchers.IO) {
                    ratingRepository.parseGroupList(groupUrl)
                }
                val zachetkas = ratingPage.students.map { it.zachetka }.filter { it.isNotBlank() }
                if (zachetkas.isNotEmpty()) {
                    zachetkasUiState = ZachetkasUiState.Success(zachetkas)
                } else {
                    zachetkasUiState = ZachetkasUiState.Error("Зачетки для выбранной группы не найдены")
                }
            } catch (e: Exception) {
                zachetkasUiState = ZachetkasUiState.Error("Не удалось загрузить зачетки группы")
            }
        }
    }

    fun selectGroup(group: Group) {
        selectedGroup = group
        selectedZachetka = ""
        prefs.edit().apply {
            putString("selected_group_id", group.id)
            putString("selected_group_name", group.name)
            putString("selected_group_course", group.course)
            putString("selected_group_level", group.level.name)
            putString("selected_zachetka", "")
            commit()
        }
    }

    fun updateSelectedZachetka(zachetka: String) {
        selectedZachetka = zachetka.trim()
        prefs.edit().putString("selected_zachetka", zachetka.trim()).commit()
    }

    fun updateRemindersEnabled(enabled: Boolean) {
        remindersEnabled = enabled
        prefs.edit().putBoolean("reminders_enabled", enabled).apply()
    }

    fun updateReminderTime(minutes: Int) {
        reminderTimeMinutes = minutes
        prefs.edit().putInt("reminder_time", minutes).apply()
        if (changesEnabled) startScheduleWorker()
    }

    fun updateChangesEnabled(enabled: Boolean) {
        changesEnabled = enabled
        prefs.edit().putBoolean("changes_enabled", enabled).apply()
        if (enabled) {
            startScheduleWorker()
        } else {
            stopScheduleWorker()
        }
    }

    fun updateLiveUpdatesEnabled(enabled: Boolean) {
        liveUpdatesEnabled = enabled
        prefs.edit().putBoolean("live_updates_enabled", enabled).apply()
        if (!enabled) {
            LiveUpdateManager.cancelLiveUpdate(getApplication())
        }
    }

    fun updateThemeMode(mode: Int) {
        themeMode = mode
        prefs.edit().putInt("theme_mode", mode).apply()
    }

    fun updateDynamicColorEnabled(enabled: Boolean) {
        dynamicColorEnabled = enabled
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
    }

    fun updateCacheDaysCount(days: Int) {
        cacheDaysCount = days
        prefs.edit().putInt("cache_days_count", days).apply()
        _periodState.value = days
    }

    fun clearStorage(scheduleViewModel: ScheduleViewModel) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearDatabase()
            scheduleViewModel.clearCache()
        }
    }

    private fun startScheduleWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ScheduleUpdateWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(getApplication())
            .enqueueUniquePeriodicWork(
                "ScheduleUpdate",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

        // Автоподгрузка рейтинга БРС 3 раза в день (каждые 8 часов)
        val ratingRequest = PeriodicWorkRequestBuilder<RatingUpdateWorker>(8, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(getApplication())
            .enqueueUniquePeriodicWork(
                "RatingUpdate",
                ExistingPeriodicWorkPolicy.KEEP,
                ratingRequest
            )
    }

    private fun stopScheduleWorker() {
        WorkManager.getInstance(getApplication()).cancelUniqueWork("ScheduleUpdate")
        WorkManager.getInstance(getApplication()).cancelUniqueWork("RatingUpdate")
    }

    // Debug Tools
    fun testLiveUpdateUpcoming() {
        val dummyLesson = Lesson(
            id = "test_1",
            title = "Теория организации",
            type = "Лекция",
            startTime = LocalTime.now().plusMinutes(15),
            endTime = LocalTime.now().plusMinutes(105),
            teacher = "Пахомов И.Ю.",
            room = "423",
            date = LocalDate.now(),
            number = 1
        )
        LiveUpdateManager.showFirstLessonUpcomingNotification(getApplication(), dummyLesson)
    }

    fun testLiveUpdateActive() {
        val dummyLesson = Lesson(
            id = "test_2",
            title = "Теория организации",
            type = "Лекция",
            startTime = LocalTime.now().minusMinutes(75),
            endTime = LocalTime.now().plusMinutes(15),
            teacher = "Пахомов И.Ю.",
            room = "423",
            date = LocalDate.now(),
            number = 1
        )
        LiveUpdateManager.showLessonEndingNotification(getApplication(), dummyLesson)
    }

    fun stopLiveUpdate() {
        NotificationHelper.cancelNotification(getApplication(), 999)
    }

    fun testScheduleChange() {
        NotificationHelper.showNotification(
            getApplication(),
            NotificationHelper.CHANNEL_SCHEDULE_CHANGES,
            "⚠️ Внимание! Изменение",
            "Лекция по праву перенесена из Ауд. 420 в Ауд. 423",
            1001
        )
    }

    fun testClassReminder() {
        NotificationHelper.showNotification(
            getApplication(),
            NotificationHelper.CHANNEL_CLASS_REMINDERS,
            "Напоминание",
            "Через 15 мин начнется: Высшая математика, Ауд. 101",
            1002
        )
    }
}

class ProfileViewModelFactory(
    private val repository: ScheduleRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
