package com.mguuschedule.model

/**
 * Период обучения (учебный год или семестр).
 */
data class AcademicPeriod(
    val id: String,
    val title: String,
    val isSelected: Boolean
)

/**
 * Группа в каталоге БРС.
 */
data class EduGroup(
    val groupName: String,
    val url: String
)

/**
 * Страница рейтинга группы (список студентов + доступные селекторы периода).
 */
data class GroupRatingPage(
    val students: List<StudentRating>,
    val availableYears: List<AcademicPeriod>,
    val availableSemesters: List<AcademicPeriod>
)

/**
 * Студент в рейтинге группы.
 */
data class StudentRating(
    val zachetka: String,
    val module1: Float?,
    val module2: Float?,
    val totalScore: Float?,
    val detailUrl: String
)

/**
 * Дисциплина в персональном рейтинге студента.
 */
data class SubjectScore(
    val title: String,
    val controlType: String,
    val module1: Float?,
    val module2: Float?,
    val totalScore: Float?,
    val detailUrl: String
)

/**
 * Контрольная точка дисциплины.
 */
data class ControlPoint(
    val pointName: String,
    val date: String,
    val score: Int?
)
