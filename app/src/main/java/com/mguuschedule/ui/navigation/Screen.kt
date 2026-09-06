package com.mguuschedule.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Schedule : Screen("schedule", "Расписание", Icons.Default.DateRange)
    object Rating : Screen("rating", "Рейтинг", Icons.Default.Assessment)
    object Settings : Screen("settings", "Настройки", Icons.Default.Settings)
    val Profile: Screen get() = Settings
    object Search : Screen("search", "Поиск", Icons.Default.DateRange)
    object Debug : Screen("debug", "Отладка", Icons.Default.Settings)
    object LessonDetail : Screen("lesson/{lessonId}", "Информация", Icons.Default.DateRange)
}
