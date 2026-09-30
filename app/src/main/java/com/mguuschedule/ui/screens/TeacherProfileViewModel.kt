package com.mguuschedule.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mguuschedule.model.CampusTeacher
import com.mguuschedule.repository.TeacherRepository
import kotlinx.coroutines.launch

sealed class TeacherUiState {
    object Loading : TeacherUiState()
    data class Success(val teacher: CampusTeacher) : TeacherUiState()
    data class Error(val message: String) : TeacherUiState()
    object NotFound : TeacherUiState()
}

class TeacherProfileViewModel(
    private val teacherName: String,
    private val repository: TeacherRepository
) : ViewModel() {

    var uiState: TeacherUiState by mutableStateOf(TeacherUiState.Loading)
        private set

    init {
        loadProfile()
    }

    fun loadProfile() {
        if (teacherName.isBlank() || teacherName == "Не указан") {
            uiState = TeacherUiState.NotFound
            return
        }

        viewModelScope.launch {
            // Сначала пробуем получить данные из кэша
            val cachedResult = repository.getCachedTeacherProfile(teacherName)
            if (cachedResult != null) {
                uiState = TeacherUiState.Success(cachedResult)
                
                // Проверяем, устарел ли кэш (например, старше 3 дней)
                if (repository.isCacheStale(teacherName)) {
                    // Stale-while-revalidate: обновляем данные в фоне
                    launch {
                        val freshResult = repository.getTeacherProfile(teacherName)
                        freshResult.onSuccess { freshTeacher ->
                            uiState = TeacherUiState.Success(freshTeacher)
                        }
                    }
                }
            } else {
                // Если кэша нет, показываем загрузку и делаем сетевой запрос
                uiState = TeacherUiState.Loading
                val result = repository.getTeacherProfile(teacherName)
                result.onSuccess { teacher ->
                    uiState = TeacherUiState.Success(teacher)
                }.onFailure { error ->
                    if (error.message?.contains("не найден") == true || error.message?.contains("неоднознач") == true) {
                        uiState = TeacherUiState.NotFound
                    } else {
                        uiState = TeacherUiState.Error("Не удалось загрузить профиль: ${error.localizedMessage}")
                    }
                }
            }
        }
    }

}

class TeacherProfileViewModelFactory(
    private val teacherName: String,
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TeacherProfileViewModel(teacherName, TeacherRepository(application)) as T
    }
}
