package com.mguuschedule.ui.screens

import android.app.Application
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mguuschedule.model.Lesson
import com.mguuschedule.model.MoodleCourse
import com.mguuschedule.model.MoodleCourseSection
import com.mguuschedule.model.MoodleDeadline
import com.mguuschedule.model.MoodleGradeItem
import com.mguuschedule.model.MoodleSession
import com.mguuschedule.repository.InvalidMoodleTokenException
import com.mguuschedule.repository.MoodleRepository
import com.mguuschedule.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class MoodleTab(val label: String) {
    COURSES("Курсы"),
    DEADLINES("Задания"),
    PORTAL("Портал")
}

class MoodleViewModel(
    private val repository: MoodleRepository,
    application: Application
) : AndroidViewModel(application) {

    var session by mutableStateOf<MoodleSession?>(null)
        private set

    var activeTab by mutableStateOf(MoodleTab.COURSES)
        private set

    var courses by mutableStateOf<List<MoodleCourse>>(emptyList())
        private set

    var deadlines by mutableStateOf<List<MoodleDeadline>>(emptyList())
        private set

    var isLoggingIn by mutableStateOf(false)
        private set

    var isLoadingDashboard by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Детали выбранного курса
    var selectedCourse by mutableStateOf<MoodleCourse?>(null)
        private set

    var courseSections by mutableStateOf<List<MoodleCourseSection>>(emptyList())
        private set

    var courseGrades by mutableStateOf<List<MoodleGradeItem>>(emptyList())
        private set

    var isCourseDetailLoading by mutableStateOf(false)
        private set

    var courseDetailError by mutableStateOf<String?>(null)
        private set

    // Полноэкранный веб-портал (без API-входа или переход из курса/задания)
    var standaloneWebUrl by mutableStateOf<String?>(null)
        private set

    var portalUrl by mutableStateOf(MoodleRepository.MOODLE_MY_URL)
        private set

    // SSO авторизация через Портал МГУУ (ks-auth.mguu.ru)
    var ssoLaunchUrl by mutableStateOf<String?>(null)
        private set

    var statusBanner by mutableStateOf<String?>(null)
        private set

    init {
        restoreSavedState()
    }

    private fun restoreSavedState() {
        viewModelScope.launch {
            val (savedSession, cachedCourses, cachedDeadlines) = withContext(Dispatchers.IO) {
                Triple(
                    repository.loadSavedSession(),
                    repository.loadCachedCourses(),
                    repository.loadCachedDeadlines()
                )
            }
            session = savedSession
            courses = cachedCourses
            deadlines = cachedDeadlines

            if (savedSession != null) {
                refreshDashboard(showPullRefresh = false)
            }
        }
    }

    fun selectTab(tab: MoodleTab) {
        activeTab = tab
        if (tab == MoodleTab.PORTAL) {
            preparePortalAutoLogin(MoodleRepository.MOODLE_MY_URL)
        }
    }

    fun clearStatusBanner() {
        statusBanner = null
    }

    fun showStatus(message: String) {
        statusBanner = message
    }

    fun clearError() {
        errorMessage = null
    }

    fun loginWithCredentials(username: String, password: String) {
        if (isLoggingIn) return
        viewModelScope.launch {
            isLoggingIn = true
            errorMessage = null
            try {
                val newSession = repository.loginWithCredentials(username, password)
                session = newSession
                activeTab = MoodleTab.COURSES
                refreshDashboard(showPullRefresh = false)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Не удалось войти в СДО"
            } finally {
                isLoggingIn = false
            }
        }
    }

    fun startSsoLogin() {
        errorMessage = null
        val passport = UUID.randomUUID().toString().replace("-", "").take(16)
        ssoLaunchUrl = repository.buildSsoLaunchUrl(passport)
    }

    fun cancelSsoLogin() {
        ssoLaunchUrl = null
    }

    fun onSsoCallbackUrl(callbackUrl: String) {
        if (isLoggingIn) return
        ssoLaunchUrl = null
        viewModelScope.launch {
            isLoggingIn = true
            errorMessage = null
            try {
                val newSession = repository.completeSsoLogin(callbackUrl)
                session = newSession
                activeTab = MoodleTab.COURSES
                refreshDashboard(showPullRefresh = false)
            } catch (e: Exception) {
                AppLogger.e("MOODLE_SSO", "Ошибка SSO входа: ${e.message}", e)
                errorMessage = e.message ?: "Не удалось завершить вход через Портал МГУУ"
            } finally {
                isLoggingIn = false
            }
        }
    }

    fun openStandaloneWebPortal(url: String = MoodleRepository.MOODLE_MY_URL) {
        standaloneWebUrl = url
    }

    fun closeStandaloneWebPortal() {
        standaloneWebUrl = null
    }

    fun openInPortal(targetUrl: String) {
        val cleanUrl = targetUrl.ifBlank { MoodleRepository.MOODLE_MY_URL }
        if (session == null) {
            standaloneWebUrl = cleanUrl
            return
        }
        selectedCourse = null
        activeTab = MoodleTab.PORTAL
        preparePortalAutoLogin(cleanUrl)
    }

    private fun preparePortalAutoLogin(targetUrl: String) {
        val currentSession = session ?: run {
            portalUrl = targetUrl
            return
        }
        viewModelScope.launch {
            val resolved = repository.getAutoLoginUrl(currentSession, targetUrl)
            portalUrl = resolved
        }
    }

    fun refreshDashboard(showPullRefresh: Boolean = true) {
        val currentSession = session ?: return
        viewModelScope.launch {
            if (showPullRefresh) {
                isRefreshing = true
            } else if (courses.isEmpty()) {
                isLoadingDashboard = true
            }
            errorMessage = null

            try {
                val fetchedCourses = repository.fetchCourses(currentSession)
                courses = fetchedCourses
                val fetchedDeadlines = repository.fetchUpcomingDeadlines(currentSession, fetchedCourses)
                deadlines = fetchedDeadlines
            } catch (e: InvalidMoodleTokenException) {
                AppLogger.w("MOODLE_API", "Токен СДО недействителен, сброс сессии")
                logout(clearWebCookies = false)
                errorMessage = e.message
            } catch (e: Exception) {
                AppLogger.e("MOODLE_API", "Ошибка обновления СДО: ${e.message}", e)
                if (courses.isEmpty()) {
                    errorMessage = e.message ?: "Не удалось загрузить данные СДО"
                } else {
                    statusBanner = "Нет сети — показаны сохранённые курсы"
                }
            } finally {
                isLoadingDashboard = false
                isRefreshing = false
            }
        }
    }

    fun openCourse(course: MoodleCourse) {
        val currentSession = session ?: return
        selectedCourse = course
        courseSections = emptyList()
        courseGrades = emptyList()
        courseDetailError = null
        isCourseDetailLoading = true

        viewModelScope.launch {
            try {
                val (sections, grades) = coroutineScope {
                    val sectionsDeferred = async { repository.fetchCourseContents(currentSession, course.id) }
                    val gradesDeferred = async { repository.fetchCourseGrades(currentSession, course.id) }
                    sectionsDeferred.await() to gradesDeferred.await()
                }
                courseSections = sections
                courseGrades = grades
            } catch (e: InvalidMoodleTokenException) {
                selectedCourse = null
                logout(clearWebCookies = false)
                errorMessage = e.message
            } catch (e: Exception) {
                AppLogger.e("MOODLE_API", "Ошибка загрузки курса ${course.id}: ${e.message}", e)
                courseDetailError = e.message ?: "Не удалось загрузить материалы курса"
            } finally {
                isCourseDetailLoading = false
            }
        }
    }

    fun closeCourse() {
        selectedCourse = null
        courseDetailError = null
    }

    fun attachResourceToLesson(
        lesson: Lesson,
        title: String,
        url: String,
        fileName: String? = null,
        mimeType: String? = null,
        sizeBytes: Long? = null
    ) {
        viewModelScope.launch {
            try {
                val lessonKey = "${lesson.date}_${lesson.number}_${lesson.startTime}"
                repository.attachToLesson(
                    lessonKey = lessonKey,
                    title = title,
                    url = url,
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes
                )
                statusBanner = "Прикреплено к паре «${lesson.title}»"
            } catch (e: Exception) {
                AppLogger.e("MOODLE_API", "Ошибка прикрепления файла к паре: ${e.message}", e)
                statusBanner = "Не удалось прикрепить материал"
            }
        }
    }

    fun logout(clearWebCookies: Boolean = true) {
        repository.clearSession()
        if (clearWebCookies) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
        session = null
        courses = emptyList()
        deadlines = emptyList()
        selectedCourse = null
        courseSections = emptyList()
        courseGrades = emptyList()
        standaloneWebUrl = null
        ssoLaunchUrl = null
        activeTab = MoodleTab.COURSES
        portalUrl = MoodleRepository.MOODLE_MY_URL
    }
}

class MoodleViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MoodleViewModel(MoodleRepository(application), application) as T
    }
}
