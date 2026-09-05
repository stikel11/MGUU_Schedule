package com.mguuschedule.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Schedule : Screen("schedule", "Расписание", Icons.Default.DateRange)
    object Profile : Screen("profile", "Профиль", Icons.Default.AccountCircle)
    object Search : Screen("search", "Поиск", Icons.Default.DateRange)
    object Debug : Screen("debug", "Отладка", Icons.Default.AccountCircle)
    object LessonDetail : Screen("lesson/{lessonId}", "Информация", Icons.Default.DateRange)
}
