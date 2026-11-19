package com.mguuschedule.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Schedule : Screen("schedule", "Расписание", Icons.Rounded.CalendarMonth)
    object Rating : Screen("rating", "Рейтинг", Icons.Rounded.Assessment)
    object Sections : Screen("sections", "Разделы", Icons.Rounded.GridView)
    object Settings : Screen("settings", "Настройки", Icons.Rounded.Settings)
    val Profile: Screen get() = Settings
    object Search : Screen("search", "Поиск", Icons.Rounded.Search)
    object Debug : Screen("debug", "Отладка", Icons.Rounded.BugReport)
    object LessonDetail : Screen("lesson/{lessonId}", "Информация", Icons.Rounded.Info)
    object TeacherProfile : Screen("teacher/{teacherName}", "Профиль преподавателя", Icons.Rounded.Person)
    object Todo : Screen("todo", "Задачи", Icons.Rounded.CheckCircle)
}
