package com.mguuschedule.model

import androidx.compose.runtime.Immutable

enum class EducationLevel(val code: String, val title: String, val schedulerPage: String) {
    BACHELOR("bak", "Бакалавриат", "scheduler1.php"),
    MASTER("mag", "Магистратура", "scheduler2.php")
}

@Immutable
data class Group(
    val id: String,
    val name: String,
    val course: String = "",
    val level: EducationLevel = EducationLevel.BACHELOR
)
